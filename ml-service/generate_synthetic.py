from __future__ import annotations

import argparse
import random
from pathlib import Path

import numpy as np
import pandas as pd


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate synthetic fraud dataset.")
    parser.add_argument("--rows", type=int, default=5000)
    parser.add_argument("--out", default="synthetic_fraud.csv")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    rng = random.Random(42)
    np.random.seed(42)

    categories = ["grocery", "electronics", "travel", "gaming", "fashion", "fuel", "pharmacy"]

    rows = []
    for _ in range(args.rows):
        amount = round(max(1.0, np.random.lognormal(mean=8.0, sigma=0.6)), 2)
        hour_of_day = rng.randint(0, 23)
        velocity_count = int(np.random.poisson(lam=2))
        geo_distance_km = abs(np.random.normal(loc=50, scale=120))
        device_age_days = abs(np.random.normal(loc=20, scale=15))
        merchant_category = rng.choice(categories)

        # Synthetic label heuristic
        risk = 0.0
        risk += min(amount / 100000.0, 0.6)
        if velocity_count > 5:
            risk += 0.12
        if geo_distance_km > 500:
            risk += 0.12
        if device_age_days < 3:
            risk += 0.08
        if hour_of_day <= 5 or hour_of_day >= 23:
            risk += 0.05
        if merchant_category in {"gaming", "electronics"}:
            risk += 0.05

        prob = min(0.99, risk)
        is_fraud = 1 if rng.random() < prob else 0

        rows.append(
            {
                "amount": amount,
                "hour_of_day": hour_of_day,
                "velocity_count": velocity_count,
                "geo_distance_km": round(geo_distance_km, 2),
                "device_age_days": round(device_age_days, 2),
                "merchant_category": merchant_category,
                "is_fraud": is_fraud,
            }
        )

    df = pd.DataFrame(rows)
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(out_path, index=False)
    print(f"Wrote {len(df)} rows to {out_path}")


if __name__ == "__main__":
    main()
