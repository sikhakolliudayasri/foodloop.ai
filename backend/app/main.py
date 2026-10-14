import os
import random
from datetime import datetime, timedelta, timezone

from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt
from pwdlib import PasswordHash
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from .database import Base, SessionLocal, engine, get_db, initialize_database
from .matching import DonationCandidate, RequestCandidate, recommend_matches
from .models import Allocation, AuditEvent, Donation, RecipientRequest, User
from .models import OptimizationRun, PredictionRun, Vehicle
from .prediction import train_and_forecast
from .routing import optimize_vehicle_routes
from .schemas import (
    AllocationCreate,
    AllocationDecision,
    AuthResponse,
    DirectClaimCreate,
    DonationCreate,
    DonationView,
    DonorClaimDecision,
    RequestCreate,
    RequestView,
    UserCreate,
    UserLogin,
    UserView,
    VehicleCreate,
)



SECRET_KEY = os.getenv("FOODLOOP_SECRET", "local-development-only-change-before-deployment")
ALGORITHM = "HS256"
password_hash = PasswordHash.recommended()
bearer = HTTPBearer(auto_error=False)
app = FastAPI(title="FoodLoop AI API", version="1.0.0")
frontend_origins = [
    origin.strip()
    for origin in os.getenv(
        "FRONTEND_ORIGINS",
        "http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173,http://127.0.0.1:5174",
    ).split(",")
    if origin.strip()
]
app.add_middleware(
    CORSMiddleware,
    allow_origins=frontend_origins,
    allow_credentials=True,
    allow_methods=["GET", "POST", "PATCH", "OPTIONS"],
    allow_headers=["Authorization", "Content-Type"],
)


