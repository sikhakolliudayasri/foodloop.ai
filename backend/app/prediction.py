from __future__ import annotations

import random
from datetime import datetime, timedelta, timezone
from pathlib import Path

try:
    import joblib
    import numpy as np
    import pandas as pd
    from sklearn.compose import ColumnTransformer
    from sklearn.ensemble import RandomForestRegressor
    from sklearn.metrics import mean_absolute_error, mean_absolute_percentage_error
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import OneHotEncoder
    HAS_ML = True
except ImportError:
    joblib = None
    np = None
    pd = None
    HAS_ML = False



MODEL_DIR = Path(__file__).resolve().parents[1] / "models"
FEATURES = ["category", "weekday", "hour", "month", "is_weekend"]


def reproducible_synthetic_history(category: str, *, now: datetime | None = None) -> pd.DataFrame:
    current_time = now or datetime.now(timezone.utc)
    rng = random.Random(20261014)
    rows = []
    for day_offset in range(240, 0, -1):
        for event_hour in (8, 12, 17):
            event_time = current_time - timedelta(days=day_offset)
            event_time = event_time.replace(hour=event_hour, minute=0, second=0, microsecond=0)
            weekday_factor = 1.18 if event_time.weekday() in (4, 5) else 0.94
            month_factor = 1 + 0.08 * np.sin(2 * np.pi * event_time.timetuple().tm_yday / 365)
            quantity = max(0.5, rng.gauss(17, 5) * weekday_factor * month_factor)
            rows.append({"time": event_time, "category": category, "quantity": round(quantity, 3)})
    return pd.DataFrame(rows)


def _features(frame: pd.DataFrame) -> pd.DataFrame:
    dates = pd.to_datetime(frame["time"], utc=True)
    return pd.DataFrame(
        {
            "category": frame["category"].astype(str),
            "weekday": dates.dt.weekday,
            "hour": dates.dt.hour,
            "month": dates.dt.month,
            "is_weekend": (dates.dt.weekday >= 5).astype(int),
        }
    )


def train_and_forecast(
    history: list[dict],
    category: str,
    *,
    now: datetime | None = None,
    forecast_days: int = 7,
) -> dict:
    current_time = now or datetime.now(timezone.utc)
    if not HAS_ML:
        return {
            "source": "heuristic baseline (ML packages not loaded)",
            "observed_records": len(history),
            "training_rows": len(history),
            "model": "historical average",
            "metrics": None,
            "forecast": [{"time": (current_time + timedelta(days=i)).isoformat(), "quantity": 10.0} for i in range(1, forecast_days + 1)],
            "model_path": None,
            "limitation": "scikit-learn is not installed in the current environment.",
        }
    observed = pd.DataFrame(history)
    observed_count = len(observed)
    if observed_count >= 30:
        data = observed.loc[:, ["time", "category", "quantity"]].copy()
        data["time"] = pd.to_datetime(data["time"], utc=True)
        source = "observed database records"
    else:
        data = reproducible_synthetic_history(category, now=current_time)
        source = "reproducible synthetic training data; not observed donations"

    data = data.dropna(subset=["time", "category", "quantity"]).sort_values("time")
    data = data[data["quantity"] >= 0]
    if data.empty:
        return {
            "source": "no usable history",
            "observed_records": observed_count,
            "metrics": None,
            "forecast": [],
            "limitation": "No historical records are available for this category.",
        }

    features = _features(data)
    target = data["quantity"].astype(float)
    split_at = max(1, int(len(data) * 0.8))
    if len(data) - split_at < 1:
        split_at = len(data) - 1
    train_features, test_features = features.iloc[:split_at], features.iloc[split_at:]
    train_target, test_target = target.iloc[:split_at], target.iloc[split_at:]

    if len(train_target) >= 20 and len(test_target) > 0:
        preprocessing = ColumnTransformer(
            [("category", OneHotEncoder(handle_unknown="ignore"), ["category"])],
            remainder="passthrough",
        )
        model = make_pipeline(
            preprocessing,
            RandomForestRegressor(n_estimators=160, min_samples_leaf=3, random_state=42, n_jobs=1),
        )
        model.fit(train_features, train_target)
        predicted = np.maximum(0, model.predict(test_features))
        baseline = np.full(len(test_target), train_target.mean())
        metrics = {
            "mae": round(float(mean_absolute_error(test_target, predicted)), 3),
            "mape_percent": round(float(mean_absolute_percentage_error(np.maximum(test_target, 0.1), predicted) * 100), 2),
            "historical_average_mae": round(float(mean_absolute_error(test_target, baseline)), 3),
            "holdout_rows": len(test_target),
            "split": "chronological 80/20",
        }
        model.fit(features, target)
        MODEL_DIR.mkdir(parents=True, exist_ok=True)
        model_path = MODEL_DIR / f"forecast-{category}.joblib"
        joblib.dump(model, model_path)
        limitation = None
    else:
        model = None
        model_path = None
        metrics = None
        limitation = "Too few rows for a credible ML holdout; forecast uses the historical average."

    forecast_rows = []
    for offset in range(1, forecast_days + 1):
        day = current_time + timedelta(days=offset)
        forecast_rows.append({"time": day.isoformat(), "category": category, "quantity": 0.0})
    forecast_features = _features(pd.DataFrame(forecast_rows))
    if model is None:
        values = np.full(forecast_days, float(target.mean()))
    else:
        values = np.maximum(0, model.predict(forecast_features))
    forecast = [
        {"time": row["time"], "quantity": round(float(value), 2)}
        for row, value in zip(forecast_rows, values, strict=True)
    ]
    return {
        "source": source,
        "observed_records": observed_count,
        "training_rows": len(data),
        "model": "random forest" if model is not None else "historical average",
        "metrics": metrics,
        "forecast": forecast,
        "model_path": str(model_path) if model_path else None,
        "limitation": limitation,
    }