from datetime import datetime, timezone

from sqlalchemy import DateTime, Float, ForeignKey, Integer, JSON, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from .database import Base


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(120))
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    role: Mapped[str] = mapped_column(String(20), index=True)
    org_type: Mapped[str] = mapped_column(String(60), default="restaurant")
    phone: Mapped[str] = mapped_column(String(40), default="")
    city: Mapped[str] = mapped_column(String(80), default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class Donation(Base):
    __tablename__ = "donations"

    id: Mapped[int] = mapped_column(primary_key=True)
    donor_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    title: Mapped[str] = mapped_column(String(160))
    category: Mapped[str] = mapped_column(String(40), index=True)
    donor_type: Mapped[str] = mapped_column(String(40), default="restaurant_stall", index=True)
    description: Mapped[str] = mapped_column(Text, default="")
    dietary: Mapped[str] = mapped_column(String(30), default="veg")
    original_price: Mapped[float | None] = mapped_column(Float, nullable=True)
    expiry_date: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    image_emoji: Mapped[str] = mapped_column(String(20), default="🍲")
    quantity: Mapped[float] = mapped_column(Float)
    remaining_quantity: Mapped[float] = mapped_column(Float)
    unit: Mapped[str] = mapped_column(String(16))
    usable_until: Mapped[datetime] = mapped_column(DateTime(timezone=True), index=True)
    storage: Mapped[str] = mapped_column(String(40))
    allergens: Mapped[list[str]] = mapped_column(JSON, default=list)
    pickup_address: Mapped[str] = mapped_column(String(240))
    latitude: Mapped[float] = mapped_column(Float)
    longitude: Mapped[float] = mapped_column(Float)
    pickup_start: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    pickup_end: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    packaging: Mapped[str] = mapped_column(String(160), default="")
    safety_verified: Mapped[bool] = mapped_column(default=False)
    status: Mapped[str] = mapped_column(String(24), default="available", index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class RecipientRequest(Base):
    __tablename__ = "recipient_requests"

    id: Mapped[int] = mapped_column(primary_key=True)
    recipient_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    organization: Mapped[str] = mapped_column(String(160))
    categories: Mapped[list[str]] = mapped_column(JSON)
    requested_quantity: Mapped[float] = mapped_column(Float)
    unmet_quantity: Mapped[float] = mapped_column(Float)
    unit: Mapped[str] = mapped_column(String(16))
    urgency: Mapped[int] = mapped_column(Integer)
    storage: Mapped[list[str]] = mapped_column(JSON)
    excluded_allergens: Mapped[list[str]] = mapped_column(JSON, default=list)
    latitude: Mapped[float] = mapped_column(Float)
    longitude: Mapped[float] = mapped_column(Float)
    delivery_address: Mapped[str] = mapped_column(String(240))
    needed_by: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    status: Mapped[str] = mapped_column(String(24), default="open", index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class Allocation(Base):
    __tablename__ = "allocations"

    id: Mapped[int] = mapped_column(primary_key=True)
    donation_id: Mapped[int] = mapped_column(ForeignKey("donations.id"), index=True)
    request_id: Mapped[int | None] = mapped_column(ForeignKey("recipient_requests.id"), nullable=True, index=True)
    recipient_id: Mapped[int | None] = mapped_column(ForeignKey("users.id"), nullable=True, index=True)
    organization_name: Mapped[str] = mapped_column(String(160), default="")
    quantity: Mapped[float] = mapped_column(Float)
    status: Mapped[str] = mapped_column(String(24), default="pending_recipient", index=True)
    claim_reason: Mapped[str] = mapped_column(Text, default="")
    beneficiary_type: Mapped[str] = mapped_column(String(100), default="")
    contact_phone: Mapped[str] = mapped_column(String(40), default="")
    pickup_code: Mapped[str] = mapped_column(String(20), default="")
    donor_notes: Mapped[str] = mapped_column(Text, default="")
    estimated_pickup: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_by: Mapped[int] = mapped_column(ForeignKey("users.id"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now, onupdate=utc_now)



class AuditEvent(Base):
    __tablename__ = "audit_events"

    id: Mapped[int] = mapped_column(primary_key=True)
    actor_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    action: Mapped[str] = mapped_column(String(80))
    entity_type: Mapped[str] = mapped_column(String(40))
    entity_id: Mapped[int] = mapped_column(Integer)
    details: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class Vehicle(Base):
    __tablename__ = "vehicles"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(120))
    capacity: Mapped[float] = mapped_column(Float)
    unit: Mapped[str] = mapped_column(String(16), default="kg")
    storage: Mapped[list[str]] = mapped_column(JSON, default=list)
    latitude: Mapped[float] = mapped_column(Float)
    longitude: Mapped[float] = mapped_column(Float)
    available: Mapped[bool] = mapped_column(default=True, index=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class OptimizationRun(Base):
    __tablename__ = "optimization_runs"

    id: Mapped[int] = mapped_column(primary_key=True)
    coordinator_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    status: Mapped[str] = mapped_column(String(24))
    result: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)


class PredictionRun(Base):
    __tablename__ = "prediction_runs"

    id: Mapped[int] = mapped_column(primary_key=True)
    category: Mapped[str] = mapped_column(String(40), index=True)
    source: Mapped[str] = mapped_column(String(80))
    sample_count: Mapped[int] = mapped_column(Integer)
    metrics: Mapped[dict] = mapped_column(JSON)
    forecast: Mapped[list] = mapped_column(JSON)
    model_path: Mapped[str | None] = mapped_column(String(240), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utc_now)