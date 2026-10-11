# WAMarketing — Security & Privacy Architecture

## 1. Zero Mobile Token Storage Architecture

In traditional unvetted applications, WhatsApp Business API tokens or consumer phone secrets are mistakenly bundled inside mobile apps or APK files, creating massive security and credential leaks.

In WAMarketing:
- **Zero Credentials on Device**: The Android client contains **no** Meta API tokens, app secrets, or direct WhatsApp credentials.
- **Backend Token Broker**: All Meta Graph API calls are routed through the Python FastAPI backend using server-side encrypted environment variables.
- **Scoped User Authentication**: Android users authenticate via standard JWT tokens signed using HMAC SHA-256 with role-based access control (RBAC).

---

## 2. Webhook Integrity & Signature Verification

WhatsApp Cloud API delivers events via HTTPS POST webhooks. To prevent spoofing and man-in-the-middle attacks:
- Webhook payloads include an `X-Hub-Signature-256` header calculated as `sha256=HMAC_SHA256(payload, APP_SECRET)`.
- The FastAPI webhook receiver validates this signature before processing message status receipts or inbound chats.

---

## 3. Data Protection & Privacy Compliance

- **Suppression List**: When a contact replies with an unsubscribe keyword (`STOP`, `UNSUBSCRIBE`, `REMOVE`), they are added to a global suppression table. The campaign dispatch service executes an automatic check before dispatching every single recipient.
- **E.164 Number Sanitization**: All contact numbers are validated and sanitized according to international E.164 specifications to eliminate accidental misrouting.
- **SQL Injection Prevention**: Built entirely on SQLAlchemy 2.0 ORM with parametrized queries.
- **Password Hashing**: User authentication uses `bcrypt` cryptographic hashing with salt rounds.
