# WhatsApp Business Platform / Cloud API Compliance & Integration

## 1. Compliance Principles & Meta Policies

WAMarketing strictly enforces compliance with Meta's WhatsApp Business Platform policies:

1. **Explicit Opt-In Required**: Campaigns cannot be dispatched to numbers without prior documented opt-in consent.
2. **Template Pre-Approval**: Outbound business-initiated conversations outside the 24-hour customer service window must use Meta-approved message templates.
3. **Mandatory Automated Opt-Out**: Automated keyword handling is enforced. Any message matching unsubscribe keywords (`STOP`, `UNSUBSCRIBE`, `REMOVE`, `QUIT`, `CANCEL`) automatically flags the contact as `opted_out` and halts all automated messaging.
4. **No Unauthorized Automation**: WAMarketing explicitly avoids browser automation, consumer app hijacking, accessibility service scraping, or rate limit evasion.

---

## 2. Setting Up WhatsApp Cloud API

### Step 1: Create Meta Developer App
1. Go to [developers.facebook.com](https://developers.facebook.com) and create a "Business" type app.
2. Add the **WhatsApp** product to your app.
3. Obtain your test phone number, **Phone Number ID**, and **WhatsApp Business Account ID (WABA ID)** from the WhatsApp Getting Started page.

### Step 2: Configure System User & Permanent Token
1. In Meta Business Manager, navigate to **System Users**.
2. Create an Admin system user and generate an access token with permissions:
   - `whatsapp_business_messaging`
   - `whatsapp_business_management`
3. Store this token in your backend `.env` as `DEFAULT_ACCESS_TOKEN`. **Never embed this token in the mobile app.**

### Step 3: Register Webhook
1. In Meta App Dashboard -> WhatsApp -> Configuration:
   - **Callback URL**: `https://your-domain.com/api/v1/webhooks/whatsapp`
   - **Verify Token**: Match `WHATSAPP_WEBHOOK_VERIFY_TOKEN` in your backend configuration.
2. Subscribe to the `messages` webhook field.

---

## 3. Message Template Guidelines

When creating templates in WAMarketing:
- Choose from standard Meta categories: `MARKETING`, `UTILITY`, or `AUTHENTICATION`.
- Insert variables using curly bracket notation: `{{1}}`, `{{2}}` or named format `{{first_name}}`, `{{company}}`.
- The Android UI explicitly distinguishes **WhatsApp Approved Templates** from **Local App Templates** to prevent compliance violations.