def seed_demo_data(db: Session):
    if os.getenv("FOODLOOP_SECRET") == "integration-test-secret":
        return
    if db.scalar(select(User).where(User.email == "paradise.biryani@foodloop.org")):
        return

    now = datetime.now(timezone.utc)
    # Demo Users
    restaurant = User(
        name="Paradise Biryani & Dhaba",
        email="paradise.biryani@foodloop.org",
        password_hash=password_hash.hash("DemoAccess123!"),
        role="donor",
        org_type="restaurant",
        phone="+91 98450 12345",
        city="Bengaluru",
    )
    stall = User(
        name="Annapurna Fast Food Stall",
        email="annapurna.stall@foodloop.org",
        password_hash=password_hash.hash("DemoAccess123!"),
        role="donor",
        org_type="food_stall",
        phone="+91 98450 23456",
        city="Bengaluru",
    )
    dmart = User(
        name="D-Mart Supercenter (Indiranagar)",
        email="dmart.indiranagar@foodloop.org",
        password_hash=password_hash.hash("DemoAccess123!"),
        role="donor",
        org_type="supermarket",
        phone="+91 98450 67890",
        city="Bengaluru",
    )
    recipient_ngo = User(
        name="Hope Haven Children's Shelter",
        email="hopehaven@foodloop.org",
        password_hash=password_hash.hash("DemoAccess123!"),
        role="recipient",
        org_type="shelter",
        phone="+91 98765 43210",
        city="Bengaluru",
    )
    kitchen_ngo = User(
        name="Karuna Community Kitchen",
        email="karuna.kitchen@foodloop.org",
        password_hash=password_hash.hash("DemoAccess123!"),
        role="recipient",
        org_type="community_kitchen",
        phone="+91 98765 11223",
        city="Bengaluru",
    )
    coordinator = User(
        name="City Logistics & Rescue Hub",
        email="coordinator@foodloop.org",
        password_hash=password_hash.hash("DemoAccess123!"),
        role="coordinator",
        org_type="coordinator",
        phone="+91 98000 11223",
        city="Bengaluru",
    )
    db.add_all([restaurant, stall, dmart, recipient_ngo, kitchen_ngo, coordinator])
    db.commit()
    db.refresh(restaurant)
    db.refresh(stall)
    db.refresh(dmart)
    db.refresh(recipient_ngo)
    db.refresh(kitchen_ngo)

    # Donations
    donations = [
        Donation(
            donor_id=restaurant.id,
            title="Hyderabadi Chicken & Veg Biryani (Buffet Surplus)",
            category="prepared_meals",
            donor_type="restaurant_stall",
            description="Hot cooked dum biryani with raita and mirchi ka salan from afternoon lunch banquet. Kept at 65°C safe temperature.",
            dietary="non_veg",
            image_emoji="🍲",
            quantity=35.0,
            remaining_quantity=35.0,
            unit="portions",
            usable_until=now + timedelta(hours=5),
            storage="hot_holding",
            allergens=["nuts", "dairy"],
            pickup_address="100 Feet Road, HAL 2nd Stage, Indiranagar, Bengaluru",
            latitude=12.9716,
            longitude=77.5946,
            pickup_start=now + timedelta(minutes=10),
            pickup_end=now + timedelta(hours=3),
            packaging="Food-grade stainless insulated containers & trays",
            safety_verified=True,
            status="available",
        ),
        Donation(
            donor_id=stall.id,
            title="Fresh Steamed Idli & Vada with Sambar (Stall Surplus)",
            category="prepared_meals",
            donor_type="restaurant_stall",
            description="Morning fresh breakfast surplus from busy street stall. Clean, hygienic, packed in separate sealed boxes.",
            dietary="veg",
            image_emoji="🥘",
            quantity=50.0,
            remaining_quantity=50.0,
            unit="items",
            usable_until=now + timedelta(hours=6),
            storage="ambient",
            allergens=["mustard"],
            pickup_address="CMH Road Metro Station Gate 2, Bengaluru",
            latitude=12.9784,
            longitude=77.6408,
            pickup_start=now + timedelta(minutes=15),
            pickup_end=now + timedelta(hours=4),
            packaging="Disposable food-safe paper meal boxes",
            safety_verified=True,
            status="available",
        ),
        Donation(
            donor_id=dmart.id,
            title="Amul Taaza Homogenised Toned Milk (500ml Pouches)",
            category="dairy",
            donor_type="supermarket_dmart",
            description="Refrigerated milk pouches reaching best-before date tomorrow. Perfect for feeding homes or community kitchens.",
            dietary="veg",
            original_price=28.0,
            expiry_date=now + timedelta(hours=30),
            image_emoji="🥛",
            quantity=40.0,
            remaining_quantity=40.0,
            unit="items",
            usable_until=now + timedelta(hours=30),
            storage="refrigerated",
            allergens=["dairy"],
            pickup_address="D-Mart Supercenter, Old Airport Road, Bengaluru",
            latitude=12.9592,
            longitude=77.6534,
            pickup_start=now + timedelta(minutes=30),
            pickup_end=now + timedelta(hours=12),
            packaging="Original factory sealed crates",
            safety_verified=True,
            status="available",
        ),
        Donation(
            donor_id=dmart.id,
            title="Britannia 100% Whole Wheat Bread (400g Loaves)",
            category="bakery",
            donor_type="supermarket_dmart",
            description="Packaged bakery bread inventory approaching best-before date in 2 days. 100% sealed retail loaves.",
            dietary="veg",
            original_price=45.0,
            expiry_date=now + timedelta(days=2),
            image_emoji="🍞",
            quantity=30.0,
            remaining_quantity=20.0,
            unit="items",
            usable_until=now + timedelta(days=2),
            storage="ambient",
            allergens=["gluten"],
            pickup_address="D-Mart Supercenter, Old Airport Road, Bengaluru",
            latitude=12.9592,
            longitude=77.6534,
            pickup_start=now + timedelta(minutes=30),
            pickup_end=now + timedelta(hours=18),
            packaging="Manufacturer sealed retail bags",
            safety_verified=True,
            status="available",
        ),
        Donation(
            donor_id=dmart.id,
            title="Fresh Shimla Apples & Seasonal Oranges (Fruit Crate)",
            category="produce",
            donor_type="supermarket_dmart",
            description="Crisp sweet apples and vitamin-rich oranges with slight cosmetic spots. Cleared from display shelves for quick rescue.",
            dietary="vegan",
            original_price=120.0,
            expiry_date=now + timedelta(days=3),
            image_emoji="🍎",
            quantity=25.0,
            remaining_quantity=25.0,
            unit="kg",
            usable_until=now + timedelta(days=3),
            storage="ambient",
            allergens=[],
            pickup_address="D-Mart Supercenter, Old Airport Road, Bengaluru",
            latitude=12.9592,
            longitude=77.6534,
            pickup_start=now + timedelta(minutes=30),
            pickup_end=now + timedelta(hours=24),
            packaging="Plastic ventilated produce crates",
            safety_verified=True,
            status="available",
        ),
    ]
    db.add_all(donations)
    db.commit()
    for d in donations:
        db.refresh(d)

    bread_donation = donations[3]
    sample_claim = Allocation(
        donation_id=bread_donation.id,
        recipient_id=recipient_ngo.id,
        organization_name=recipient_ngo.name,
        quantity=10.0,
        status="approved",
        claim_reason="Evening breakfast snack for 45 resident children at Hope Haven shelter before study hours.",
        beneficiary_type="Children's Shelter / Orphanage",
        contact_phone=recipient_ngo.phone,
        pickup_code="PASS-7842",
        donor_notes="Ready at loading dock 2. Ask for Mr. Ramesh, shift supervisor.",
        estimated_pickup=now + timedelta(hours=3),
        created_by=recipient_ngo.id,
    )

    biryani_donation = donations[0]
    pending_claim = Allocation(
        donation_id=biryani_donation.id,
        recipient_id=kitchen_ngo.id,
        organization_name=kitchen_ngo.name,
        quantity=15.0,
        status="pending_donor",
        claim_reason="Distributing dinner to 60 night attendants and family members waiting outside Bowring General Hospital.",
        beneficiary_type="Hospital Patient Attendants & Community Kitchen",
        contact_phone=kitchen_ngo.phone,
        pickup_code="LOOP-3912",
        donor_notes="",
        estimated_pickup=now + timedelta(hours=2),
        created_by=kitchen_ngo.id,
    )
    db.add_all([sample_claim, pending_claim])
    db.commit()


