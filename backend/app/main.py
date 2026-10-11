import os
import json
import logging
from contextlib import asynccontextmanager
from typing import List, Set
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.config import settings
from app.database import engine, Base, SessionLocal
from app.models.entities import User, Business, Tag, MessageTemplate, WhatsAppAccount, UserRole, OptStatus
from app.utils.security import hash_password

from app.api.auth import router as auth_router
from app.api.contacts import router as contacts_router
from app.api.campaigns import router as campaigns_router
from app.api.templates import router as templates_router
from app.api.inbox import router as inbox_router
from app.api.crm import router as crm_router
from app.api.automations import router as automations_router
from app.api.ai import router as ai_router
from app.api.analytics import router as analytics_router
from app.api.media import router as media_router
from app.api.settings import router as settings_router
from app.api.webhooks import router as webhooks_router
from app.api.wallet import router as wallet_router

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Real-time WebSocket connection manager
class ConnectionManager:
    def __init__(self):
        self.active_connections: List[WebSocket] = []

    async def connect(self, websocket: WebSocket):
        await websocket.accept()
        self.active_connections.append(websocket)

    def disconnect(self, websocket: WebSocket):
        if websocket in self.active_connections:
            self.active_connections.remove(websocket)

    async def broadcast(self, message: dict):
        for connection in list(self.active_connections):
            try:
                await connection.send_text(json.dumps(message))
            except Exception:
                self.disconnect(connection)

ws_manager = ConnectionManager()

def seed_initial_data():
    db = SessionLocal()
    try:
        biz = db.query(Business).first()
        if not biz:
            biz = Business(
                name="Acme Enterprise",
                phone="+15551234567",
                email="admin@wamarketing.app",
                plan="Business"
            )
            db.add(biz)
            db.commit()
            db.refresh(biz)

        # Ensure default admin user exists
        admin = db.query(User).filter(User.email == "admin@wamarketing.app").first()
        if not admin:
            admin = User(
                business_id=biz.id,
                email="admin@wamarketing.app",
                password_hash=hash_password("admin123"),
                full_name="Business Owner",
                role=UserRole.OWNER,
                is_active=True
            )
            db.add(admin)

        # Default WhatsApp Business account
        wa_acc = db.query(WhatsAppAccount).filter(WhatsAppAccount.business_id == biz.id).first()
        if not wa_acc:
            wa_acc = WhatsAppAccount(
                business_id=biz.id,
                business_name="Test number",
                phone_number=os.getenv("DEFAULT_WHATSAPP_PHONE", "+15551977671"),
                waba_id=settings.WHATSAPP_BUSINESS_ACCOUNT_ID or "2080401999220434",
                phone_number_id=settings.WHATSAPP_PHONE_NUMBER_ID or "1181137018426157",
                access_token=settings.WHATSAPP_ACCESS_TOKEN,
                status="CONNECTED",
                is_default=True
            )
            db.add(wa_acc)
        else:
            wa_acc.access_token = settings.WHATSAPP_ACCESS_TOKEN
            wa_acc.phone_number_id = settings.WHATSAPP_PHONE_NUMBER_ID or "1181137018426157"
            wa_acc.waba_id = settings.WHATSAPP_BUSINESS_ACCOUNT_ID or "2080401999220434"
            wa_acc.business_name = "Test number"
            wa_acc.phone_number = os.getenv("DEFAULT_WHATSAPP_PHONE", "+15551977671")
            db.commit()

        # Default tags
        tag_count = db.query(Tag).filter(Tag.business_id == biz.id).count()
        if tag_count == 0:
            default_tags = [
                ("New Lead", "#3B82F6"),
                ("Hot Lead", "#EF4444"),
                ("Customer", "#10B981"),
                ("VIP", "#8B5CF6"),
                ("Follow Up", "#F59E0B"),
                ("Dubai", "#06B6D4"),
                ("Pakistan", "#14B8A6"),
                ("Saudi Arabia", "#84CC16")
            ]
            for tag_name, color in default_tags:
                t = Tag(business_id=biz.id, name=tag_name, color=color)
                db.add(t)

        # Default WhatsApp templates
        tmpl_count = db.query(MessageTemplate).filter(MessageTemplate.business_id == biz.id).count()
        if tmpl_count == 0:
            templates = [
                MessageTemplate(
                    business_id=biz.id,
                    name="hello_world",
                    category="Utility",
                    language="en_US",
                    content="Hello World! Welcome and thank you for choosing our business.",
                    template_type="WhatsApp Approved Template",
                    status="APPROVED",
                    variables=json.dumps([])
                ),
                MessageTemplate(
                    business_id=biz.id,
                    name="welcome_intro_offer",
                    category="Marketing",
                    language="en",
                    content="Hello {{first_name}}, welcome to {{company}}! We are thrilled to have you with us. Reply 'OFFER' to see our exclusive launch discounts.",
                    template_type="WhatsApp Approved Template",
                    status="APPROVED",
                    variables=json.dumps(["first_name", "company"])
                ),
                MessageTemplate(
                    business_id=biz.id,
                    name="consultation_follow_up",
                    category="Follow-up",
                    language="en",
                    content="Hi {{first_name}}, following up on our recent conversation regarding our services in {{city}}. Would tomorrow at 3 PM work for a quick demo?",
                    template_type="Local App Template",
                    status="APPROVED",
                    variables=json.dumps(["first_name", "city"])
                ),
                MessageTemplate(
                    business_id=biz.id,
                    name="appointment_reminder_v1",
                    category="Appointment",
                    language="en",
                    content="Dear {{first_name}}, this is a friendly reminder for your scheduled briefing on {{date}} at {{time}}. Reply 'CONFIRM' to confirm.",
                    template_type="WhatsApp Approved Template",
                    status="APPROVED",
                    variables=json.dumps(["first_name", "date", "time"])
                )
            ]
            for tmpl in templates:
                db.add(tmpl)

        db.commit()
        logger.info("Default seed data initialized successfully.")
    finally:
        db.close()

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup: Ensure database schema exists
    os.makedirs(settings.UPLOAD_DIR, exist_ok=True)
    Base.metadata.create_all(bind=engine)
    seed_initial_data()
    yield

