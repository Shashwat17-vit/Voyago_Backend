# Voyago_Backend
Java Spring Backend for Voyago

---

## Database: PostgreSQL

---

## Schema

### User
| Column | Type | Notes |
|--------|------|-------|
| uid | UUID / BIGINT | PK |
| name | VARCHAR | |
| email | VARCHAR | UNIQUE |
| phone | VARCHAR | |
| bio | TEXT | |
| language | VARCHAR | |
| currency | VARCHAR | |
| date_format | VARCHAR | |
| timezone | VARCHAR | |
| created_at | TIMESTAMP | |

---

### Trip
| Column | Type | Notes |
|--------|------|-------|
| tid | UUID / BIGINT | PK |
| title | VARCHAR | |
| destination | VARCHAR | |
| start_date | DATE | |
| end_date | DATE | |
| num_travelers | INT | |
| image_url | VARCHAR | |
| status | ENUM | PLANNING / ACTIVE / COMPLETED |
| created_at | TIMESTAMP | |
| uid | FK → User | Trip admin/owner |

---

### TripPreferences
| Column | Type | Notes |
|--------|------|-------|
| pref_id | BIGINT | PK |
| tid | FK → Trip | 1:1 with Trip |
| current_location | VARCHAR | |
| budget | VARCHAR | |
| trip_type | VARCHAR | Adventure / Relaxation / Cultural etc. |
| accommodation | VARCHAR | Hotel / Airbnb / Hostel etc. |
| transportation | VARCHAR | Flight / Train / Car etc. |
| interests | TEXT | comma-separated |
| notes | TEXT | additional notes |

---

### TripMember
| Column | Type | Notes |
|--------|------|-------|
| id | BIGINT | PK |
| tid | FK → Trip | |
| uid | FK → User | |
| role | ENUM | ADMIN / MEMBER |
| status | ENUM | INVITED / ACCEPTED / DECLINED |
| joined_at | TIMESTAMP | |

---

### TripItineraryDay
| Column | Type | Notes |
|--------|------|-------|
| day_id | BIGINT | PK |
| tid | FK → Trip | |
| day_number | INT | 1, 2, 3... |
| day_label | VARCHAR | "Day 1", "Day 2-3" |
| date | DATE | actual date if known |

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
| category | ENUM | FOOD / ACTIVITY / TRANSPORT / ACCOMMODATION / SIGHTSEEING |
| start_time | TIME | |
| end_time | TIME | |
| order_index | INT | sorting within a day |

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
