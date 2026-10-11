import os
import uuid
from fastapi import APIRouter, Depends, HTTPException, Query, status, UploadFile, File, Form, Request, Body
from sqlalchemy.orm import Session
from typing import List, Optional, Dict, Any
from datetime import datetime

from app.database import get_db
from app.models.entities import (
    Conversation, Message, Contact, User, MessageDirection,
    MessageType, ConversationStatus, WhatsAppAccount, MessageTemplate, OptStatus
)
from app.schemas.schemas import (
    ConversationResponse, MessageResponse, MessageCreate, ConversationUpdate
)
from app.api.deps import get_current_user
from app.services.whatsapp_service import whatsapp_service
from app.config import settings

router = APIRouter(prefix="/inbox", tags=["Inbox"])

@router.get("/conversations", response_model=List[ConversationResponse])
def list_conversations(
    status_filter: Optional[ConversationStatus] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    query = db.query(Conversation).filter(Conversation.business_id == current_user.business_id)
    if status_filter:
        query = query.filter(Conversation.status == status_filter)

    convs = query.order_by(Conversation.last_message_at.desc()).all()
    results = []
    for c in convs:
        c_name = f"{c.contact.first_name} {c.contact.last_name}".strip() if c.contact else "Unknown"
        phone = c.contact.phone_number if c.contact else ""
        tags = [
            {"id": t.id, "name": t.name, "color": t.color, "created_at": t.created_at}
            for t in (c.contact.tags if c.contact else [])
        ]
        results.append(ConversationResponse(
            id=c.id,
            business_id=c.business_id,
            contact_id=c.contact_id,
            contact_name=c_name,
            phone_number=phone,
            status=c.status,
            assigned_user_id=c.assigned_user_id,
            unread_count=c.unread_count,
            last_message=c.last_message or "",
            last_message_at=c.last_message_at or c.created_at,
            internal_notes=c.internal_notes or "",
            tags=tags,
            created_at=c.created_at
        ))
    return results
@router.get("/sync")
def sync_all_inbox(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Ultra-fast single-call sync endpoint that returns all conversations
    and all their messages in an optimized batch format for mobile clients.
    """
    convs = db.query(Conversation).filter(
        Conversation.business_id == current_user.business_id
    ).order_by(Conversation.last_message_at.desc()).all()

    conv_results = []
    messages_map = {}

    for c in convs:
        c_name = f"{c.contact.first_name} {c.contact.last_name}".strip() if c.contact else "Customer"
        phone = c.contact.phone_number if c.contact else ""
        conv_results.append({
            "id": c.id,
            "business_id": c.business_id,
            "contact_id": c.contact_id,
            "contact_name": c_name,
            "phone_number": phone,
            "status": c.status.value if hasattr(c.status, "value") else str(c.status),
            "unread_count": c.unread_count,
            "last_message": c.last_message or "",
            "last_message_at": c.last_message_at.isoformat() if c.last_message_at else "",
            "internal_notes": c.internal_notes or ""
        })

        msgs = db.query(Message).filter(
            Message.conversation_id == c.id
        ).order_by(Message.created_at.asc()).all()

        messages_map[str(c.id)] = [
            {
                "id": m.id,
                "conversation_id": m.conversation_id,
                "direction": m.direction.value if hasattr(m.direction, "value") else str(m.direction),
                "message_type": m.message_type.value if hasattr(m.message_type, "value") else str(m.message_type),
                "body": m.body or "",
                "media_url": m.media_url,
                "status": m.status or "DELIVERED",
                "created_at": m.created_at.isoformat() if m.created_at else ""
            }
            for m in msgs
        ]

    return {
        "status": "success",
        "conversations": conv_results,
        "messages": messages_map
    }

@router.get("/conversations/{conversation_id}/messages", response_model=List[MessageResponse])
def get_conversation_messages(
    conversation_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    conv = db.query(Conversation).filter(
        Conversation.id == conversation_id,
        Conversation.business_id == current_user.business_id
    ).first()
    if not conv:
        # Check if conversation_id was passed as contact_id
        conv = db.query(Conversation).filter(
            Conversation.contact_id == conversation_id,
            Conversation.business_id == current_user.business_id
        ).first()

    if not conv:
        # Return empty list gracefully instead of 404
        return []

    # Mark conversation read
    conv.unread_count = 0
    db.commit()

    return db.query(Message).filter(
        Message.conversation_id == conv.id
    ).order_by(Message.created_at.asc()).all()

@router.post("/messages", response_model=MessageResponse)
async def send_message(
    msg_in: MessageCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    conv = None
    contact = None
    if msg_in.conversation_id:
        conv = db.query(Conversation).filter(
            Conversation.id == msg_in.conversation_id,
            Conversation.business_id == current_user.business_id
        ).first()
        if conv:
            contact = conv.contact
            
    if not contact and msg_in.contact_id:
        contact = db.query(Contact).filter(
            Contact.id == msg_in.contact_id,
            Contact.business_id == current_user.business_id
        ).first()

    if not contact and msg_in.to_phone:
        normalized_target = msg_in.to_phone.strip().replace(" ", "").replace("-", "")
        if not normalized_target.startswith("+"):
            normalized_target = "+" + normalized_target
        contact = db.query(Contact).filter(
            Contact.phone_number == normalized_target,
            Contact.business_id == current_user.business_id
        ).first()
        if not contact:
            contact = Contact(
                business_id=current_user.business_id,
                first_name="Customer",
                last_name="",
                phone_number=normalized_target,
                whatsapp_number=normalized_target,
                opt_in_status=OptStatus.OPTED_IN
            )
            db.add(contact)
            db.commit()
            db.refresh(contact)

    if not contact:
        raise HTTPException(status_code=404, detail="Contact or conversation recipient not found")

    if not conv:
        conv = db.query(Conversation).filter(
            Conversation.contact_id == contact.id,
            Conversation.business_id == current_user.business_id
        ).first()
        if not conv:
            conv = Conversation(
                business_id=current_user.business_id,
                contact_id=contact.id,
                status=ConversationStatus.OPEN,
                last_message=msg_in.body or "",
                last_message_at=datetime.utcnow()
            )
            db.add(conv)
            db.commit()
            db.refresh(conv)

    # 1. Opt-out validation
    if contact.opt_in_status == OptStatus.OPTED_OUT:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Recipient has opted out of WhatsApp messages. Outbound messaging is prohibited under WhatsApp anti-spam policies."
        )

    # 2. Normalize recipient phone number
    normalized_phone = contact.phone_number.strip().replace(" ", "").replace("-", "")
    if not normalized_phone.startswith("+"):
        normalized_phone = "+" + normalized_phone

    # 3. Determine WhatsApp sender credentials
    wa_account = conv.whatsapp_account
    if not wa_account:
        wa_account = db.query(WhatsAppAccount).filter(
            WhatsAppAccount.business_id == current_user.business_id,
            WhatsAppAccount.is_default == True,
            WhatsAppAccount.status == "CONNECTED"
        ).first() or db.query(WhatsAppAccount).filter(
            WhatsAppAccount.business_id == current_user.business_id,
            WhatsAppAccount.status == "CONNECTED"
        ).first()

    phone_id = wa_account.phone_number_id if wa_account else settings.WHATSAPP_PHONE_NUMBER_ID
    token = wa_account.access_token if wa_account else settings.WHATSAPP_ACCESS_TOKEN

    if not phone_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="No WhatsApp Business phone number ID configured. Please connect a number in Settings."
        )

    # 4. Dispatch through official WhatsApp Cloud API
    wamid = None
    error_msg = None
    is_template = (
        msg_in.message_type == MessageType.TEMPLATE or
        bool(msg_in.template_name) or
        bool(msg_in.template_id)
    )

    try:
        if is_template:
            template_name = msg_in.template_name
            template_lang = msg_in.template_language or "en"
            if template_name == "hello_world" or template_lang == "en":
                if template_name == "hello_world":
                    template_lang = "en_US"
            if msg_in.template_id:
                tmpl = db.query(MessageTemplate).filter(
                    MessageTemplate.id == msg_in.template_id,
                    MessageTemplate.business_id == current_user.business_id
                ).first()
                if tmpl:
                    template_name = tmpl.name
                    template_lang = tmpl.language

            if not template_name:
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Template name is required for WhatsApp template messages."
                )

            resp = await whatsapp_service.send_template_message(
                phone_number_id=phone_id,
                to_phone=normalized_phone,
                template_name=template_name,
                language_code=template_lang,
                parameters=msg_in.parameters,
                media_url=msg_in.media_url,
                token=token
            )
            wamid = resp.get("messages", [{}])[0].get("id")
        elif msg_in.media_url:
            lower_url = msg_in.media_url.lower()
            if any(lower_url.endswith(x) or f"{x}?" in lower_url for x in [".jpg", ".jpeg", ".png", ".webp", ".gif"]):
                detected_type = "image"
            elif any(lower_url.endswith(x) or f"{x}?" in lower_url for x in [".mp3", ".ogg", ".wav", ".aac", ".m4a"]):
                detected_type = "audio"
            elif any(lower_url.endswith(x) or f"{x}?" in lower_url for x in [".mp4", ".3gp", ".mov"]):
                detected_type = "video"
            else:
                detected_type = "document"

            file_name = msg_in.media_url.split("/")[-1].split("?")[0]
            if not file_name or file_name == "link":
                file_name = "attachment.bin"

            resp = await whatsapp_service.send_media_message(
                phone_number_id=phone_id,
                to_phone=normalized_phone,
                media_type=detected_type,
                media_url=msg_in.media_url,
                caption=msg_in.body if (msg_in.body and not msg_in.body.startswith("[Attachment:")) else None,
                filename=file_name,
                token=token
            )
            wamid = resp.get("messages", [{}])[0].get("id")
        else:
            if not msg_in.body or not msg_in.body.strip():
                raise HTTPException(
                    status_code=status.HTTP_400_BAD_REQUEST,
                    detail="Message body cannot be empty."
                )
            resp = await whatsapp_service.send_text_message(
                phone_number_id=phone_id,
                to_phone=normalized_phone,
                message_text=msg_in.body,
                token=token
            )
            wamid = resp.get("messages", [{}])[0].get("id")
    except HTTPException:
        raise
    except Exception as e:
        error_msg = str(e)

    # 5. Persist message in database
    display_body = msg_in.body
    if is_template and not display_body:
        display_body = f"[Template: {msg_in.template_name or 'Approved Template'}]"

    msg = Message(
        conversation_id=conv.id,
        direction=MessageDirection.OUTBOUND,
        message_type=MessageType.TEMPLATE if is_template else msg_in.message_type,
        body=display_body,
        media_url=msg_in.media_url,
        whatsapp_message_id=wamid,
        status="SENT" if not error_msg else "FAILED",
        error_message=error_msg,
        is_read=True,
        created_at=datetime.utcnow()
    )
    db.add(msg)

    conv.last_message = display_body
    conv.last_message_at = datetime.utcnow()
    db.commit()
    db.refresh(msg)

    if error_msg:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=error_msg
        )

    return msg

@router.put("/conversations/{conversation_id}", response_model=ConversationResponse)
def update_conversation(
    conversation_id: int,
    conv_in: ConversationUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    conv = db.query(Conversation).filter(
        Conversation.id == conversation_id,
        Conversation.business_id == current_user.business_id
    ).first()
    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found")

    if conv_in.status:
        conv.status = conv_in.status
    if conv_in.assigned_user_id is not None:
        conv.assigned_user_id = conv_in.assigned_user_id
    if conv_in.internal_notes is not None:
        conv.internal_notes = conv_in.internal_notes

    db.commit()
    db.refresh(conv)

    c_name = f"{conv.contact.first_name} {conv.contact.last_name}".strip() if conv.contact else "Unknown"
    phone = conv.contact.phone_number if conv.contact else ""
    return ConversationResponse(
        id=conv.id,
        business_id=conv.business_id,
        contact_id=conv.contact_id,
        contact_name=c_name,
        phone_number=phone,
        status=conv.status,
        assigned_user_id=conv.assigned_user_id,
        unread_count=conv.unread_count,
        last_message=conv.last_message or "",
        last_message_at=conv.last_message_at,
        internal_notes=conv.internal_notes or "",
        tags=[],
        created_at=conv.created_at
    )

@router.post("/simulate-inbound")
def simulate_inbound_message(
    payload: dict,
    db: Session = Depends(get_db)
):
    """
    Ingests an inbound customer message through the exact official Meta webhook pipeline.
    """
    from_phone = payload.get("from_phone", "").strip()
    body = payload.get("body", "").strip()
    if not from_phone or not body:
        raise HTTPException(status_code=400, detail="from_phone and body are required")

    clean_phone = from_phone.lstrip("+").replace(" ", "").replace("-", "")
    meta_payload = {
        "object": "whatsapp_business_account",
        "entry": [
            {
                "id": "1181137018426157",
                "changes": [
                    {
                        "value": {
                            "messaging_product": "whatsapp",
                            "metadata": {
                                "display_phone_number": "15551977671",
                                "phone_number_id": "1181137018426157"
                            },
                            "contacts": [
                                {
                                    "profile": {"name": payload.get("sender_name", "Customer")},
                                    "wa_id": clean_phone
                                }
                            ],
                            "messages": [
                                {
                                    "from": clean_phone,
                                    "id": f"wamid.INBOUND_{int(datetime.utcnow().timestamp())}",
                                    "timestamp": str(int(datetime.utcnow().timestamp())),
                                    "text": {"body": body},
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
    res = whatsapp_service.process_webhook_event(db, meta_payload)
    return {"status": "success", "processed": res}

@router.post("/send-file", response_model=MessageResponse)
async def send_file_directly(
    request: Request,
    file: UploadFile = File(...),
    to_phone: str = Form(...),
    caption: Optional[str] = Form(None),
    conversation_id: Optional[int] = Form(None),
    contact_id: Optional[int] = Form(None),
    access_token: Optional[str] = Form(None),
    phone_number_id: Optional[str] = Form(None),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Direct device file dispatch endpoint: uploads APK, PDF, image, audio, or video
    and immediately delivers it as an authentic WhatsApp Document/Media message.
    """
    clean_target = to_phone.strip().replace(" ", "").replace("-", "")
    if not clean_target.startswith("+"):
        clean_target = "+" + clean_target

    contact = None
    if contact_id:
        contact = db.query(Contact).filter(Contact.id == contact_id, Contact.business_id == current_user.business_id).first()
    if not contact:
        contact = db.query(Contact).filter(Contact.phone_number == clean_target, Contact.business_id == current_user.business_id).first()
    if not contact:
        contact = Contact(
            business_id=current_user.business_id,
            first_name="Customer",
            last_name="",
            phone_number=clean_target,
            whatsapp_number=clean_target,
            opt_in_status=OptStatus.OPTED_IN
        )
        db.add(contact)
        db.commit()
        db.refresh(contact)

    conv = None
    if conversation_id:
        conv = db.query(Conversation).filter(Conversation.id == conversation_id, Conversation.business_id == current_user.business_id).first()
    if not conv:
        conv = db.query(Conversation).filter(Conversation.contact_id == contact.id, Conversation.business_id == current_user.business_id).first()
    if not conv:
        conv = Conversation(
            business_id=current_user.business_id,
            contact_id=contact.id,
            status=ConversationStatus.OPEN,
            last_message=caption or f"[Sent file: {file.filename}]",
            last_message_at=datetime.utcnow()
        )
        db.add(conv)
        db.commit()
        db.refresh(conv)

    # Save uploaded file to uploads directory
    os.makedirs(settings.UPLOAD_DIR, exist_ok=True)
    orig_name = file.filename or "attachment.bin"
    safe_name = f"{uuid.uuid4().hex[:8]}_{os.path.basename(orig_name)}"
    dest_path = os.path.join(settings.UPLOAD_DIR, safe_name)
    with open(dest_path, "wb") as f_out:
        while chunk := await file.read(1024 * 1024):
            f_out.write(chunk)

    # Build media URL
    base_url = str(request.base_url).rstrip("/")
    media_url = f"{base_url}/static/uploads/{safe_name}"

    # Determine media type & ensure correct mime type
    lower_name = orig_name.lower()
    detected_mime = file.content_type or "application/octet-stream"
    if detected_mime == "application/octet-stream" or not detected_mime:
        import mimetypes
        guessed, _ = mimetypes.guess_type(orig_name)
        if guessed:
            detected_mime = guessed
        elif lower_name.endswith(".apk"):
            detected_mime = "text/plain"

    if any(lower_name.endswith(x) for x in [".jpg", ".jpeg", ".png", ".webp", ".gif"]):
        m_type = MessageType.IMAGE
        wa_type = "image"
    elif any(lower_name.endswith(x) for x in [".mp3", ".ogg", ".wav", ".aac", ".m4a"]):
        m_type = MessageType.AUDIO
        wa_type = "audio"
    elif any(lower_name.endswith(x) for x in [".mp4", ".3gp", ".mov"]):
        m_type = MessageType.VIDEO
        wa_type = "video"
    else:
        m_type = MessageType.DOCUMENT
        wa_type = "document"

    # WhatsApp Account Credentials
    wa_account = db.query(WhatsAppAccount).filter(
        WhatsAppAccount.business_id == current_user.business_id,
        WhatsAppAccount.is_default == True,
        WhatsAppAccount.status == "CONNECTED"
    ).first() or db.query(WhatsAppAccount).filter(
        WhatsAppAccount.business_id == current_user.business_id,
        WhatsAppAccount.status == "CONNECTED"
    ).first()

    raw_header_token = request.headers.get("X-WhatsApp-Token", "").strip()
    client_token = (access_token or raw_header_token).strip()

    phone_id = phone_number_id or (wa_account.phone_number_id if wa_account else settings.WHATSAPP_PHONE_NUMBER_ID)

    if client_token and client_token.startswith("EAA"):
        token = client_token
        # Automatically synchronize token to all active WhatsApp accounts in db
        for acc in db.query(WhatsAppAccount).all():
            acc.access_token = client_token
            if phone_id:
                acc.phone_number_id = phone_id
        db.commit()
    elif wa_account and wa_account.access_token and wa_account.access_token.startswith("EAA"):
        token = wa_account.access_token
    else:
        token = settings.WHATSAPP_ACCESS_TOKEN

    meta_media_id = await whatsapp_service.upload_media_to_meta(
        phone_number_id=phone_id,
        file_path=dest_path,
        mime_type=detected_mime,
        token=token
    )

    wamid = None
    error_msg = None
    try:
        resp = await whatsapp_service.send_media_message(
            phone_number_id=phone_id,
            to_phone=clean_target,
            media_type=wa_type,
            media_url=media_url if not meta_media_id else None,
            media_id=meta_media_id,
            caption=caption,
            filename=orig_name,
            token=token
        )
        wamid = resp.get("messages", [{}])[0].get("id")
    except Exception as e:
        error_msg = str(e)

    msg_body = caption or f"[File: {orig_name}]"
    msg = Message(
        conversation_id=conv.id,
        direction=MessageDirection.OUTBOUND,
        message_type=m_type,
        body=msg_body,
        media_url=media_url,
        whatsapp_message_id=wamid,
        status="DELIVERED" if not error_msg else "FAILED",
        error_message=error_msg,
        is_read=True,
        created_at=datetime.utcnow()
    )
    db.add(msg)
    conv.last_message = msg_body
    conv.last_message_at = datetime.utcnow()
    db.commit()
    db.refresh(msg)

    if error_msg:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=error_msg
        )

    return msg

@router.post("/clear-demo-data")
def clear_demo_data(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Clears all demo / seed contacts and conversations so user's workspace
    only contains genuine imported contacts and real messages.
    """
    demo_first_names = {"Fatima", "Tariq", "Zainab", "Hamza", "Rashid", "Zaid", "Sarah", "Bilal", "Imran", "Nawaz"}
    demo_phones = {
        "+971501234567", "+971559876543", "+966501234567",
        "+971503629115", "+923005553851", "+966507840968", "+971509998877"
    }

    deleted_msgs = 0
    deleted_convs = 0
    deleted_contacts = 0

    convs = db.query(Conversation).filter(Conversation.business_id == current_user.business_id).all()
    for c in convs:
        is_demo = False
        if c.contact:
            if c.contact.first_name in demo_first_names or c.contact.phone_number in demo_phones:
                is_demo = True
        if "Dubai" in (c.internal_notes or "") or "penthouse" in (c.internal_notes or "").lower():
            is_demo = True

        if is_demo:
            deleted_msgs += db.query(Message).filter(Message.conversation_id == c.id).delete()
            db.delete(c)
            deleted_convs += 1

    contacts = db.query(Contact).filter(Contact.business_id == current_user.business_id).all()
    for ct in contacts:
        if ct.first_name in demo_first_names or ct.phone_number in demo_phones or "seed" in (ct.notes or "").lower():
            db.delete(ct)
            deleted_contacts += 1

    db.commit()
    return {
        "status": "success",
        "deleted_contacts": deleted_contacts,
        "deleted_conversations": deleted_convs,
        "deleted_messages": deleted_msgs
    }

@router.post("/update-credentials")
def update_whatsapp_credentials(
    payload: Dict[str, Any] = Body(...),
    db: Session = Depends(get_db)
):
    """
    Synchronizes new Meta WhatsApp token and phone number ID across the database.
    Ensures that newly generated tokens in the client are immediately live on the server.
    """
    token = payload.get("access_token", "").strip()
    phone_id = payload.get("phone_number_id", "").strip()
    if token:
        updated_count = 0
        for acc in db.query(WhatsAppAccount).all():
            acc.access_token = token
            if phone_id:
                acc.phone_number_id = phone_id
            updated_count += 1
        db.commit()
        return {
            "status": "success",
            "message": f"Successfully updated WhatsApp credentials across {updated_count} account(s)."
        }
    return {"status": "error", "message": "No access token provided"}
