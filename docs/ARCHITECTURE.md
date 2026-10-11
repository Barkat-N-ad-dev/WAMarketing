# WAMarketing — Enterprise System Architecture

## 1. High-Level System Overview

WAMarketing is an enterprise-grade multi-tenant SaaS application built for compliant WhatsApp marketing, CRM pipeline management, multi-step campaign automations, and intelligent AI assistance.

```
                      +-----------------------------+
                      |      Android Client         |
                      |  (Kotlin / Jetpack Compose) |
                      +--------------+--------------+
                                     |
                                     | HTTPS / REST & WebSockets
                                     v
                      +-----------------------------+
                      |       FastAPI Backend       |
                      |  (Python 3.11 / SQLAlchemy) |
                      +--------------+--------------+
                                     |
           +-------------------------+-------------------------+
           |                         |                         |
           v                         v                         v
+--------------------+    +--------------------+    +--------------------+
|  PostgreSQL 15 DB  |    |  Campaign/Worker   |    | Meta Graph API v19 |
| (Audit, CRM, Data) |    |  Dispatch Engine   |    | WhatsApp Cloud API |
+--------------------+    +--------------------+    +--------------------+
                                                               |
                                                               v
                                                    +--------------------+
                                                    |  Target Customer   |
                                                    |  WhatsApp Handset  |
                                                    +--------------------+
```

---

## 2. Component Architecture

### A. Android Client Application (`/android`)
- **UI Framework**: Modern Jetpack Compose with Material 3 design system and dynamic dark/light theming.
- **Architecture Pattern**: MVVM / Clean Architecture with unidirectional data flow (UDF).
- **State Management**: Reactive `StateFlow` streams exposed by repository layers.
- **Local Persistence & Networking**: Room database, DataStore preferences, Retrofit/OkHttp, and WorkManager for local notifications.
- **Security**: Zero WhatsApp credentials, tokens, or business secrets are stored on the handset. All communication goes through authenticated JWT endpoints against the backend.

### B. Python FastAPI Backend (`/backend`)
- **API Framework**: FastAPI with asynchronous endpoints and strict Pydantic v2 schemas.
- **ORM & Migrations**: SQLAlchemy 2.0 with Alembic database migrations.
- **WhatsApp Cloud API Integration**: Direct client with Meta Graph API v19 (`/v19.0/{phone_number_id}/messages`).
- **Webhook Handler**: Real-time webhook ingestion verifying SHA-256 HMAC signatures, processing delivery statuses (`sent`, `delivered`, `read`, `failed`), and capturing inbound messages.
- **Automation Pipeline**: Server-side sequential workflow executor processing triggers, tags, delayed tasks, and conditional logic.

---

## 3. Data Flow

### Outbound Campaign Dispatch Flow
1. Operator configures campaign in the Android Campaign Builder (Audience segment, Approved Template, Media attachment, Schedule).
2. Android client dispatches `POST /api/v1/campaigns/{id}/launch` with authorization bearer token.
3. Backend campaign engine verifies audience opt-in suppression list:
   - Contacts with `opted_out` status are strictly excluded.
   - E.164 phone numbers are normalized.
4. Messages are queued and paced at safe rates (e.g., 20 msg/sec) against Meta WhatsApp Business Cloud API.
5. Meta dispatches delivery receipts to the backend webhook (`/api/v1/webhooks/whatsapp`).
6. Backend updates message recipient statuses in PostgreSQL; Android client displays updated KPIs in real-time.

### Inbound Message & Opt-Out Flow
1. Recipient sends message (e.g., "STOP" or "Please send brochure") on WhatsApp.
2. WhatsApp Cloud API delivers webhook payload to FastAPI backend.
3. Webhook handler:
   - Checks for opt-out keywords (`STOP`, `UNSUBSCRIBE`, `REMOVE`).
   - If keyword matches: Immediately updates contact to `opted_out`, logs audit entry, and adds contact to global suppression list.
   - If customer inquiry: Creates inbound message in `conversations` table, increments unread counter, and triggers any active automation workflows.
