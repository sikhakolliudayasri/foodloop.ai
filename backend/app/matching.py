from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from math import atan2, cos, radians, sin, sqrt


@dataclass(frozen=True)
class DonationCandidate:
    id: int
    title: str
    category: str
    quantity: float
    unit: str
    usable_until: datetime
    pickup_start: datetime
    pickup_end: datetime
    storage: str
    allergens: frozenset[str]
    latitude: float
    longitude: float


@dataclass(frozen=True)
class RequestCandidate:
    id: int
    organization: str
    categories: frozenset[str]
    unmet_quantity: float
    unit: str
    urgency: int
    storage: frozenset[str]
    excluded_allergens: frozenset[str]
    needed_by: datetime
    latitude: float
    longitude: float
    prior_allocations: float = 0


def distance_km(lat_a: float, lon_a: float, lat_b: float, lon_b: float) -> float:
    earth_radius_km = 6371.0
    delta_lat = radians(lat_b - lat_a)
    delta_lon = radians(lon_b - lon_a)
    arc = (
        sin(delta_lat / 2) ** 2
        + cos(radians(lat_a)) * cos(radians(lat_b)) * sin(delta_lon / 2) ** 2
    )
    return 2 * earth_radius_km * atan2(sqrt(arc), sqrt(1 - arc))


def explain_rejection(donation: DonationCandidate, request: RequestCandidate, now: datetime) -> str | None:
    if donation.usable_until <= now:
        return "Donation is past its verified usable-until time."
    if request.needed_by <= now:
        return "Recipient's requested delivery time has passed."
    if donation.category not in request.categories:
        return "Food category is not requested."
    if donation.unit != request.unit:
        return "Donation and request use different quantity units."
    if donation.quantity <= 0 or request.unmet_quantity <= 0:
        return "No allocatable quantity or unmet demand remains."
    if donation.allergens & request.excluded_allergens:
        return "Donation contains an allergen excluded by the recipient."
    if donation.storage.casefold() not in {value.casefold() for value in request.storage}:
        return "Recipient does not have the required storage capability."
    distance = distance_km(donation.latitude, donation.longitude, request.latitude, request.longitude)
    if distance > 80:
        return "Recipient is outside the 80 km local delivery radius."
    pickup_time = max(now, donation.pickup_start) + timedelta(minutes=30)
    if pickup_time > donation.pickup_end:
        return "The available pickup window cannot fit a practical handoff."
    delivery_time = pickup_time + timedelta(minutes=distance * 3)
    if delivery_time > min(donation.usable_until, request.needed_by):
        return "Estimated travel time exceeds the food or recipient deadline."
    return None


def recommend_matches(
    donations: list[DonationCandidate],
    requests: list[RequestCandidate],
    *,
    now: datetime | None = None,
) -> tuple[list[dict], list[dict]]:
    current_time = now or datetime.now(timezone.utc)
    feasible: list[dict] = []
    rejected: list[dict] = []

    for donation in donations:
        donation_rejections = []
        for request in requests:
            reason = explain_rejection(donation, request, current_time)
            if reason:
                donation_rejections.append({"request_id": request.id, "reason": reason})
                continue

            distance = distance_km(
                donation.latitude,
                donation.longitude,
                request.latitude,
                request.longitude,
            )
            hours_left = (donation.usable_until - current_time).total_seconds() / 3600
            quantity_fit = min(donation.quantity, request.unmet_quantity) / max(
                donation.quantity, request.unmet_quantity
            )
            fairness = 1 / (1 + max(request.prior_allocations, 0))
            score = min(100, round(
                request.urgency * 5
                + max(0, 20 - hours_left) * 1.5
                + quantity_fit * 20
                + fairness * 15
                + max(0, 20 - distance) * 0.75,
                2,
            ))
            quantity = min(donation.quantity, request.unmet_quantity)
            feasible.append(
                {
                    "donation_id": donation.id,
                    "request_id": request.id,
                    "organization": request.organization,
                    "quantity": quantity,
                    "unit": donation.unit,
                    "distance_km": round(distance, 2),
                    "score": score,
                    "explanation": (
                        f"Urgency {request.urgency}/10, {hours_left:.1f} usable hours, "
                        f"{distance:.1f} km straight-line distance, and {quantity:g} {donation.unit} fits unmet demand."
                    ),
                }
            )
        if donation_rejections:
            rejected.append({"donation_id": donation.id, "reasons": donation_rejections})

    feasible.sort(key=lambda item: (-item["score"], item["distance_km"], item["request_id"]))
    return feasible, rejected