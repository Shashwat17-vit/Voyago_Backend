# Voyago Backend
Java Spring Boot backend for Voyago — an AI-powered trip planning web app.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 4.0.5 (MVC) |
| Auth | Spring Security + OAuth2 (Google, GitHub) |
| ORM | Hibernate / JPA |
| Database | PostgreSQL (AWS RDS) |
| Password Hashing | BCrypt |
| Build | Maven |

---

## Database: PostgreSQL

---

## Schema

### User
| Column | Type | Notes |
|--------|------|-------|
| uid | BIGINT | PK, auto-generated |
| full_name | VARCHAR | |
| email | VARCHAR | UNIQUE |
| password | VARCHAR | BCrypt hashed — null for OAuth users |
| created_at | TIMESTAMP | |

---

### Trip
| Column | Type | Notes |
|--------|------|-------|
| tid | BIGINT | PK, auto-generated |
| title | VARCHAR | |
| destination | VARCHAR | |
| start_date | DATE | |
| end_date | DATE | |
| num_travelers | INT | |
| image_url | VARCHAR | |
| status | VARCHAR | PLANNING / ACTIVE / COMPLETED |
| created_at | TIMESTAMP | |
| uid | FK → User | Trip owner |

---

### TripPreferences
| Column | Type | Notes |
|--------|------|-------|
| pref_id | BIGINT | PK |
| tid | FK → Trip | 1:1 with Trip |
| current_location | VARCHAR | departure city |
| budget | VARCHAR | e.g. "Under $1000" |
| trip_type | VARCHAR | Adventure / Relaxation / Cultural etc. |
| accommodation | VARCHAR | Hotel / Airbnb / Hostel etc. |
| transportation | VARCHAR | Flight / Train / Car etc. |
| interests | TEXT | comma-separated tags |
| notes | TEXT | additional free-text notes |

---

### TripMember
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT | PK |
| tid | FK → Trip | |
| uid | FK → User | |
| role | VARCHAR | ADMIN / MEMBER |
| status | VARCHAR | INVITED / ACCEPTED / DECLINED |
| joined_at | TIMESTAMP | |

---

### TripItineraryDay
| Column | Type | Notes |
|--------|------|-------|
| day_id | BIGINT | PK |
| tid | FK → Trip | |
| day_number | INT | 1, 2, 3... |
| day_label | VARCHAR | "Day 1 — Paris" |
| date | DATE | actual calendar date |

---

### TripEvent
| Column | Type | Notes |
|--------|------|-------|
| event_id | BIGINT | PK |
| day_id | FK → TripItineraryDay | |
| title | VARCHAR | |
| description | TEXT | |
| location_name | VARCHAR | |
| latitude | DOUBLE | for map pinning |
| longitude | DOUBLE | for map pinning |
| category | VARCHAR | FOOD / ACTIVITY / TRANSPORT / ACCOMMODATION / SIGHTSEEING |
| start_time | TIME | |
| end_time | TIME | |
| order_index | INT | sort order within a day |

---

## Relationships

```
User ──< TripMember >── Trip
                         │
                    TripPreferences (1:1)
                         │
                    TripItineraryDay (1:many)
                         │
                    TripEvent (1:many)
```

---

## Implementation Roadmap

### Step 1 — Store OAuth2 Login (Google / GitHub)
- In `OAuth2SuccessHandler.java`, inject `UserRepository`
- On successful OAuth login, check if user already exists by email
- If not, create a new `User` record with name + email (password = null)
- If yes, skip insert (idempotent upsert)
- Redirect to `http://localhost:5173/#/home`

**Files:** `OAuth2SuccessHandler.java`, `UserRepository.java`, `User.java`

---

### Step 2 — Email / Password Login Endpoint
- Add `POST /api/auth/login` in `AuthController`
- Accept `{ email, password }` in a `LoginRequest` DTO
- In `AuthService`, find user by email → verify BCrypt hash
- Return success or 401 Unauthorized

**Files:** `AuthController.java`, `AuthService.java`, `LoginRequest.java` (new DTO)

---

### Step 3 — JWT Token (Session Management)
- Add `io.jsonwebtoken:jjwt` dependency to `pom.xml`
- Create a `JwtUtil` class: generate token (email as subject, 24h expiry), validate token
- On successful signup or login, return `{ token: "..." }` in the response
- Create a `JwtFilter` (extends `OncePerRequestFilter`) to parse token on every request
- Register filter in `SecurityConfig` before `UsernamePasswordAuthenticationFilter`
- Frontend stores token in `localStorage`, sends as `Authorization: Bearer <token>` header

