from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.metrics import roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder
from sklearn.ensemble import RandomForestClassifier

DEFAULT_FEATURES = [
    "amount",
    "hour_of_day",
    "velocity_count",
    "geo_distance_km",
    "device_age_days",
    "merchant_category",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train fraud model and export artifacts.")
    parser.add_argument("--data", required=True, help="Path to CSV dataset")
    parser.add_argument("--label", default="is_fraud", help="Label column name (0/1)")
    parser.add_argument("--output-dir", default="model", help="Output directory for model artifacts")
    parser.add_argument("--model-version", default=None, help="Optional model version override")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    data_path = Path(args.data)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    df = pd.read_csv(data_path)
    if args.label not in df.columns:
        raise ValueError(f"Label column '{args.label}' not found in dataset")

    # Ensure required features exist
    missing = [col for col in DEFAULT_FEATURES if col not in df.columns]
    if missing:
        raise ValueError(f"Missing required feature columns: {missing}")

    X = df[DEFAULT_FEATURES]
    y = df[args.label].astype(int)

    numeric_features = [
        "amount",
        "hour_of_day",
        "velocity_count",
        "geo_distance_km",
        "device_age_days",
    ]
    categorical_features = ["merchant_category"]

    numeric_transformer = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="median")),
        ]
    )

    categorical_transformer = Pipeline(
        steps=[
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("onehot", OneHotEncoder(handle_unknown="ignore")),
        ]
    )

    preprocessor = ColumnTransformer(
        transformers=[
            ("num", numeric_transformer, numeric_features),
            ("cat", categorical_transformer, categorical_features),
        ]
    )

    model = RandomForestClassifier(
        n_estimators=200,
        max_depth=None,
        random_state=42,
        class_weight="balanced",
    )

    clf = Pipeline(steps=[("preprocess", preprocessor), ("model", model)])

    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    clf.fit(X_train, y_train)

    # Evaluate AUC if possible
    try:
        proba = clf.predict_proba(X_test)[:, 1]
        auc = roc_auc_score(y_test, proba)
    except Exception:
        auc = None

    model_path = output_dir / "model.pkl"
    joblib.dump(clf, model_path)

    model_version = args.model_version or f"rf-{datetime.now(timezone.utc).strftime('%Y%m%d%H%M%S')}"
    meta = {
        "modelVersion": model_version,
        "featureColumns": DEFAULT_FEATURES,
        "labelColumn": args.label,
        "metrics": {"roc_auc": auc} if auc is not None else {},
        "trainedAtUtc": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }

    with (output_dir / "model_meta.json").open("w", encoding="utf-8") as f:
        json.dump(meta, f, indent=2)

    print(f"Saved model to {model_path}")


if __name__ == "__main__":
    main()


