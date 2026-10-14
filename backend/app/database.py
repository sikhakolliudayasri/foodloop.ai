import os
from pathlib import Path

from dotenv import load_dotenv
from sqlalchemy import create_engine, inspect, text
from sqlalchemy.orm import DeclarativeBase, sessionmaker


load_dotenv(Path(__file__).resolve().parents[1] / ".env")
DATABASE_URL = os.getenv("DATABASE_URL", "sqlite:///./backend/foodloop.db")
if "DATABASE_URL" not in os.environ:
    DATABASE_URL = f"sqlite:///{(Path(__file__).resolve().parents[1] / 'foodloop.db').as_posix()}"
connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}
engine = create_engine(DATABASE_URL, connect_args=connect_args)
SessionLocal = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


def initialize_database():
    Base.metadata.create_all(bind=engine)
    if engine.dialect.name != "sqlite":
        return

    legacy_columns = {
        "users": {
            "org_type": "VARCHAR(60) NOT NULL DEFAULT 'restaurant'",
            "phone": "VARCHAR(40) NOT NULL DEFAULT ''",
            "city": "VARCHAR(80) NOT NULL DEFAULT ''",
        },
        "donations": {
            "donor_type": "VARCHAR(40) NOT NULL DEFAULT 'restaurant_stall'",
            "description": "TEXT NOT NULL DEFAULT ''",
            "dietary": "VARCHAR(30) NOT NULL DEFAULT 'veg'",
            "original_price": "FLOAT",
            "expiry_date": "DATETIME",
            "image_emoji": "VARCHAR(20) NOT NULL DEFAULT '\U0001f372'",
        },
        "allocations": {
            "recipient_id": "INTEGER",
            "organization_name": "VARCHAR(160) NOT NULL DEFAULT ''",
            "claim_reason": "TEXT NOT NULL DEFAULT ''",
            "beneficiary_type": "VARCHAR(100) NOT NULL DEFAULT ''",
            "contact_phone": "VARCHAR(40) NOT NULL DEFAULT ''",
            "pickup_code": "VARCHAR(20) NOT NULL DEFAULT ''",
            "donor_notes": "TEXT NOT NULL DEFAULT ''",
            "estimated_pickup": "DATETIME",
        },
    }
    with engine.begin() as connection:
        inspector = inspect(connection)
        for table_name, columns in legacy_columns.items():
            if not inspector.has_table(table_name):
                continue
            existing_columns = {column["name"] for column in inspector.get_columns(table_name)}
            for column_name, definition in columns.items():
                if column_name not in existing_columns:
                    connection.execute(
                        text(f"ALTER TABLE {table_name} ADD COLUMN {column_name} {definition}")
                    )
                    existing_columns.add(column_name)


def get_db():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()