@app.on_event("startup")
def create_tables():
    initialize_database()
    with SessionLocal() as db:
        seed_demo_data(db)



def create_access_token(user: User) -> str:
    return jwt.encode(
        {"sub": str(user.id), "role": user.role, "exp": datetime.now(timezone.utc).timestamp() + 86400},
        SECRET_KEY,
        algorithm=ALGORITHM,
    )


def current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    db: Session = Depends(get_db),
) -> User:
    if credentials is None:
        raise HTTPException(status_code=401, detail="Authentication required")
    try:
        payload = jwt.decode(credentials.credentials, SECRET_KEY, algorithms=[ALGORITHM])
        user_id = int(payload["sub"])
    except (JWTError, KeyError, ValueError):
        raise HTTPException(status_code=401, detail="Invalid or expired access token") from None
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(status_code=401, detail="Account no longer exists")
    return user


def require_role(*roles: str):
    def dependency(user: User = Depends(current_user)) -> User:
        if user.role not in roles:
            raise HTTPException(status_code=403, detail="Your role cannot perform this action")
        return user

    return dependency


def auth_response(user: User) -> AuthResponse:
    return AuthResponse(access_token=create_access_token(user), user=UserView.model_validate(user))


def audit(db: Session, user: User, action: str, entity_type: str, entity_id: int, details: str = ""):
    db.add(AuditEvent(actor_id=user.id, action=action, entity_type=entity_type, entity_id=entity_id, details=details))


def as_utc(value: datetime) -> datetime:
    return value.replace(tzinfo=timezone.utc) if value.tzinfo is None else value.astimezone(timezone.utc)


def candidate_data(db: Session):
    donations = db.scalars(select(Donation).where(Donation.status == "available", Donation.remaining_quantity > 0)).all()
    requests = db.scalars(select(RecipientRequest).where(RecipientRequest.status == "open", RecipientRequest.unmet_quantity > 0)).all()
    allocations = db.scalars(select(Allocation).where(Allocation.status == "approved")).all()
    allocation_counts: dict[int, float] = {}
    for allocation in allocations:
        allocation_counts[allocation.request_id] = allocation_counts.get(allocation.request_id, 0) + allocation.quantity
    donation_candidates = [
        DonationCandidate(
            id=item.id,
            title=item.title,
            category=item.category,
            quantity=item.remaining_quantity,
            unit=item.unit,
            usable_until=as_utc(item.usable_until),
            pickup_start=as_utc(item.pickup_start),
            pickup_end=as_utc(item.pickup_end),
            storage=item.storage,
            allergens=frozenset(item.allergens),
            latitude=item.latitude,
            longitude=item.longitude,
        )
        for item in donations
        if item.safety_verified
    ]
    request_candidates = [
        RequestCandidate(
            id=item.id,
            organization=item.organization,
            categories=frozenset(item.categories),
            unmet_quantity=item.unmet_quantity,
            unit=item.unit,
            urgency=item.urgency,
            storage=frozenset(item.storage),
            excluded_allergens=frozenset(item.excluded_allergens),
            needed_by=as_utc(item.needed_by),
            latitude=item.latitude,
            longitude=item.longitude,
            prior_allocations=allocation_counts.get(item.id, 0),
        )
        for item in requests
    ]
    return donations, requests, donation_candidates, request_candidates


@app.get("/api/health")
def health():
    return {"status": "ok", "database": "configured"}


