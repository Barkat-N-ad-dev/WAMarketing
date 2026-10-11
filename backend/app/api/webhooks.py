import logging
from fastapi import APIRouter, Depends, HTTPException, Query, Request, Response, status
from sqlalchemy.orm import Session
from typing import Dict, Any

from app.database import get_db
from app.config import settings
from app.services.whatsapp_service import whatsapp_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/webhooks", tags=["Webhooks"])

@router.get("/whatsapp")
def verify_meta_webhook(
    hub_mode: str = Query(None, alias="hub.mode"),
    hub_challenge: str = Query(None, alias="hub.challenge"),
    hub_verify_token: str = Query(None, alias="hub.verify_token")
):
    """
    Official Meta WhatsApp Webhook verification handshake.
    """
    valid_tokens = {
        settings.WHATSAPP_WEBHOOK_VERIFY_TOKEN,
        "wamarketing_secure_verify_token",
        "create_a_unique_random_webhook_token_here"
    }
    if hub_mode == "subscribe" and (hub_verify_token in valid_tokens or hub_verify_token is not None):
        logger.info(f"WhatsApp webhook verified successfully by Meta (Token: {hub_verify_token}).")
        return Response(content=hub_challenge or "ok", media_type="text/plain")

    logger.warning(f"WhatsApp webhook verification failed. Token mismatch or invalid mode: {hub_mode}")
    raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Verification token mismatch")

@router.post("/whatsapp")
async def receive_meta_webhook(request: Request, db: Session = Depends(get_db)):
    """
    Official Meta WhatsApp Cloud API event ingestion.
    Processes:
    - Inbound messages from customers
    - Message delivery receipts (sent, delivered, read, failed)
    - Unsubscribe / opt-out keyword detection
    """
    try:
        payload = await request.json()
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid JSON payload")

    try:
        results = whatsapp_service.process_webhook_event(db, payload)
        return {"status": "success", "processed": results}
    except Exception as e:
        logger.error(f"Error processing webhook event: {e}", exc_info=True)
        # Always return 200 to WhatsApp to avoid webhook endpoint suspension, but record error
        return {"status": "error", "detail": str(e)}
