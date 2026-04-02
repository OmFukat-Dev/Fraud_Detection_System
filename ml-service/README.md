# ML Service

## Training

Provide a CSV with these columns:
- amount
- hour_of_day
- velocity_count
- geo_distance_km
- device_age_days
- merchant_category
- is_fraud (label, 0/1)

Example:

```bash
python train.py --data path/to/dataset.csv --label is_fraud --output-dir model
```

The training script writes `model/model.pkl` and `model/model_meta.json`.
The metadata file stores the model version, feature columns, metrics, and UTC training time.

## Serving

Run the service locally:

```bash
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

The service exposes:
- `GET /health` for liveness and the active `modelVersion`
- `POST /predict` returning `{ fraudProbability, modelVersion }`

## Docker

Build and run the container:

```bash
docker build -t fraud-ml-service .
docker run --rm -p 8000:8000 fraud-ml-service
```
