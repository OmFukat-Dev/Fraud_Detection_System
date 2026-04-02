# 🛡️ Fraud Detection System — Implementation Plan

---

## ✅ PHASE 1 — Foundation & Infrastructure (COMPLETED)

### Goal
Set up the complete backend skeleton, security layer, data model, Kafka pipeline entry point, and Docker-based infrastructure.

### Work Done

#### 1. 🗄️ Data Model — `Transaction.java`
Core JPA entity saved to MySQL with all fraud-relevant fields:

| Field | Type | Description |
|---|---|---|
| `transactionId` | String (UUID) | Unique transaction identifier |
| `userId` | String | Who made the transaction |
| `merchantId` | String | Target merchant |
| `amount` | BigDecimal (15,2) | Transaction amount |
| `currency` | String (3-char) | Currency code |
| `deviceId` / `ipAddress` | String | Device fingerprinting |
| `latitude` / `longitude` | Double | Geolocation of transaction |
| `status` | Enum | `PENDING`, `COMPLETED`, `BLOCKED` |
| `fraudVerdict` | Enum | `ALLOW`, `REVIEW`, `FRAUD` |
| `fraudScore` | Double | Fraud probability (0.0–1.0) |
| `triggeredRules` | TEXT | Which fraud rules fired |
| `createdAt` / `updatedAt` | LocalDateTime | Auto-managed timestamps |

#### 2. 🔐 Security — JWT Authentication
- **`JwtUtil.java`** — Token generation (HS256, 24h expiry), validation, username extraction
- **`JwtFilter.java`** — Intercepts requests, validates `Authorization: Bearer <token>` header
- **`SecurityConfig.java`** — Public routes for `/auth/**` + actuator health; JWT required for all else; stateless sessions; in-memory users (`admin`, `analyst`) with BCrypt hashing

