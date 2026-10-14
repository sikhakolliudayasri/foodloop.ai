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
    app.dependency_overrides[get_db] = override_db
    with TestClient(app) as test_client:
        yield test_client
    app.dependency_overrides.clear()
    Base.metadata.drop_all(test_engine)
    test_engine.dispose()


def test_direct_claim_and_donor_approval_flow(client: TestClient):
    # 1. Test Demo Login for Restaurant and Recipient
    restaurant_auth = client.post("/api/auth/demo-login/restaurant").json()
    assert restaurant_auth["user"]["name"] == "Paradise Biryani & Dhaba"
    donor_headers = {"Authorization": f"Bearer {restaurant_auth['access_token']}"}

    recipient_auth = client.post("/api/auth/demo-login/recipient").json()
    assert recipient_auth["user"]["role"] == "recipient"
    recipient_headers = {"Authorization": f"Bearer {recipient_auth['access_token']}"}

    # 2. Donor lists surplus restaurant food
    now = datetime.now(timezone.utc)
    donation_payload = {
        "title": "Fresh Hyderabadi Dum Biryani Surplus",
        "category": "prepared_meals",
        "donor_type": "restaurant_stall",
        "description": "Unused afternoon banquet buffet biryani. Kept in hot-holding at safe temperature.",
        "dietary": "non_veg",
        "quantity": 30,
        "unit": "portions",
        "usable_until": (now + timedelta(hours=5)).isoformat(),
        "storage": "hot_holding",
        "allergens": ["dairy"],
        "pickup_address": "Indiranagar 100ft Road, Bengaluru",
        "latitude": 12.9716,
        "longitude": 77.5946,
        "pickup_start": (now + timedelta(minutes=10)).isoformat(),
        "pickup_end": (now + timedelta(hours=3)).isoformat(),
        "packaging": "Insulated stainless steel containers",
        "safety_verified": True,
    }
    create_res = client.post("/api/donations", headers=donor_headers, json=donation_payload)
    assert create_res.status_code == 201, create_res.text
    donation_id = create_res.json()["id"]

    # 3. Explore endpoint allows discovering food
    explore_res = client.get("/api/donations/explore")
    assert explore_res.status_code == 200
    assert any(d["id"] == donation_id for d in explore_res.json())

    # 4. Recipient claims the food with proper reason
    claim_payload = {
        "quantity": 20,
        "claim_reason": "Feeding 45 children dinner tonight at Hope Haven shelter before evening studies.",
        "beneficiary_type": "Children's Shelter / Orphanage",
        "organization_name": "Hope Haven Children's Shelter",
        "contact_phone": "+91 98765 43210",
    }
    claim_res = client.post(f"/api/donations/{donation_id}/claim", headers=recipient_headers, json=claim_payload)
    assert claim_res.status_code == 201, claim_res.text
    allocation_id = claim_res.json()["id"]
    assert claim_res.json()["status"] == "pending_donor"
    assert "Hope Haven" in claim_res.json()["claim_reason"]

    # 5. Donor views incoming claims and approves it
    donor_allocations = client.get("/api/allocations", headers=donor_headers).json()
    assert any(a["id"] == allocation_id for a in donor_allocations)

    decision_res = client.patch(
        f"/api/allocations/{allocation_id}/donor-decision",
        headers=donor_headers,
        json={"accept": True, "donor_notes": "Containers ready at kitchen back door. Ask for Chef Imran."},
    )
    assert decision_res.status_code == 200
    assert decision_res.json()["status"] == "approved"
    assert decision_res.json()["pickup_code"].startswith("PASS-")

    # 6. Check that donation remaining quantity is properly deducted
    updated_donation = client.get("/api/donations", headers=donor_headers).json()[0]
    assert updated_donation["remaining_quantity"] == 10  # 30 - 20 = 10

    # 7. Complete handoff lifecycle: pickup collected and delivered
    collect_res = client.patch(f"/api/allocations/{allocation_id}/event?event=collected", headers=donor_headers)
    assert collect_res.status_code == 200
    assert collect_res.json()["status"] == "collected"

    deliver_res = client.patch(f"/api/allocations/{allocation_id}/event?event=delivered", headers=recipient_headers)
    assert deliver_res.status_code == 200
    assert deliver_res.json()["status"] == "delivered"