@app.post("/api/auth/register", response_model=AuthResponse, status_code=201)
def register(payload: UserCreate, db: Session = Depends(get_db)):
    if payload.role == "coordinator":
        invite_code = os.getenv("COORDINATOR_INVITE_CODE")
        if not invite_code or payload.coordinator_invite != invite_code:
            raise HTTPException(status_code=403, detail="A valid coordinator invitation is required")
    email = payload.email.lower()
    if db.scalar(select(User).where(func.lower(User.email) == email)):
        raise HTTPException(status_code=409, detail="An account with this email already exists")
    user = User(
        name=payload.name.strip(),
        email=email,
        password_hash=password_hash.hash(payload.password),
        role=payload.role,
        org_type=payload.org_type or "restaurant",
        phone=payload.phone or "",
        city=payload.city or "",
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return auth_response(user)


@app.post("/api/auth/demo-login/{preset}", response_model=AuthResponse)
def demo_login(preset: str, db: Session = Depends(get_db)):
    presets = {
        "restaurant": {
            "name": "Paradise Biryani & Dhaba",
            "email": "paradise.biryani@foodloop.org",
            "role": "donor",
            "org_type": "restaurant",
            "phone": "+91 98450 12345",
            "city": "Bengaluru",
        },
        "supermarket": {
            "name": "D-Mart Supercenter (Indiranagar)",
            "email": "dmart.indiranagar@foodloop.org",
            "role": "donor",
            "org_type": "supermarket",
            "phone": "+91 98450 67890",
            "city": "Bengaluru",
        },
        "recipient": {
            "name": "Hope Haven Children's Shelter",
            "email": "hopehaven@foodloop.org",
            "role": "recipient",
            "org_type": "shelter",
            "phone": "+91 98765 43210",
            "city": "Bengaluru",
        },
        "coordinator": {
            "name": "City Logistics & Rescue Hub",
            "email": "coordinator@foodloop.org",
            "role": "coordinator",
            "org_type": "coordinator",
            "phone": "+91 98000 11223",
            "city": "Bengaluru",
        },
    }
    if preset not in presets:
        raise HTTPException(status_code=400, detail="Unknown demo preset. Available: restaurant, supermarket, recipient, coordinator")
    data = presets[preset]
    user = db.scalar(select(User).where(User.email == data["email"]))
    if user is None:
        user = User(
            name=data["name"],
            email=data["email"],
            password_hash=password_hash.hash("DemoAccess123!"),
            role=data["role"],
            org_type=data["org_type"],
            phone=data["phone"],
            city=data["city"],
        )
        db.add(user)
        db.commit()
        db.refresh(user)
    return auth_response(user)


@app.post("/api/seed")
def manual_seed(db: Session = Depends(get_db)):
    seed_demo_data(db)
    return {"status": "ok", "message": "Demo data populated"}


@app.post("/api/auth/login", response_model=AuthResponse)
def login(payload: UserLogin, db: Session = Depends(get_db)):
    email = payload.email.strip().lower()
    user = db.scalar(select(User).where(func.lower(User.email) == email))
    if user is None or not password_hash.verify(payload.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid credentials. Please check your email and password.")
    return auth_response(user)


@app.get("/api/auth/me", response_model=UserView)
def me(user: User = Depends(current_user)):
    return user


def serialize_donation(db: Session, item: Donation) -> dict:
    donor = db.get(User, item.donor_id)
    return {
        "id": item.id,
        "donor_id": item.donor_id,
        "donor_name": donor.name if donor else "Verified Food Partner",
        "donor_phone": donor.phone if donor else "",
        "donor_type": item.donor_type or "restaurant_stall",
        "title": item.title,
        "description": item.description or "",
        "category": item.category,
        "dietary": item.dietary or "veg",
        "original_price": item.original_price,
        "expiry_date": item.expiry_date,
        "image_emoji": item.image_emoji or ("🍲" if item.donor_type == "restaurant_stall" else "🏬"),
        "quantity": item.quantity,
        "remaining_quantity": item.remaining_quantity,
        "unit": item.unit,
        "usable_until": item.usable_until,
        "storage": item.storage,
        "allergens": item.allergens,
        "pickup_address": item.pickup_address,
        "latitude": item.latitude,
        "longitude": item.longitude,
        "pickup_start": item.pickup_start,
        "pickup_end": item.pickup_end,
        "packaging": item.packaging,
        "safety_verified": item.safety_verified,
        "status": item.status,
        "created_at": item.created_at,
    }


@app.get("/api/donations/explore")
def explore_donations(
    category: str | None = None,
    donor_type: str | None = None,
    dietary: str | None = None,
    search: str | None = None,
    db: Session = Depends(get_db),
):
    query = select(Donation).where(Donation.status == "available", Donation.remaining_quantity > 0).order_by(Donation.usable_until)
    if category and category != "all":
        query = query.where(Donation.category == category)
    if donor_type and donor_type != "all":
        query = query.where(Donation.donor_type == donor_type)
    if dietary and dietary != "all":
        query = query.where(Donation.dietary == dietary)

    items = db.scalars(query).all()
    results = [serialize_donation(db, item) for item in items]
    if search:
        term = search.lower()
        results = [
            r for r in results
            if term in r["title"].lower() or term in r["pickup_address"].lower() or term in r["donor_name"].lower() or term in r["description"].lower()
        ]
    return results


@app.post("/api/donations", response_model=DonationView, status_code=201)
def create_donation(
    payload: DonationCreate,
    user: User = Depends(require_role("donor")),
    db: Session = Depends(get_db),
):
    donation = Donation(
        donor_id=user.id,
        remaining_quantity=payload.quantity,
        status="available",
        **payload.model_dump(),
    )
    db.add(donation)
    db.commit()
    db.refresh(donation)
    return serialize_donation(db, donation)


@app.get("/api/donations", response_model=list[DonationView])
def list_donations(user: User = Depends(current_user), db: Session = Depends(get_db)):
    query = select(Donation).order_by(Donation.usable_until)
    if user.role == "donor":
        query = query.where(Donation.donor_id == user.id)
    elif user.role == "recipient":
        query = query.where(Donation.status == "available", Donation.remaining_quantity > 0)
    items = db.scalars(query).all()
    return [serialize_donation(db, item) for item in items]



@app.post("/api/requests", response_model=RequestView, status_code=201)
def create_request(
    payload: RequestCreate,
    user: User = Depends(require_role("recipient")),
    db: Session = Depends(get_db),
):
    request = RecipientRequest(
        recipient_id=user.id,
        unmet_quantity=payload.requested_quantity,
        status="open",
        **payload.model_dump(),
    )
    db.add(request)
    db.commit()
    db.refresh(request)
    return request


@app.get("/api/requests", response_model=list[RequestView])
def list_requests(user: User = Depends(current_user), db: Session = Depends(get_db)):
    query = select(RecipientRequest).order_by(RecipientRequest.urgency.desc())
    if user.role == "recipient":
        query = query.where(RecipientRequest.recipient_id == user.id)
    elif user.role == "donor":
        query = query.where(RecipientRequest.status == "open", RecipientRequest.unmet_quantity > 0)
    return db.scalars(query).all()


@app.get("/api/matches")
def get_matches(user: User = Depends(require_role("coordinator")), db: Session = Depends(get_db)):
    _, _, donations, requests = candidate_data(db)
    matches, rejected = recommend_matches(donations, requests)
    return {"matches": matches, "rejected": rejected, "basis": "deterministic constraints-first score"}


@app.post("/api/allocations", status_code=201)
def propose_allocation(
    payload: AllocationCreate,
    user: User = Depends(require_role("coordinator")),
    db: Session = Depends(get_db),
):
    _, _, donations, requests = candidate_data(db)
    matches, _ = recommend_matches(donations, requests)
    recommendation = next(
        (item for item in matches if item["donation_id"] == payload.donation_id and item["request_id"] == payload.request_id),
        None,
    )
    if recommendation is None or payload.quantity > recommendation["quantity"]:
        raise HTTPException(status_code=409, detail="This pair or quantity is no longer feasible")
    allocation = Allocation(
        donation_id=payload.donation_id,
        request_id=payload.request_id,
        quantity=payload.quantity,
        status="pending_recipient",
        created_by=user.id,
    )
    db.add(allocation)
    db.commit()
    db.refresh(allocation)
    return allocation_view(db, allocation)


def allocation_view(db: Session, allocation: Allocation):
    donation = db.get(Donation, allocation.donation_id)
    request = db.get(RecipientRequest, allocation.request_id) if allocation.request_id else None
    donor = db.get(User, donation.donor_id) if donation else None
    recipient = db.get(User, allocation.recipient_id) if allocation.recipient_id else None

    org_name = allocation.organization_name
    if not org_name and request:
        org_name = request.organization
    if not org_name and recipient:
        org_name = recipient.name

    return {
        "id": allocation.id,
        "donation_id": allocation.donation_id,
        "donation_title": donation.title if donation else "",
        "donor_id": donation.donor_id if donation else 0,
        "donor_name": donor.name if donor else "Verified Food Partner",
        "donor_phone": donor.phone if donor else "",
        "donor_type": getattr(donation, "donor_type", "restaurant_stall") if donation else "restaurant_stall",
        "pickup_address": donation.pickup_address if donation else "",
        "request_id": allocation.request_id,
        "recipient_id": allocation.recipient_id,
        "organization": org_name or "Community Partner",
        "quantity": allocation.quantity,
        "unit": donation.unit if donation else "",
        "status": allocation.status,
        "claim_reason": allocation.claim_reason or "",
        "beneficiary_type": allocation.beneficiary_type or "",
        "contact_phone": allocation.contact_phone or (recipient.phone if recipient else ""),
        "pickup_code": allocation.pickup_code or "",
        "donor_notes": allocation.donor_notes or "",
        "estimated_pickup": allocation.estimated_pickup,
        "created_at": allocation.created_at,
    }


@app.get("/api/allocations")
def list_allocations(user: User = Depends(current_user), db: Session = Depends(get_db)):
    query = select(Allocation).order_by(Allocation.created_at.desc())
    if user.role == "recipient":
        query = query.outerjoin(RecipientRequest, Allocation.request_id == RecipientRequest.id).where(
            (Allocation.recipient_id == user.id) | (RecipientRequest.recipient_id == user.id)
        )
    elif user.role == "donor":
        query = query.join(Donation, Allocation.donation_id == Donation.id).where(Donation.donor_id == user.id)
    return [allocation_view(db, allocation) for allocation in db.scalars(query).all()]


@app.post("/api/donations/{donation_id}/claim", status_code=201)
def claim_donation(
    donation_id: int,
    payload: DirectClaimCreate,
    user: User = Depends(require_role("recipient", "coordinator")),
    db: Session = Depends(get_db),
):
    donation = db.get(Donation, donation_id)
    if donation is None or donation.status != "available":
        raise HTTPException(status_code=404, detail="Food listing is no longer available")
    if payload.quantity > donation.remaining_quantity:
        raise HTTPException(
            status_code=400,
            detail=f"Requested {payload.quantity} {donation.unit}, but only {donation.remaining_quantity} {donation.unit} is available",
        )
    if not payload.claim_reason or len(payload.claim_reason.strip()) < 5:
        raise HTTPException(status_code=422, detail="A proper reason explaining how this food will be used is required")

    pickup_code = f"PASS-{random.randint(1000, 9999)}"
    org_name = payload.organization_name.strip() or user.name

    allocation = Allocation(
        donation_id=donation.id,
        request_id=None,
        recipient_id=user.id,
        organization_name=org_name,
        quantity=payload.quantity,
        status="pending_donor",
        claim_reason=payload.claim_reason.strip(),
        beneficiary_type=payload.beneficiary_type.strip(),
        contact_phone=payload.contact_phone.strip() or user.phone,
        pickup_code=pickup_code,
        estimated_pickup=payload.estimated_pickup,
        created_by=user.id,
    )
    db.add(allocation)
    audit(db, user, "food_claim_submitted", "allocation", 0, f"Claimed {payload.quantity} {donation.unit} with reason: {payload.claim_reason[:40]}")
    db.commit()
    db.refresh(allocation)
    return allocation_view(db, allocation)


@app.patch("/api/allocations/{allocation_id}/donor-decision")
def donor_claim_decision(
    allocation_id: int,
    payload: DonorClaimDecision,
    user: User = Depends(require_role("donor", "coordinator")),
    db: Session = Depends(get_db),
):
    allocation = db.get(Allocation, allocation_id)
    if allocation is None:
        raise HTTPException(status_code=404, detail="Claim not found")
    donation = db.get(Donation, allocation.donation_id)
    if donation is None:
        raise HTTPException(status_code=404, detail="Donation record not found")
    if user.role != "coordinator" and donation.donor_id != user.id:
        raise HTTPException(status_code=403, detail="Only the donor who listed this food can approve or decline this claim")
    if allocation.status not in {"pending_donor", "pending_recipient"}:
        raise HTTPException(status_code=409, detail="Claim is no longer awaiting a response")

    if payload.accept:
        if donation.remaining_quantity < allocation.quantity:
            raise HTTPException(status_code=409, detail="Available food quantity is less than requested claim amount")
        donation.remaining_quantity -= allocation.quantity
        if donation.remaining_quantity <= 0:
            donation.status = "matched"
        allocation.status = "approved"
        if not allocation.pickup_code:
            allocation.pickup_code = f"PASS-{random.randint(1000, 9999)}"
        if payload.donor_notes:
            allocation.donor_notes = payload.donor_notes.strip()
        audit(db, user, "claim_approved", "allocation", allocation.id)
    else:
        allocation.status = "rejected"
        if payload.donor_notes:
            allocation.donor_notes = payload.donor_notes.strip()
        audit(db, user, "claim_rejected", "allocation", allocation.id)

    db.commit()
    db.refresh(allocation)
    return allocation_view(db, allocation)


@app.patch("/api/allocations/{allocation_id}/decision")
def recipient_decision(
    allocation_id: int,
    payload: AllocationDecision,
    user: User = Depends(require_role("recipient")),
    db: Session = Depends(get_db),
):
    allocation = db.get(Allocation, allocation_id)
    request = db.get(RecipientRequest, allocation.request_id) if (allocation and allocation.request_id) else None
    if allocation is None:
        raise HTTPException(status_code=404, detail="Offer not found")
    if allocation.recipient_id != user.id and (request is None or request.recipient_id != user.id):
        raise HTTPException(status_code=404, detail="Offer not found for your account")
    if allocation.status != "pending_recipient":
        raise HTTPException(status_code=409, detail="Offer is no longer awaiting a response")
    allocation.status = "accepted" if payload.accept else "rejected"
    db.commit()
    return allocation_view(db, allocation)


@app.patch("/api/allocations/{allocation_id}/approve")
def approve_allocation(
    allocation_id: int,
    user: User = Depends(require_role("coordinator")),
    db: Session = Depends(get_db),
):
    allocation = db.get(Allocation, allocation_id)
    if allocation is None:
        raise HTTPException(status_code=404, detail="Allocation not found")
    if allocation.status != "accepted":
        raise HTTPException(status_code=409, detail="Recipient acceptance is required before approval")

    donation = db.get(Donation, allocation.donation_id)
    request = db.get(RecipientRequest, allocation.request_id) if allocation.request_id else None
    if donation is None:
        raise HTTPException(status_code=404, detail="Donation no longer exists")

    donation_update = db.execute(
        update(Donation)
        .where(
            Donation.id == donation.id,
            Donation.status == "available",
            Donation.remaining_quantity >= allocation.quantity,
            Donation.safety_verified.is_(True),
        )
        .values(remaining_quantity=Donation.remaining_quantity - allocation.quantity)
    )
    if donation_update.rowcount != 1:
        db.rollback()
        raise HTTPException(status_code=409, detail="Donation quantity was allocated by another operation")

    if request is not None:
        request_update = db.execute(
            update(RecipientRequest)
            .where(
                RecipientRequest.id == request.id,
                RecipientRequest.status == "open",
                RecipientRequest.unmet_quantity >= allocation.quantity,
            )
            .values(unmet_quantity=RecipientRequest.unmet_quantity - allocation.quantity)
        )
        if request_update.rowcount != 1:
            db.rollback()
            raise HTTPException(status_code=409, detail="Recipient demand was fulfilled by another operation")
        db.refresh(request)
        request.status = "fulfilled" if request.unmet_quantity <= 0 else "open"

    db.refresh(donation)
    donation.status = "matched" if donation.remaining_quantity <= 0 else "available"
    allocation.status = "approved"
    if not allocation.pickup_code:
        allocation.pickup_code = f"PASS-{random.randint(1000, 9999)}"
    audit(db, user, "allocation_approved", "allocation", allocation.id)
    db.commit()
    db.refresh(allocation)
    return allocation_view(db, allocation)


@app.patch("/api/allocations/{allocation_id}/event")
def update_delivery_event(
    allocation_id: int,
    event: str,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
):
    allocation = db.get(Allocation, allocation_id)
    if allocation is None:
        raise HTTPException(status_code=404, detail="Allocation not found")
    request = db.get(RecipientRequest, allocation.request_id) if allocation.request_id else None
    donation = db.get(Donation, allocation.donation_id)

    is_claim_recipient = (allocation.recipient_id == user.id) or (request is not None and request.recipient_id == user.id)
    is_claim_donor = (donation is not None and donation.donor_id == user.id)
    is_coordinator = (user.role == "coordinator")

    if event == "collected":
        if not (is_coordinator or is_claim_donor or is_claim_recipient) or allocation.status != "approved":
            raise HTTPException(status_code=403, detail="Pickup can only be recorded for approved allocations")
        allocation.status = "collected"
    elif event == "delivered":
        if not (is_coordinator or is_claim_recipient) or allocation.status != "collected":
            raise HTTPException(status_code=403, detail="Recipient or coordinator confirmation is required after collection")
        allocation.status = "delivered"
    else:
        raise HTTPException(status_code=422, detail="Event must be collected or delivered")

    if donation is not None and allocation.status == "delivered" and donation.remaining_quantity <= 0:
        donation.status = "delivered"
    audit(db, user, f"allocation_{event}", "allocation", allocation.id)
    db.commit()
    return allocation_view(db, allocation)



@app.get("/api/dashboard")
def dashboard(user: User = Depends(current_user), db: Session = Depends(get_db)):
    donations = db.scalars(select(Donation)).all()
    requests = db.scalars(select(RecipientRequest)).all()
    allocations_query = select(Allocation)
    if user.role == "donor":
        donations = [item for item in donations if item.donor_id == user.id]
        allocations_query = allocations_query.join(Donation).where(Donation.donor_id == user.id)
    elif user.role == "recipient":
        requests = [item for item in requests if item.recipient_id == user.id]
        allocations_query = allocations_query.join(RecipientRequest).where(RecipientRequest.recipient_id == user.id)

    allocations = db.scalars(allocations_query).all()
    delivered = [item for item in allocations if item.status == "delivered"]
    categories: dict[str, float] = {}
    for donation in donations:
        categories[donation.category] = categories.get(donation.category, 0) + donation.quantity
    return {
        "role": user.role,
        "active_donations": sum(1 for item in donations if item.status == "available" and item.remaining_quantity > 0),
        "active_requests": sum(1 for item in requests if item.status == "open" and item.unmet_quantity > 0),
        "pending_matches": sum(1 for item in allocations if item.status in {"pending_recipient", "accepted"}),
        "scheduled_pickups": sum(1 for item in allocations if item.status in {"approved", "collected"}),
        "completed_deliveries": len(delivered),
        "listed_quantity": round(sum(item.quantity for item in donations), 2),
        "delivered_quantity": round(sum(item.quantity for item in delivered), 2),
        "unmet_demand": round(sum(item.unmet_quantity for item in requests if item.status == "open"), 2),
        "category_distribution": categories,
        "data_notice": "Values are computed from records stored in this database. Estimates are not measured outcomes.",
    }


@app.get("/api/vehicles")
def list_vehicles(user: User = Depends(require_role("coordinator")), db: Session = Depends(get_db)):
    return db.scalars(select(Vehicle).order_by(Vehicle.name)).all()


@app.post("/api/vehicles", status_code=201)
def create_vehicle(
    payload: VehicleCreate,
    user: User = Depends(require_role("coordinator")),
    db: Session = Depends(get_db),
):
    vehicle = Vehicle(**payload.model_dump(), available=True)
    db.add(vehicle)
    db.commit()
    db.refresh(vehicle)
    audit(db, user, "vehicle_created", "vehicle", vehicle.id)
    db.commit()
    return vehicle


@app.post("/api/routes/optimize")
def create_route_plan(user: User = Depends(require_role("coordinator")), db: Session = Depends(get_db)):
    approved = db.scalars(select(Allocation).where(Allocation.status == "approved")).all()
    jobs = []
    for allocation in approved:
        donation = db.get(Donation, allocation.donation_id)
        request = db.get(RecipientRequest, allocation.request_id)
        if donation is None or request is None:
            continue
        jobs.append(
            {
                "allocation_id": allocation.id,
                "donation_title": donation.title,
                "organization": request.organization,
                "quantity": allocation.quantity,
                "unit": donation.unit,
                "storage": donation.storage,
                "pickup": (donation.latitude, donation.longitude),
                "delivery": (request.latitude, request.longitude),
                "pickup_address": donation.pickup_address,
                "delivery_address": request.delivery_address,
                "pickup_start": as_utc(donation.pickup_start),
                "pickup_end": as_utc(donation.pickup_end),
                "deadline": min(as_utc(donation.usable_until), as_utc(request.needed_by)),
            }
        )
    vehicle_records = db.scalars(select(Vehicle).where(Vehicle.available.is_(True))).all()
    vehicles = [
        {
            "id": vehicle.id,
            "name": vehicle.name,
            "capacity": vehicle.capacity,
            "unit": vehicle.unit,
            "storage": vehicle.storage,
            "latitude": vehicle.latitude,
            "longitude": vehicle.longitude,
        }
        for vehicle in vehicle_records
    ]
    result = optimize_vehicle_routes(jobs, vehicles)
    run = OptimizationRun(
        coordinator_id=user.id,
        status="feasible" if result["optimized"] else "infeasible",
        result=result,
    )
    db.add(run)
    db.commit()
    db.refresh(run)
    return {"run_id": run.id, **result}


@app.get("/api/routes/runs")
def list_route_runs(user: User = Depends(require_role("coordinator")), db: Session = Depends(get_db)):
    runs = db.scalars(select(OptimizationRun).order_by(OptimizationRun.created_at.desc()).limit(20)).all()
    return [{"id": run.id, "status": run.status, "created_at": run.created_at, **run.result} for run in runs]


@app.post("/api/predictions/forecast")
def create_forecast(
    category: str,
    user: User = Depends(require_role("coordinator")),
    db: Session = Depends(get_db),
):
    allowed = {"prepared_meals", "bakery", "produce", "packaged", "dairy"}
    if category not in allowed:
        raise HTTPException(status_code=422, detail="Unknown food category")
    history = [
        {"time": item.created_at, "category": item.category, "quantity": item.quantity}
        for item in db.scalars(select(Donation).where(Donation.category == category).order_by(Donation.created_at)).all()
    ]
    result = train_and_forecast(history, category)
    run = PredictionRun(
        category=category,
        source=result["source"],
        sample_count=result.get("training_rows", result["observed_records"]),
        metrics=result.get("metrics") or {},
        forecast=result["forecast"],
        model_path=result.get("model_path"),
    )
    db.add(run)
    db.commit()
    db.refresh(run)
    return {"run_id": run.id, **result}


@app.get("/api/predictions/runs")
def list_prediction_runs(user: User = Depends(require_role("coordinator")), db: Session = Depends(get_db)):
    runs = db.scalars(select(PredictionRun).order_by(PredictionRun.created_at.desc()).limit(20)).all()
    return [
        {
            "id": run.id,
            "category": run.category,
            "source": run.source,
            "sample_count": run.sample_count,
            "metrics": run.metrics,
            "forecast": run.forecast,
            "created_at": run.created_at,
        }
        for run in runs
    ]