from __future__ import annotations

from datetime import datetime, timezone
from math import ceil

try:
    from ortools.constraint_solver import pywrapcp, routing_enums_pb2
    HAS_ORTOOLS = True
except ImportError:
    pywrapcp = None
    routing_enums_pb2 = None
    HAS_ORTOOLS = False

from .matching import distance_km


def optimize_vehicle_routes(
    jobs: list[dict],
    vehicles: list[dict],
    *,
    now: datetime | None = None,
    time_limit_seconds: int = 2,
) -> dict:
    current_time = now or datetime.now(timezone.utc)
    if not HAS_ORTOOLS:
        return _result(
            [],
            [{"allocation_id": j["allocation_id"], "reason": "OR-Tools package not installed in environment"} for j in jobs],
            False,
            "OR-Tools is not installed in the local Python environment. Routes could not be computed.",
        )

    unassigned = []
    feasible_jobs = []
    for job in jobs:
        if job["unit"] != "kg":
            unassigned.append({"allocation_id": job["allocation_id"], "reason": "Vehicle capacity planning currently requires kg units."})
        elif job["quantity"] <= 0:
            unassigned.append({"allocation_id": job["allocation_id"], "reason": "Allocation quantity must be positive."})
        elif job["quantity"] > max((vehicle["capacity"] for vehicle in vehicles if vehicle["unit"] == "kg" and _vehicle_supports(vehicle, job)), default=0):
            unassigned.append({"allocation_id": job["allocation_id"], "reason": "No available vehicle has sufficient carrying capacity."})
        else:
            feasible_jobs.append(job)

    if not vehicles:
        unassigned.extend(
            {"allocation_id": job["allocation_id"], "reason": "No available vehicles are configured."}
            for job in feasible_jobs
        )
        return _result([], unassigned, False, "No vehicle is available; no route was optimized.")

    vehicles = [vehicle for vehicle in vehicles if vehicle["unit"] == "kg" and vehicle["capacity"] > 0]
    if not feasible_jobs or not vehicles:
        if feasible_jobs:
            unassigned.extend({"allocation_id": job["allocation_id"], "reason": "No kg-capacity vehicle is configured."} for job in feasible_jobs)
        return _result([], unassigned, False, "No feasible job and vehicle combination was found.")

    coordinates = []
    for vehicle in vehicles:
        coordinates.append((vehicle["latitude"], vehicle["longitude"]))
    for job in feasible_jobs:
        coordinates.extend((job["pickup"], job["delivery"]))
    vehicle_count = len(vehicles)
    start_nodes = list(range(vehicle_count))
    manager = pywrapcp.RoutingIndexManager(len(coordinates), vehicle_count, start_nodes, start_nodes)
    routing = pywrapcp.RoutingModel(manager)

    def travel_minutes(from_index: int, to_index: int) -> int:
        start = coordinates[manager.IndexToNode(from_index)]
        end = coordinates[manager.IndexToNode(to_index)]
        if start == end:
            return 0
        return max(1, ceil(distance_km(*start, *end) * 3))

    transit_callback = routing.RegisterTransitCallback(travel_minutes)
    routing.SetArcCostEvaluatorOfAllVehicles(transit_callback)
    routing.AddDimension(transit_callback, 30, 24 * 60, False, "Time")
    time_dimension = routing.GetDimensionOrDie("Time")
    time_dimension.SetGlobalSpanCostCoefficient(8)

    demands = [0] * len(coordinates)
    pickup_delivery_indices = []
    for job_index, job in enumerate(feasible_jobs):
        pickup_node = vehicle_count + job_index * 2
        delivery_node = pickup_node + 1
        demands[pickup_node] = ceil(job["quantity"])
        demands[delivery_node] = -ceil(job["quantity"])
        pickup = manager.NodeToIndex(pickup_node)
        delivery = manager.NodeToIndex(delivery_node)
        pickup_delivery_indices.append((pickup, delivery, job, pickup_node, delivery_node))
        routing.AddPickupAndDelivery(pickup, delivery)
        routing.solver().Add(routing.VehicleVar(pickup) == routing.VehicleVar(delivery))
        routing.solver().Add(time_dimension.CumulVar(pickup) <= time_dimension.CumulVar(delivery))
        allowed_vehicles = [
            vehicle_index
            for vehicle_index, vehicle in enumerate(vehicles)
            if _vehicle_supports(vehicle, job) and vehicle["capacity"] >= job["quantity"]
        ]
        if allowed_vehicles:
            routing.VehicleVar(pickup).SetValues(allowed_vehicles)
            routing.VehicleVar(delivery).SetValues(allowed_vehicles)

        pickup_start = max(0, int((job["pickup_start"] - current_time).total_seconds() / 60))
        pickup_end = max(0, int((job["pickup_end"] - current_time).total_seconds() / 60))
        delivery_end = max(0, int((job["deadline"] - current_time).total_seconds() / 60))
        if pickup_start > pickup_end or delivery_end == 0:
            unassigned.append({"allocation_id": job["allocation_id"], "reason": "Pickup or delivery time window has passed."})
            routing.AddDisjunction([pickup], 1_000_000)
            routing.AddDisjunction([delivery], 1_000_000)
        else:
            time_dimension.CumulVar(pickup).SetRange(pickup_start, min(pickup_end, 24 * 60))
            time_dimension.CumulVar(delivery).SetRange(0, min(delivery_end, 24 * 60))

    def demand_callback(from_index: int) -> int:
        return demands[manager.IndexToNode(from_index)]

    demand_callback_index = routing.RegisterUnaryTransitCallback(demand_callback)
    routing.AddDimensionWithVehicleCapacity(
        demand_callback_index,
        0,
        [ceil(vehicle["capacity"]) for vehicle in vehicles],
        True,
        "Capacity",
    )
    for pickup, delivery, _, _, _ in pickup_delivery_indices:
        routing.AddDisjunction([pickup], 1_000_000)
        routing.AddDisjunction([delivery], 1_000_000)

    search = pywrapcp.DefaultRoutingSearchParameters()
    search.first_solution_strategy = routing_enums_pb2.FirstSolutionStrategy.PARALLEL_CHEAPEST_INSERTION
    search.local_search_metaheuristic = routing_enums_pb2.LocalSearchMetaheuristic.GUIDED_LOCAL_SEARCH
    search.time_limit.FromSeconds(time_limit_seconds)
    solution = routing.SolveWithParameters(search)
    if solution is None:
        unassigned.extend(
            {"allocation_id": job["allocation_id"], "reason": "No feasible pickup, delivery, and capacity plan was found."}
            for job in feasible_jobs
            if not any(item["allocation_id"] == job["allocation_id"] for item in unassigned)
        )
        return _result([], unassigned, False, "OR-Tools found no feasible route plan.")

    routes = []
    assigned_ids = set()
    total_distance = 0.0
    total_minutes = 0
    for vehicle_index, vehicle in enumerate(vehicles):
        index = routing.Start(vehicle_index)
        sequence = []
        route_distance = 0.0
        route_minutes = 0
        while not routing.IsEnd(index):
            node = manager.IndexToNode(index)
            next_index = solution.Value(routing.NextVar(index))
            next_node = manager.IndexToNode(next_index)
            if node != next_node:
                route_distance += distance_km(*coordinates[node], *coordinates[next_node])
                route_minutes += travel_minutes(index, next_index)
            if next_node >= vehicle_count:
                job_index = (next_node - vehicle_count) // 2
                job = feasible_jobs[job_index]
                point_kind = "pickup" if (next_node - vehicle_count) % 2 == 0 else "delivery"
                sequence.append({
                    "stop": point_kind,
                    "allocation_id": job["allocation_id"],
                    "name": job["donation_title"] if point_kind == "pickup" else job["organization"],
                    "address": job["pickup_address"] if point_kind == "pickup" else job["delivery_address"],
                    "estimated_minutes_from_previous": travel_minutes(index, next_index),
                })
                assigned_ids.add(job["allocation_id"])
            index = next_index
        if sequence:
            routes.append({
                "vehicle_id": vehicle["id"],
                "vehicle_name": vehicle["name"],
                "sequence": sequence,
                "estimated_straight_line_km": round(route_distance, 2),
                "estimated_travel_minutes": route_minutes,
            })
            total_distance += route_distance
            total_minutes += route_minutes

    for job in feasible_jobs:
        if job["allocation_id"] not in assigned_ids and not any(item["allocation_id"] == job["allocation_id"] for item in unassigned):
            unassigned.append({"allocation_id": job["allocation_id"], "reason": "Excluded from the feasible solver plan."})
    return _result(
        routes,
        unassigned,
        bool(routes),
        "OR-Tools returned a feasible vehicle plan." if routes else "OR-Tools found no assigned routes.",
        distance=round(total_distance, 2),
        minutes=total_minutes,
        estimated=True,
    )


def _result(routes, unassigned, optimized, summary, *, distance=0, minutes=0, estimated=False):
    return {
        "optimized": optimized,
        "solver": "Google OR-Tools",
        "summary": summary,
        "routes": routes,
        "unassigned": unassigned,
        "unfulfilled": [],
        "estimated_straight_line_km": distance,
        "estimated_travel_minutes": minutes,
        "distance_notice": "Straight-line distance and estimated travel time; road-network routing is not configured." if estimated else None,
        "human_approval_required": True,
    }


def _vehicle_supports(vehicle: dict, job: dict) -> bool:
    capabilities = {value.casefold() for value in vehicle["storage"]}
    return job["storage"].casefold() == "ambient" or job["storage"].casefold() in capabilities