app = FastAPI(
    title="WAMarketing API",
    description="Production-grade SaaS backend for WhatsApp Marketing, Campaigns, CRM, Automations & Analytics.",
    version="1.0.0",
    lifespan=lifespan
)

# CORS configuration
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount static files for media uploads
os.makedirs(settings.UPLOAD_DIR, exist_ok=True)
app.mount("/static/uploads", StaticFiles(directory=settings.UPLOAD_DIR), name="uploads")

# Include API Routers
app.include_router(auth_router, prefix="/api/v1")
app.include_router(contacts_router, prefix="/api/v1")
app.include_router(campaigns_router, prefix="/api/v1")
app.include_router(templates_router, prefix="/api/v1")
app.include_router(inbox_router, prefix="/api/v1")
app.include_router(crm_router, prefix="/api/v1")
app.include_router(automations_router, prefix="/api/v1")
app.include_router(ai_router, prefix="/api/v1")
app.include_router(analytics_router, prefix="/api/v1")
app.include_router(media_router, prefix="/api/v1")
app.include_router(settings_router, prefix="/api/v1")
app.include_router(webhooks_router, prefix="/api/v1")
app.include_router(wallet_router, prefix="/api/v1")

@app.get("/")
@app.get("/health")
@app.get("/api/v1/health")
def health_check():
    return {
        "status": "online",
        "app": settings.APP_NAME,
        "version": "1.0.0",
        "documentation": "/docs"
    }

@app.websocket("/ws/inbox")
async def websocket_inbox_endpoint(websocket: WebSocket):
    await ws_manager.connect(websocket)
    try:
        while True:
            data = await websocket.receive_text()
            # Echo or broadcast message event
            await ws_manager.broadcast({"event": "client_message", "data": data})
    except WebSocketDisconnect:
        ws_manager.disconnect(websocket)

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run("app.main:app", host="0.0.0.0", port=port)
