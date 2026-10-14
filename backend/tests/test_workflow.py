from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from backend.app.database import Base, get_db
from backend.app.main import app


@pytest.fixture
def client(monkeypatch):
    test_engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    TestSession = sessionmaker(bind=test_engine, autoflush=False, expire_on_commit=False)
    Base.metadata.create_all(test_engine)

    def override_db():
        session = TestSession()
        try:
            yield session
        finally:
            session.close()

    monkeypatch.setenv("FOODLOOP_SECRET", "integration-test-secret")
    monkeypatch.setenv("COORDINATOR_INVITE_CODE", "invite-for-test")
    app.dependency_overrides[get_db] = override_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()
    Base.metadata.drop_all(test_engine)
    test_engine.dispose()


def register(client: TestClient, name: str, role: str) -> dict:
    payload = {
        "name": name,
        "email": f"{role}@example.org",
        "password": "local-test-password",
        "role": role,
    }
    if role == "coordinator":
        payload["coordinator_invite"] = "invite-for-test"
    response = client.post("/api/auth/register", json=payload)
    assert response.status_code == 201, response.text
    return response.json()


def auth(result: dict) -> dict[str, str]:
    return {"Authorization": f"Bearer {result['access_token']}"}


def test_registration_normalizes_email_and_allows_immediate_login(client: TestClient):
    registered = client.post(
        "/api/auth/register",
        json={
            "name": "Local donor",
            "email": "  Donor@Example.org  ",
            "password": "local-test-password",
            "role": "donor",
        },
    )
    assert registered.status_code == 201
    assert registered.json()["user"]["email"] == "donor@example.org"

    login = client.post(
        "/api/auth/login",
        json={"email": " DONOR@example.org ", "password": "local-test-password"},
    )
    assert login.status_code == 200
    assert login.json()["user"]["id"] == registered.json()["user"]["id"]

    duplicate = client.post(
        "/api/auth/register",
        json={
            "name": "Duplicate donor",
            "email": "DONOR@example.org",
            "password": "local-test-password",
            "role": "donor",
        },
    )
    assert duplicate.status_code == 409


def test_vite_fallback_origin_passes_cors_preflight(client: TestClient):
    response = client.options(
        "/api/auth/login",
        headers={
            "Origin": "http://localhost:5174",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "content-type",
        },
    )
    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == "http://localhost:5174"


def create_donation(client: TestClient, headers: dict[str, str], quantity: float = 12) -> dict:
    now = datetime.now(timezone.utc)
    response = client.post(
        "/api/donations",
        headers=headers,
        json={
            "title": "Prepared vegetable meals",
            "category": "prepared_meals",
            "quantity": quantity,
            "unit": "kg",
            "usable_until": (now + timedelta(hours=8)).isoformat(),
            "storage": "refrigerated",
            "allergens": ["sesame"],
            "pickup_address": "14 Market Street, San Francisco",
            "latitude": 37.7749,
            "longitude": -122.4194,
            "pickup_start": (now + timedelta(minutes=10)).isoformat(),
            "pickup_end": (now + timedelta(hours=2)).isoformat(),
            "packaging": "Sealed trays",
            "safety_verified": True,
        },
    )
    assert response.status_code == 201, response.text
    return response.json()


def create_request(client: TestClient, headers: dict[str, str], quantity: float = 7) -> dict:
    response = client.post(
        "/api/requests",
        headers=headers,
        json={
            "organization": "Harbor Community Kitchen",
            "categories": ["prepared_meals"],
            "requested_quantity": quantity,
            "unit": "kg",
            "urgency": 9,
            "storage": ["refrigerated"],
            "excluded_allergens": ["peanuts"],
            "latitude": 37.775,
            "longitude": -122.419,
            "delivery_address": "21 Howard Street, San Francisco",
            "needed_by": (datetime.now(timezone.utc) + timedelta(hours=4)).isoformat(),
        },
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_vertical_slice_persists_partial_match_and_confirmed_delivery(client: TestClient):
    donor = register(client, "Local donor", "donor")
    recipient = register(client, "Kitchen partner", "recipient")
    coordinator = register(client, "Coordinator", "coordinator")
    donor_headers = auth(donor)
    recipient_headers = auth(recipient)
    coordinator_headers = auth(coordinator)

    donation = create_donation(client, donor_headers)
    request = create_request(client, recipient_headers)
    assert client.get("/api/matches", headers=recipient_headers).status_code == 403

    result = client.get("/api/matches", headers=coordinator_headers)
    assert result.status_code == 200
    recommendation = result.json()["matches"][0]
    assert recommendation["quantity"] == 7
    assert recommendation["request_id"] == request["id"]
    assert "straight-line" in recommendation["explanation"]

    proposal = client.post(
        "/api/allocations",
        headers=coordinator_headers,
        json={"donation_id": donation["id"], "request_id": request["id"], "quantity": 7},
    )
    assert proposal.status_code == 201, proposal.text
    allocation_id = proposal.json()["id"]
    assert client.patch(f"/api/allocations/{allocation_id}/approve", headers=coordinator_headers).status_code == 409
    accepted = client.patch(
        f"/api/allocations/{allocation_id}/decision",
        headers=recipient_headers,
        json={"accept": True},
    )
    assert accepted.status_code == 200
    assert accepted.json()["status"] == "accepted"

    approved = client.patch(f"/api/allocations/{allocation_id}/approve", headers=coordinator_headers)
    assert approved.status_code == 200
    remaining = client.get("/api/donations", headers=donor_headers).json()[0]["remaining_quantity"]
    assert remaining == 5
    assert client.get("/api/dashboard", headers=recipient_headers).json()["delivered_quantity"] == 0

    collected = client.patch(
        f"/api/allocations/{allocation_id}/event?event=collected",
        headers=coordinator_headers,
    )
    assert collected.status_code == 200
    delivered = client.patch(
        f"/api/allocations/{allocation_id}/event?event=delivered",
        headers=recipient_headers,
    )
    assert delivered.status_code == 200
    metrics = client.get("/api/dashboard", headers=recipient_headers).json()
    assert metrics["delivered_quantity"] == 7
    assert metrics["completed_deliveries"] == 1
    assert client.get("/api/matches", headers=coordinator_headers).json()["matches"] == []


def test_duplicate_accepted_proposals_cannot_overallocate(client: TestClient):
    donor = register(client, "Second donor", "donor")
    recipient = register(client, "Second kitchen", "recipient")
    coordinator = register(client, "Second coordinator", "coordinator")
    donor_headers = auth(donor)
    recipient_headers = auth(recipient)
    coordinator_headers = auth(coordinator)
    donation = create_donation(client, donor_headers, quantity=10)
    request = create_request(client, recipient_headers, quantity=8)

    proposals = []
    for _ in range(2):
        response = client.post(
            "/api/allocations",
            headers=coordinator_headers,
            json={"donation_id": donation["id"], "request_id": request["id"], "quantity": 8},
        )
        assert response.status_code == 201
        proposals.append(response.json()["id"])
        assert client.patch(
            f"/api/allocations/{response.json()['id']}/decision",
            headers=recipient_headers,
            json={"accept": True},
        ).status_code == 200

    assert client.patch(f"/api/allocations/{proposals[0]}/approve", headers=coordinator_headers).status_code == 200
    conflict = client.patch(f"/api/allocations/{proposals[1]}/approve", headers=coordinator_headers)
    assert conflict.status_code == 409
    assert client.get("/api/donations", headers=donor_headers).json()[0]["remaining_quantity"] == 2