**Files:** `JwtUtil.java` (new), `JwtFilter.java` (new), `SecurityConfig.java`, `AuthController.java`

---

### Step 4 — Trips Database Tables + JPA Entities
- Create PostgreSQL tables: `trip`, `trip_preferences`, `trip_member`, `trip_itinerary_day`, `trip_event`
- Create JPA entity classes for each table
- Create repositories: `TripRepository`, `TripPreferencesRepository`
- Create `TripController` with endpoints:
  - `POST /api/trips` — create trip
  - `GET /api/trips` — list trips for logged-in user
  - `GET /api/trips/{id}` — single trip with itinerary

**Files:** `Trip.java`, `TripRepository.java`, `TripController.java`, `TripService.java`

---

### Step 5 — Wire Frontend to Trip API
- Add `api_createTrip.js` and `api_getTrips.js` in `src/api/`
- On the New Trip form submit → call `POST /api/trips`
- On Home/Dashboard load → call `GET /api/trips` and render real data
- Pass JWT token in Authorization header on every request

**Files:** `src/api/api_createTrip.js`, `src/api/api_getTrips.js`, frontend trip pages

---

### Step 6 — TripPreferences Form + Storage
- Build a multi-step preferences form after trip creation:
  - Fields: departure city, budget, trip type, accommodation, transportation, interests, notes
- On submit → call `POST /api/trips/{id}/preferences`
- Store in `trip_preferences` table (1:1 with Trip)
- This data becomes the input to the LLM agent

**Files:** `TripPreferences.java`, `TripPreferencesController.java`, frontend form component

---

### Phase 3 — LangGraph Agent (Python Microservice)

#### Step 7 — Standalone Python Agent (test in isolation)
- Create a separate Python project / folder
- Set up LangGraph `StateGraph` with nodes:
  - Node 1: Parse TripPreferences → decide which APIs to call
  - Node 2: Amadeus → search flights (origin → destination, travel dates)
  - Node 3: Amadeus or Google Places → search hotels
  - Node 4: Foursquare or Google Places → search POIs / activities
  - Node 5: Feed all results into LLM prompt → return structured JSON itinerary
- Use Pydantic models or LLM tool-calling to enforce JSON output shape
- Test end-to-end with hardcoded preferences before wiring to Spring

**Tools:** LangGraph, LangChain, Amadeus SDK, OpenAI / Claude API

---

#### Step 8 — Spring ↔ Python Handoff
- Expose the Python agent as a FastAPI endpoint: `POST /generate-itinerary`
- In Spring, add `POST /api/trips/{id}/generate` endpoint
- Spring reads `TripPreferences` from DB, sends to Python via HTTP
- Python agent runs, returns itinerary JSON to Spring
- Spring parses response and saves to `trip_itinerary_day` + `trip_event` tables

**Files:** `ItineraryController.java`, `ItineraryService.java`, `Python: main.py / agent.py`

---

### Phase 4 — Store + Display Itinerary

#### Step 9 — Save Itinerary to DB
- Map LLM JSON response → `TripItineraryDay` + `TripEvent` JPA entities
- Persist via `TripItineraryDayRepository` and `TripEventRepository`
- Add `GET /api/trips/{id}/itinerary` endpoint to fetch full day-by-day itinerary

**Files:** `TripItineraryDay.java`, `TripEvent.java`, `ItineraryController.java`

---

#### Step 10 — Display Itinerary on Frontend
- On `TripDetails` page, call `GET /api/trips/{id}/itinerary`
- Render each `TripItineraryDay` as a section with its `TripEvent` list
- Show event title, time, location, category icon
- Replace all hardcoded itinerary data with real API responses

**Files:** `src/pages/TripDetails.jsx`, `src/api/api_getItinerary.js` (new)

---

## API Endpoints Summary

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/signup` | Register new user |
| POST | `/api/auth/login` | Login with email + password |
| POST | `/api/trips` | Create a new trip |
| GET | `/api/trips` | Get all trips for logged-in user |
| GET | `/api/trips/{id}` | Get single trip |
| POST | `/api/trips/{id}/preferences` | Save trip preferences |
| POST | `/api/trips/{id}/generate` | Trigger LLM itinerary generation |
| GET | `/api/trips/{id}/itinerary` | Fetch generated itinerary |
