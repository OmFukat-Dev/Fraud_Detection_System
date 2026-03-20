from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Optional

from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="Fraud ML Service", version="0.1.0")

MODEL_DIR = Path(__file__).resolve().parent.parent / "model"
MODEL_PATH = MODEL_DIR / "model.pkl"
META_PATH = MODEL_DIR / "model_meta.json"


class PredictRequest(BaseModel):
    amount: float = Field(..., ge=0)
    currency: Optional[str] = Field(default=None, max_length=3)
    hour_of_day: Optional[int] = Field(default=None, ge=0, le=23)
    velocity_count: Optional[int] = Field(default=None, ge=0)
    geo_anomaly: Optional[bool] = None
    is_new_device: Optional[bool] = None
    merchant_id: Optional[str] = None


class PredictResponse(BaseModel):
    fraudProbability: float
    modelVersion: str


class _HeuristicModel:
    version = "heuristic-v1"

    def predict_proba(self, features: PredictRequest) -> float:
        score = 0.10
        score += min(features.amount / 100000.0, 0.50)
        if features.velocity_count is not None and features.velocity_count > 5:
            score += 0.12
        if features.geo_anomaly:
            score += 0.15
        if features.is_new_device:
            score += 0.08
        if features.hour_of_day is not None and (features.hour_of_day <= 5 or features.hour_of_day >= 23):
            score += 0.05
        return max(0.0, min(score, 0.99))


def _load_model():
    if not MODEL_PATH.exists():
        return _HeuristicModel(), _HeuristicModel.version

    try:
        import joblib  # type: ignore
    except Exception:
        return _HeuristicModel(), _HeuristicModel.version

    try:
        model = joblib.load(MODEL_PATH)
        version = _HeuristicModel.version
        if META_PATH.exists():
            with META_PATH.open("r", encoding="utf-8") as f:
                meta = json.load(f)
            version = meta.get("modelVersion", version)
        return model, version
    except Exception:
        return _HeuristicModel(), _HeuristicModel.version


MODEL, MODEL_VERSION = _load_model()


@app.get("/health")
def health():
    return {"status": "ok", "modelVersion": MODEL_VERSION}


@app.post("/predict", response_model=PredictResponse)
def predict(payload: PredictRequest):
    try:
        if hasattr(MODEL, "predict_proba"):
            if isinstance(MODEL, _HeuristicModel):
                prob = MODEL.predict_proba(payload)
            else:
                vector = [
                    payload.amount,
                    float(payload.hour_of_day or 0),
                    float(payload.velocity_count or 0),
                    1.0 if payload.geo_anomaly else 0.0,
                    1.0 if payload.is_new_device else 0.0,
                ]
                proba = MODEL.predict_proba([vector])
                prob = float(proba[0][1]) if len(proba[0]) > 1 else float(proba[0][0])
        else:
            prob = _HeuristicModel().predict_proba(payload)
    except Exception:
        prob = _HeuristicModel().predict_proba(payload)

    prob = max(0.0, min(prob, 0.99))
    if math.isnan(prob):
        prob = 0.10

    return PredictResponse(fraudProbability=prob, modelVersion=MODEL_VERSION)

