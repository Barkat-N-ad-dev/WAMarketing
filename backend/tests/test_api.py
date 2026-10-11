import pytest
from fastapi.testclient import TestClient
from app.main import app, seed_initial_data
from app.database import Base, engine, SessionLocal
from app.models.entities import Contact, Campaign, OptStatus, WhatsAppAccount
from app.config import settings

client = TestClient(app)

@pytest.fixture(autouse=True)
def setup_db():
    Base.metadata.create_all(bind=engine)
    seed_initial_data()
    yield

def test_health_check():
    response = client.get("/")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "online"

import uuid

def test_auth_workflow():
    unique_email = f"owner_{uuid.uuid4().hex[:8]}@globaltech.io"
    reg_payload = {
        "business_name": "Test Global Tech",
        "email": unique_email,
        "password": "Password123!",
        "full_name": "John Doe"
    }
    res = client.post("/api/v1/auth/register", json=reg_payload)
    assert res.status_code == 200
    token_data = res.json()
    assert "access_token" in token_data
    token = token_data["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # 2. Test /me
    me_res = client.get("/api/v1/auth/me", headers=headers)
    assert me_res.status_code == 200
    assert me_res.json()["email"] == unique_email

    # 3. Test Login
    login_res = client.post("/api/v1/auth/login", json={
        "email": unique_email,
        "password": "Password123!"
    })
    assert login_res.status_code == 200
    assert "access_token" in login_res.json()

def test_contacts_and_import():
    # Login as seeded admin
    login_res = client.post("/api/v1/auth/login", json={
        "email": "admin@wamarketing.app",
        "password": "admin123"
    })
    assert login_res.status_code == 200
    headers = {"Authorization": f"Bearer {login_res.json()['access_token']}"}

    rand_phone = f"+97150{uuid.uuid4().int % 9000000 + 1000000}"
    # Create contact
    contact_payload = {
        "first_name": "Fatima",
        "last_name": "Zahra",
        "phone_number": rand_phone,
        "email": f"fatima_{uuid.uuid4().hex[:6]}@example.ae",
        "company": "Emirates Invest",
        "city": "Dubai",
        "country": "UAE",
        "lead_status": "Hot Lead"
    }
    c_res = client.post("/api/v1/contacts", json=contact_payload, headers=headers)
    assert c_res.status_code in [200, 201]
    c_data = c_res.json()
    assert c_data["first_name"] == "Fatima"
    assert c_data["phone_number"] == rand_phone
    contact_id = c_data["id"]

    # Test CSV Import
    p1 = f"+92300{uuid.uuid4().int % 9000000 + 1000000}"
    p2 = f"+96650{uuid.uuid4().int % 9000000 + 1000000}"
    csv_data = (
        "name,phone,email,city,company\n"
        f"Tariq Mahmood,{p1},tariq@example.pk,Lahore,Pak Trade\n"
        f"Zainab Ali,{p2},zainab@example.sa,Riyadh,Saudi Corp\n"
        f"Fatima Zahra,{rand_phone},fatima@duplicate.ae,Dubai,Emirates Invest\n"
        "Bad Number,not-a-number,bad@bad.com,Karachi,Invalid Inc\n"
    )
    files = {"file": ("test_contacts.csv", csv_data.encode("utf-8"), "text/csv")}
    import_res = client.post("/api/v1/contacts/import", files=files, headers=headers)
    assert import_res.status_code == 200
    res_json = import_res.json()
    assert res_json["imported"] >= 2
    assert res_json["duplicates"] >= 1
    assert res_json["invalid"] >= 1

def test_campaign_personalization_and_preview():
    # Login
    login_res = client.post("/api/v1/auth/login", json={
        "email": "admin@wamarketing.app",
        "password": "admin123"
    })
    headers = {"Authorization": f"Bearer {login_res.json()['access_token']}"}

    preview_req = {
        "message_template": "Hello {{first_name}}, we have a special promotion for {{company}} in {{city}}!",
        "sample_contact": {
            "first_name": "Hamza",
            "company": "Apex Dynamics",
            "city": "Abu Dhabi"
        }
    }
    p_res = client.post("/api/v1/campaigns/preview-personalization", json=preview_req, headers=headers)
    assert p_res.status_code == 200
    data = p_res.json()
    assert data["personalized"] == "Hello Hamza, we have a special promotion for Apex Dynamics in Abu Dhabi!"
    assert "first_name" in data["detected_variables"]

def test_webhook_and_opt_out():
    # 1. Test Verification handshake
    token = settings.WHATSAPP_WEBHOOK_VERIFY_TOKEN
    verify_res = client.get(f"/api/v1/webhooks/whatsapp?hub.mode=subscribe&hub.verify_token={token}&hub.challenge=1122334455")
    assert verify_res.status_code == 200
    assert verify_res.text == "1122334455"

    # 2. Ingest Inbound Webhook with STOP keyword (Opt-Out trigger)
    webhook_event = {
        "object": "whatsapp_business_account",
        "entry": [
            {
                "id": "109876543210987",
                "changes": [
                    {
                        "value": {
                            "messaging_product": "whatsapp",
                            "metadata": {
                                "display_phone_number": "15551234567",
                                "phone_number_id": "102938475610293"
                            },
                            "contacts": [{"profile": {"name": "Rashid Khan"}, "wa_id": "971509998877"}],
                            "messages": [
                                {
                                    "from": "971509998877",
                                    "id": "wamid.ABGG11223344",
                                    "timestamp": "1720000000",
                                    "text": {"body": "STOP"},
                                    "type": "text"
                                }
                            ]
                        },
                        "field": "messages"
                    }
                ]
            }
        ]
    }
    hook_res = client.post("/api/v1/webhooks/whatsapp", json=webhook_event)
    assert hook_res.status_code == 200
    hook_data = hook_res.json()
    assert hook_data["processed"]["opt_outs"] == 1

def test_ai_assistant_endpoints():
    login_res = client.post("/api/v1/auth/login", json={
        "email": "admin@wamarketing.app",
        "password": "admin123"
    })
    headers = {"Authorization": f"Bearer {login_res.json()['access_token']}"}

    # Campaign copywriter
    ai_res = client.post("/api/v1/ai/campaign-writer", json={
        "prompt": "Luxury penthouse launch in Downtown Dubai",
        "product_or_service": "Luxury Penthouse",
        "target_audience": "Pakistani HNIs"
    }, headers=headers)
    assert ai_res.status_code == 200
    ai_data = ai_res.json()
    assert "main_message" in ai_data
    assert "short_version" in ai_data
    assert "professional_version" in ai_data

    # Translation
    trans_res = client.post("/api/v1/ai/translate", json={
        "text": "Hello, we have an offer for you.",
        "target_language": "Roman Urdu"
    }, headers=headers)
    assert trans_res.status_code == 200
    assert "Salam" in trans_res.json()["translated_text"]

def test_dashboard_analytics():
    login_res = client.post("/api/v1/auth/login", json={
        "email": "admin@wamarketing.app",
        "password": "admin123"
    })
    headers = {"Authorization": f"Bearer {login_res.json()['access_token']}"}

    dash_res = client.get("/api/v1/analytics/dashboard", headers=headers)
    assert dash_res.status_code == 200
    data = dash_res.json()
    assert "total_contacts" in data
    assert "delivery_rate_percent" in data
    assert len(data["messages_over_time"]) > 0

def test_whatsapp_account_management_and_profile_sync():
    login_res = client.post("/api/v1/auth/login", json={
        "email": "admin@wamarketing.app",
        "password": "admin123"
    })
    headers = {"Authorization": f"Bearer {login_res.json()['access_token']}"}

    # 1. Connect new WhatsApp Business account
    payload = {
        "business_name": "Acme VIP Concierge",
        "phone_number": "+15551977671",
        "waba_id": "2080401999220434",
        "phone_number_id": "1181137018426157",
        "access_token": "mock_token_test",
        "is_default": False
    }
    create_res = client.post("/api/v1/settings/whatsapp-accounts", json=payload, headers=headers)
    assert create_res.status_code == 201
    acc_data = create_res.json()
    assert acc_data["phone_number_id"] == "1181137018426157"
    assert acc_data["profile_name"] is not None
    assert acc_data["code_verification_status"] == "VERIFIED"
    acc_id = acc_data["id"]

    # 2. Sync profile
    sync_res = client.post(f"/api/v1/settings/whatsapp-accounts/{acc_id}/sync", headers=headers)
    assert sync_res.status_code == 200
    sync_data = sync_res.json()
    assert sync_data["quality_rating"] == "GREEN"

    # 3. Set as default
    set_def_res = client.post(f"/api/v1/settings/whatsapp-accounts/{acc_id}/set-default", headers=headers)
    assert set_def_res.status_code == 200
    assert set_def_res.json()["is_default"] is True

    # 4. List accounts
    list_res = client.get("/api/v1/settings/whatsapp-accounts", headers=headers)
    assert list_res.status_code == 200
    assert len(list_res.json()) >= 1

    # 5. Disconnect account
    del_res = client.delete(f"/api/v1/settings/whatsapp-accounts/{acc_id}", headers=headers)
    assert del_res.status_code == 204

def test_message_sending_and_template_flow():
    login_res = client.post("/api/v1/auth/login", json={
        "email": "admin@wamarketing.app",
        "password": "admin123"
    })
    headers = {"Authorization": f"Bearer {login_res.json()['access_token']}"}

    # Ensure mock access token for isolated test runner
    db = SessionLocal()
    acc = db.query(WhatsAppAccount).first()
    if acc:
        acc.access_token = "mock_test_token"
        db.commit()
    db.close()

    # Create a unique contact
    import random
    rand_phone = f"+92300555{random.randint(1000, 9999)}"
    c_res = client.post("/api/v1/contacts", json={
        "first_name": "Rashid",
        "last_name": "Minhas",
        "phone_number": rand_phone
    }, headers=headers)
    assert c_res.status_code == 201
    c_id = c_res.json()["id"]

    # Send template message
    tmpl_msg_res = client.post("/api/v1/inbox/messages", json={
        "contact_id": c_id,
        "template_name": "welcome_intro_offer",
        "template_language": "en",
        "parameters": ["Rashid", "Acme"]
    }, headers=headers)
    assert tmpl_msg_res.status_code == 200
    assert tmpl_msg_res.json()["status"] == "SENT"

    # Send free-form text message
    text_msg_res = client.post("/api/v1/inbox/messages", json={
        "contact_id": c_id,
        "body": "Hello Rashid, follow-up on your inquiry."
    }, headers=headers)
    assert text_msg_res.status_code == 200
    assert text_msg_res.json()["status"] == "SENT"

def test_file_uploads_and_apk_compatibility():
    login_res = client.post("/api/v1/auth/login", json={
        "email": "admin@wamarketing.app",
        "password": "admin123"
    })
    headers = {"Authorization": f"Bearer {login_res.json()['access_token']}"}

    # 1. Upload PDF
    pdf_bytes = b"%PDF-1.4 mock pdf content"
    pdf_res = client.post(
        "/api/v1/media/upload",
        files={"file": ("brochure.pdf", pdf_bytes, "application/pdf")},
        headers=headers
    )
    assert pdf_res.status_code == 201
    pdf_data = pdf_res.json()
    assert pdf_data["file_type"] == "document"
    assert pdf_data["is_whatsapp_supported"] is True
    pdf_id = pdf_data["id"]

    # 2. Upload APK file (allowed in library, but tagged as NOT WhatsApp deliverable)
    apk_bytes = b"PK\x03\x04 mock android package"
    apk_res = client.post(
        "/api/v1/media/upload",
        files={"file": ("wamarketing-debug.apk", apk_bytes, "application/vnd.android.package-archive")},
        headers=headers
    )
    assert apk_res.status_code == 201
    apk_data = apk_res.json()
    assert apk_data["file_type"] == "package"
    assert apk_data["is_whatsapp_supported"] is False
    assert "not support outbound sending of APK" in apk_data["whatsapp_unsupported_reason"]
    apk_id = apk_data["id"]

    # 3. Security: Attempt to upload malicious executable .exe (must be blocked)
    exe_bytes = b"MZ mock executable"
    exe_res = client.post(
        "/api/v1/media/upload",
        files={"file": ("malicious.exe", exe_bytes, "application/x-msdownload")},
        headers=headers
    )
    assert exe_res.status_code == 400
    assert "Security violation" in exe_res.json()["detail"]

    # 4. Download file
    dl_res = client.get(f"/api/v1/media/{pdf_id}/download", headers=headers)
    assert dl_res.status_code == 200

    # 5. Delete file
    del_res = client.delete(f"/api/v1/media/{pdf_id}", headers=headers)
    assert del_res.status_code == 204
    client.delete(f"/api/v1/media/{apk_id}", headers=headers)

def test_contacts_preview_and_confirm_import_flow():
    login_res = client.post("/api/v1/auth/login", json={
        "email": "admin@wamarketing.app",
        "password": "admin123"
    })
    headers = {"Authorization": f"Bearer {login_res.json()['access_token']}"}

    # 1. Preview CSV with fresh phone numbers
    import random
    p1 = f"+92300{random.randint(1000000, 9999999)}"
    p2 = f"+92301{random.randint(1000000, 9999999)}"
    csv_bytes = f"name,phone,company,city\nImran Khan,{p1},PTI,Islamabad\nNawaz Sharif,{p2},PML,Lahore".encode()
    prev_res = client.post(
        "/api/v1/contacts/preview",
        files={"file": ("voters.csv", csv_bytes, "text/csv")},
        data={"default_country_code": "+92"},
        headers=headers
    )
    assert prev_res.status_code == 200
    pdata = prev_res.json()
    assert pdata["total_records"] == 2
    assert pdata["valid_count"] == 2
    assert len(pdata["records"]) == 2

    # 2. Confirm Import
    conf_res = client.post(
        "/api/v1/contacts/confirm-import",
        json={
            "records": [
                {"name": "Imran Khan", "phone": p1, "company": "PTI", "city": "Islamabad", "status": "VALID"},
                {"name": "Nawaz Sharif", "phone": p2, "company": "PML", "city": "Lahore", "status": "VALID"}
            ],
            "tag_name": "VIP Candidates",
            "source": "voters.csv"
        },
        headers=headers
    )
    assert conf_res.status_code == 200
    cdata = conf_res.json()
    assert cdata["imported"] == 2
    assert cdata["total_records"] == 2

def test_ai_chat_and_developer_info():
    login_res = client.post("/api/v1/auth/login", json={
        "email": "admin@wamarketing.app",
        "password": "admin123"
    })
    headers = {"Authorization": f"Bearer {login_res.json()['access_token']}"}

    chat_res = client.post(
        "/api/v1/ai/chat",
        json={"message": "Who is Barkat Ali and how to connect WhatsApp?"},
        headers=headers
    )
    assert chat_res.status_code == 200
    data = chat_res.json()
    assert "Barkat Ali" in data["reply"]
    assert "https://barkatai.ihsanraza.com/#about" in data["reply"]
    assert "Barkat Ali" in data["developer_info"]
