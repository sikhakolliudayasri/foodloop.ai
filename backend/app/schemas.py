from datetime import datetime
from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator, model_validator


class UserCreate(BaseModel):
    name: str = Field(min_length=2, max_length=120)
    email: EmailStr
    password: str = Field(min_length=8, max_length=128)
    role: Literal["donor", "recipient", "coordinator"]
    org_type: str = "restaurant"
    phone: str = ""
    city: str = ""
    coordinator_invite: str | None = None

    @field_validator("email", mode="before")
    @classmethod
    def normalize_email(cls, value: str) -> str:
        return value.strip().lower() if isinstance(value, str) else value


class UserLogin(BaseModel):
    email: EmailStr
    password: str

    @field_validator("email", mode="before")
    @classmethod
    def normalize_email(cls, value: str) -> str:
        return value.strip().lower() if isinstance(value, str) else value


class UserView(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    name: str
    email: EmailStr
    role: str
    org_type: str = "restaurant"
    phone: str = ""
    city: str = ""


class AuthResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    user: UserView


class DonationCreate(BaseModel):
    title: str = Field(min_length=3, max_length=160)
    category: Literal["prepared_meals", "bakery", "produce", "packaged", "dairy"]
    donor_type: Literal["restaurant_stall", "supermarket_dmart"] = "restaurant_stall"
    description: str = Field(default="", max_length=1000)
    dietary: Literal["veg", "non_veg", "vegan", "halal", "egg", "any"] = "veg"
    original_price: float | None = None
    expiry_date: datetime | None = None
    image_emoji: str = "🍲"
    quantity: float = Field(gt=0, le=100000)
    unit: Literal["kg", "portions", "items", "packets"] = "kg"
    usable_until: datetime
    storage: Literal["ambient", "refrigerated", "frozen", "hot_holding"]
    allergens: list[str] = Field(default_factory=list)
    pickup_address: str = Field(min_length=5, max_length=240)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    pickup_start: datetime
    pickup_end: datetime
    packaging: str = Field(default="", max_length=160)
    safety_verified: bool

    @model_validator(mode="after")
    def validate_times_and_safety(self):
        now = datetime.now(self.usable_until.tzinfo)
        if self.usable_until <= now:
            raise ValueError("usable_until must be in the future")
        if self.pickup_start <= now:
            raise ValueError("pickup_start must be in the future")
        if self.pickup_end <= self.pickup_start:
            raise ValueError("pickup_end must be after pickup_start")
        if self.pickup_end > self.usable_until:
            raise ValueError("pickup window must end before usable_until")
        if not self.safety_verified:
            raise ValueError("A responsible donor must verify food safety before listing")
        return self


class DonationView(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    donor_id: int
    title: str
    category: str
    donor_type: str = "restaurant_stall"
    description: str = ""
    dietary: str = "veg"
    original_price: float | None = None
    expiry_date: datetime | None = None
    image_emoji: str = "🍲"
    quantity: float
    remaining_quantity: float
    unit: str
    usable_until: datetime
    storage: str
    allergens: list[str]
    pickup_address: str
    latitude: float
    longitude: float
    pickup_start: datetime
    pickup_end: datetime
    packaging: str
    safety_verified: bool
    status: str
    created_at: datetime
    donor_name: str | None = None
    donor_phone: str | None = None



class RequestCreate(BaseModel):
    organization: str = Field(min_length=2, max_length=160)
    categories: list[str] = Field(min_length=1)
    requested_quantity: float = Field(gt=0, le=100000)
    unit: Literal["kg", "portions", "items"] = "kg"
    urgency: int = Field(ge=1, le=10)
    storage: list[Literal["ambient", "refrigerated", "frozen", "hot_holding"]] = Field(min_length=1)
    excluded_allergens: list[str] = Field(default_factory=list)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)
    delivery_address: str = Field(min_length=5, max_length=240)
    needed_by: datetime

    @field_validator("needed_by")
    @classmethod
    def validate_needed_by(cls, value):
        if value <= datetime.now(value.tzinfo):
            raise ValueError("needed_by must be in the future")
        return value

    @field_validator("categories")
    @classmethod
    def validate_categories(cls, values):
        allowed = {"prepared_meals", "bakery", "produce", "packaged", "dairy"}
        if not set(values).issubset(allowed):
            raise ValueError("Unknown food category")
        return values


class RequestView(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    recipient_id: int
    organization: str
    categories: list[str]
    requested_quantity: float
    unmet_quantity: float
    unit: str
    urgency: int
    storage: list[str]
    excluded_allergens: list[str]
    latitude: float
    longitude: float
    delivery_address: str
    needed_by: datetime
    status: str
    created_at: datetime


class AllocationCreate(BaseModel):
    donation_id: int
    request_id: int
    quantity: float = Field(gt=0)


class AllocationDecision(BaseModel):
    accept: bool


class DirectClaimCreate(BaseModel):
    quantity: float = Field(gt=0)
    claim_reason: str = Field(min_length=5, max_length=1000)
    beneficiary_type: str = Field(default="Community Dining / Shelter", max_length=120)
    contact_phone: str = Field(default="", max_length=40)
    organization_name: str = Field(default="", max_length=160)
    estimated_pickup: datetime | None = None


class DonorClaimDecision(BaseModel):
    accept: bool
    donor_notes: str = Field(default="", max_length=500)



class VehicleCreate(BaseModel):
    name: str = Field(min_length=2, max_length=120)
    capacity: float = Field(gt=0, le=100000)
    unit: Literal["kg"] = "kg"
    storage: list[Literal["ambient", "refrigerated", "frozen", "hot_holding"]] = Field(min_length=1)
    latitude: float = Field(ge=-90, le=90)
    longitude: float = Field(ge=-180, le=180)