#### 3. 🌐 REST API — `TransactionController.java`
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/transactions` | Submit transaction for fraud analysis |
| `GET` | `/api/v1/transactions/user/{userId}` | Get all transactions by user |
| `GET` | `/api/v1/transactions/flagged` | Get all FRAUD-verdicted transactions |

#### 4. ⚙️ Business Logic — `TransactionService.java`
- UUID generation per transaction
- Saves to MySQL with status `PENDING`
- Publishes to Kafka topic `fraud.transactions.raw` asynchronously (non-blocking)

#### 5. 📨 Kafka Integration — Producer
- `acks=all` — zero data loss guarantee
- `retries=3` — fault tolerance
- Idempotence enabled — no duplicate messages
- Topic: `fraud.transactions.raw` (4 partitions)

#### 6. 🐳 Docker Infrastructure
| Service | Image | Port |
|---|---|---|
| MySQL 8 | `mysql:8` | 3306 |
| Redis 7 | `redis:7` | 6379 |
| Zookeeper | `confluentinc/cp-zookeeper:7.5.0` | 2181 |
| Kafka | `confluentinc/cp-kafka:7.5.0` | 9092 |
| Prometheus | `prom/prometheus:latest` | 9090 |
| Grafana | `grafana/grafana:latest` | 3001 |

#### 7. 📊 Observability
- Actuator endpoints: `health`, `info`, `metrics`, `prometheus`
- Prometheus scraping Spring Boot app
- Grafana dashboards on port 3001

---

## ✅ PHASE 2 — Kafka Consumer & Rule-Based Fraud Engine (COMPLETED)

### Goal
Build the async fraud analysis brain — consume transactions from Kafka, apply rule-based fraud scoring, update the database with results, and push alerts.

### Tasks

#### 2.1 Kafka Consumer
- [x] Create `KafkaConsumerService.java` — listens on `fraud.transactions.raw`
- [x] Deserialize incoming `TransactionRequest` JSON
- [x] Pass each transaction through the fraud rule engine

#### 2.2 Rule-Based Fraud Engine
- [x] Create `FraudRuleEngine.java` service
- [x] Implement core fraud detection rules:
  - **High-amount rule** — flag transactions above a configurable threshold (e.g., ₹50,000)
  - **Velocity rule** — flag if same user makes >N transactions in X minutes (Redis-backed counter)
  - **Geo-anomaly rule** — flag impossible travel (two transactions from far locations in short time)
  - **Blacklist rule** — flag transactions from blacklisted merchant IDs or IP addresses
  - **New device rule** — flag if `deviceId` has never been seen for this user
- [x] Each rule returns a `RuleResult` with: rule name, triggered (true/false), score contribution
- [x] Aggregate scores → final `fraudScore` (0.0–1.0)
- [x] Determine `fraudVerdict`: 
  - `0.0–0.4` → `ALLOW`
  - `0.4–0.7` → `REVIEW`
  - `0.7–1.0` → `FRAUD`

#### 2.3 Transaction Update
- [x] Update `Transaction` record in MySQL with: `fraudScore`, `fraudVerdict`, `triggeredRules`, `status`
- [x] Create `FraudAnalysisResult` DTO for internal processing

#### 2.4 Redis Velocity Tracking
- [x] Use Redis to store per-user transaction counts with TTL (sliding window)
- [x] Create `VelocityCheckService.java`

#### 2.5 Alert Service (Basic)
- [x] Create `AlertService.java` — logs high-severity fraud events
- [x] Publish FRAUD verdicts to a new Kafka topic: `fraud.alerts` for future notification services

#### 2.6 New API Endpoints
- [x] `GET /api/v1/transactions/{transactionId}` — Get single transaction with fraud details
- [x] `GET /api/v1/fraud/stats` — Aggregate stats (total flagged, average score, etc.)

---

## ✅ PHASE 3 — ML-Based Fraud Scoring (AI Layer) (COMPLETED)

### Goal
Replace/augment rule-based scoring with a trained machine learning model that gives more accurate, adaptive fraud probability scores.

### Tasks

#### 3.1 ML Model (Python Microservice)
- [x] Create `ml-service/` directory — standalone Python FastAPI service
- [x] Train a fraud scoring model (start with Random Forest / XGBoost on public dataset like IEEE-CIS)
- [x] Features: amount, time-of-day, velocity count, geo-distance, device age, merchant category
- [x] Expose `POST /predict` endpoint returning `{ fraudProbability: 0.85 }`
- [x] Containerize with Docker

#### 3.2 Integration with Java Backend
- [x] Create `MlScoringService.java` — HTTP client calling the Python ML service
- [x] Merge ML score with rule-based score (weighted ensemble)
- [x] Fallback to rule-only scoring if ML service is down (circuit breaker pattern)

#### 3.3 Model Versioning
- [x] Store model version in prediction response
- [x] Log which model version scored each transaction

---

## 🖥️ PHASE 4 — Frontend Dashboard (React)

### Goal
Build a real-time analyst dashboard to visualize transactions, fraud verdicts, and system health.

### Tasks

#### 4.1 Project Setup
- [ ] Initialize React + Vite project in `frontend/`
- [ ] Set up Tailwind CSS + shadcn/ui component library
- [ ] Configure Axios for API calls with JWT interceptors

#### 4.2 Pages & Components
- [ ] **Login Page** — JWT auth form
- [ ] **Dashboard Home** — Summary cards: total transactions, flagged today, avg fraud score
- [ ] **Transactions Table** — Paginated, sortable, filterable list with fraud verdict badges
- [ ] **Transaction Detail Page** — Full details + which rules triggered + fraud score gauge
- [ ] **Flagged Transactions Page** — Real-time feed of FRAUD verdicts
- [ ] **Analytics Page** — Charts: fraud rate over time, top flagged merchants, score distribution

#### 4.3 Real-time Updates
- [ ] WebSocket or Server-Sent Events (SSE) from Spring Boot for live fraud alerts
- [ ] Toast notifications for new FRAUD verdicts

---

## 📈 PHASE 5 — Advanced Observability & Production Hardening

### Goal
Make the system production-ready with full observability, alerting, rate limiting, and deployment pipeline.

### Tasks

#### 5.1 Grafana Dashboards
- [ ] Import pre-built Kafka dashboard (JMX metrics)
- [ ] Create custom fraud detection dashboard:
  - Transactions per second
  - Fraud verdict distribution (pie chart)
  - Kafka consumer lag
  - Redis cache hit rate
  - Rule engine trigger frequency

#### 5.2 Alerting
- [ ] Configure Prometheus alerting rules (`alert.rules.yml`)
- [ ] Alert conditions: consumer lag > 1000, fraud rate > 20%, service down
- [ ] Integrate Alertmanager → email / Slack notifications

#### 5.3 Rate Limiting & Security Hardening
- [ ] API rate limiting using Redis (`Bucket4j` library)
- [ ] IP-based brute force protection on `/auth/login`
- [ ] HTTPS / TLS termination via NGINX reverse proxy
- [ ] Secrets management (move credentials out of `application.properties` → env vars / Vault)

#### 5.4 Database Optimizations
- [ ] Add indexes on `userId`, `merchantId`, `fraudVerdict`, `createdAt`
- [ ] Add database migrations with Flyway
- [ ] Archive old transactions (partitioning or cold storage strategy)

#### 5.5 CI/CD Pipeline
- [ ] GitHub Actions workflow:
  - Run unit + integration tests
  - Build Docker image
  - Push to Docker Hub / GitHub Container Registry
  - Deploy to staging environment

#### 5.6 API Documentation
- [ ] Integrate Springdoc OpenAPI (Swagger UI)
- [ ] Document all endpoints with request/response examples
- [ ] Export Postman collection

---

## 🗺️ Phase Summary

| Phase | Focus | Status |
|---|---|---|
| **Phase 1** | Foundation, Security, Kafka Producer, Infrastructure | ✅ DONE |
| **Phase 2** | Kafka Consumer + Rule-Based Fraud Engine + Redis Velocity | ✅ DONE |
| **Phase 3** | ML Model (Python) + AI Scoring Integration | ✅ DONE |
| **Phase 4** | React Frontend Dashboard + Real-time Alerts | ⏳ Planned |
| **Phase 5** | Grafana Dashboards, CI/CD, Production Hardening | ⏳ Planned |


Commands to run this project are :

  1> Terminal 1:
      cd /home/om_fukat/projects/fraud-detection-system/docker
      docker compose up -d

      This command will start the functioning of docker
    
  2> Terminal 2:
      cd /home/om_fukat/projects/fraud-detection-system/backend/fraud-detection-engine
      ./mvnw -DskipTests clean spring-boot:run

      This command will start the functioning of backend

  3> Terminal 3:
      cd /home/om_fukat/projects/fraud-detection-system/frontend
      npm install
      npm run dev

      This command will  start the functioning of frontend

  


