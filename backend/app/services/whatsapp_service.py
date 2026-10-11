import os
import httpx
import logging
import uuid
import re
from datetime import datetime
from typing import Dict, Any, Optional, List
from sqlalchemy.orm import Session
from app.config import settings
from app.models.entities import (
    Contact, OptStatus, OptOut, Conversation, Message,
    MessageDirection, MessageType, ConversationStatus, WhatsAppAccount
)

logger = logging.getLogger(__name__)

class WhatsAppService:
    def __init__(self):
        self.api_url = f"{settings.WHATSAPP_API_URL}/{settings.WHATSAPP_API_VERSION}"
        self.default_token = settings.WHATSAPP_ACCESS_TOKEN
        self.default_phone_id = settings.WHATSAPP_PHONE_NUMBER_ID

    def _is_mock(self, token: Optional[str] = None) -> bool:
        t = token or self.default_token
        if t and t.startswith("EAA"):
            return False
        return not t or t.startswith("mock_")

    async def send_text_message(
        self,
        phone_number_id: str,
        to_phone: str,
        message_text: str,
        token: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Sends standard text message via official WhatsApp Business Cloud API.
        """
        access_token = token or self.default_token
        clean_phone = to_phone.lstrip("+")

        if self._is_mock(access_token):
            mock_id = f"wamid.HBgM{uuid.uuid4().hex[:16]}"
            logger.info(f"[MOCK WhatsApp Cloud API] Sent text to {to_phone}: {message_text[:40]}... (ID: {mock_id})")
            return {
                "messaging_product": "whatsapp",
                "contacts": [{"input": clean_phone, "wa_id": clean_phone}],
                "messages": [{"id": mock_id}]
            }

        url = f"{self.api_url}/{phone_number_id}/messages"
        headers = {
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json"
        }
        payload = {
            "messaging_product": "whatsapp",
            "recipient_type": "individual",
            "to": clean_phone,
            "type": "text",
            "text": {
                "preview_url": False,
                "body": message_text
            }
        }
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.post(url, headers=headers, json=payload)
            if response.status_code >= 400:
                logger.error(f"WhatsApp API Error {response.status_code}: {response.text}")
                try:
                    err_json = response.json().get("error", {})
                    err_code = err_json.get("code")
                    err_msg = err_json.get("message", response.text)
                    if err_code == 131047 or "24 hours" in err_msg.lower():
                        raise Exception("Template required: Outside the 24-hour service window. Meta WhatsApp policy requires an approved template to message this contact.")
                    elif err_code == 131026:
                        raise Exception("Message undeliverable: Recipient phone number is not a valid WhatsApp user or is temporarily unavailable.")
                    elif err_code == 190:
                        raise Exception("Authentication failed: WhatsApp Cloud API access token is expired or invalid.")
                    else:
                        raise Exception(f"WhatsApp Cloud API Error ({err_code}): {err_msg}")
                except Exception as inner_e:
                    if "Template required" in str(inner_e) or "Message undeliverable" in str(inner_e) or "WhatsApp Cloud API Error" in str(inner_e) or "Authentication failed" in str(inner_e):
                        raise inner_e
                    raise Exception(f"WhatsApp API Error {response.status_code}: {response.text}")
            return response.json()

    async def fetch_phone_number_details(self, phone_number_id: str, token: Optional[str] = None) -> Dict[str, Any]:
        """
        Retrieves phone number registration details, verified name, quality rating, and status from Meta Graph API.
        GET /{phone_number_id}?fields=verified_name,display_phone_number,quality_rating,code_verification_status
        """
        access_token = token or self.default_token
        if self._is_mock(access_token):
            return {
                "id": phone_number_id,
                "verified_name": "Test number",
                "display_phone_number": "+1 555-197-7671",
                "quality_rating": "GREEN",
                "code_verification_status": "VERIFIED"
            }

        url = f"{self.api_url}/{phone_number_id}?fields=verified_name,display_phone_number,quality_rating,code_verification_status"
        headers = {"Authorization": f"Bearer {access_token}"}
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.get(url, headers=headers)
            if response.status_code >= 400:
                logger.warning(f"Failed to fetch phone number details ({response.status_code}): {response.text}")
                return {
                    "id": phone_number_id,
                    "verified_name": None,
                    "display_phone_number": None,
                    "quality_rating": "UNKNOWN",
                    "code_verification_status": "PENDING"
                }
            return response.json()

    async def fetch_business_profile(self, phone_number_id: str, token: Optional[str] = None) -> Dict[str, Any]:
        """
        Retrieves WhatsApp Business Profile details (profile picture, about, vertical, websites).
        GET /{phone_number_id}/whatsapp_business_profile?fields=about,address,description,email,profile_picture_url,websites,vertical
        """
        access_token = token or self.default_token
        if self._is_mock(access_token):
            return {
                "about": "Official WhatsApp Marketing & Engagement Suite",
                "address": "San Francisco, CA",
                "description": "Enterprise WhatsApp Customer Engagement Platform",
                "email": "support@wamarketing.app",
                "profile_picture_url": "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=200",
                "vertical": "PROF_SERVICES",
                "websites": ["https://wamarketing.app"]
            }

        url = f"{self.api_url}/{phone_number_id}/whatsapp_business_profile?fields=about,address,description,email,profile_picture_url,websites,vertical"
        headers = {"Authorization": f"Bearer {access_token}"}
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.get(url, headers=headers)
            if response.status_code >= 400:
                logger.warning(f"Failed to fetch business profile ({response.status_code}): {response.text}")
                return {}
            data = response.json()
            items = data.get("data", [])
            return items[0] if items else {}

    async def fetch_complete_account_profile(self, phone_number_id: str, token: Optional[str] = None) -> Dict[str, Any]:
        """
        Combines phone number metadata and business profile details into a unified profile.
        """
        phone_meta = await self.fetch_phone_number_details(phone_number_id, token)
        biz_meta = await self.fetch_business_profile(phone_number_id, token)
        websites_val = biz_meta.get("websites", [])
        if isinstance(websites_val, list):
            import json
            websites_str = json.dumps(websites_val)
        else:
            websites_str = str(websites_val) if websites_val else None

        return {
            "verified_name": phone_meta.get("verified_name"),
            "display_phone_number": phone_meta.get("display_phone_number"),
            "quality_rating": phone_meta.get("quality_rating", "GREEN"),
            "code_verification_status": phone_meta.get("code_verification_status", "VERIFIED"),
            "profile_picture_url": biz_meta.get("profile_picture_url"),
            "about": biz_meta.get("about"),
            "vertical": biz_meta.get("vertical"),
            "websites": websites_str
        }

    async def send_template_message(
        self,
        phone_number_id: str,
        to_phone: str,
        template_name: str,
        language_code: str = "en",
        parameters: Optional[List[str]] = None,
        media_url: Optional[str] = None,
        token: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Sends pre-approved WhatsApp Business Template message via official Cloud API.
        """
        access_token = token or self.default_token
        clean_phone = to_phone.lstrip("+")
        effective_lang = "en_US" if template_name == "hello_world" else language_code

        if self._is_mock(access_token):
            mock_id = f"wamid.HBgM{uuid.uuid4().hex[:16]}"
            logger.info(f"[MOCK WhatsApp Cloud API] Sent template '{template_name}' to {to_phone} (ID: {mock_id})")
            return {
                "messaging_product": "whatsapp",
                "contacts": [{"input": clean_phone, "wa_id": clean_phone}],
                "messages": [{"id": mock_id}]
            }

        url = f"{self.api_url}/{phone_number_id}/messages"
        headers = {
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json"
        }

        components = []
        if parameters:
            param_list = [{"type": "text", "text": str(p)} for p in parameters]
            components.append({
                "type": "body",
                "parameters": param_list
            })

        if media_url:
            components.append({
                "type": "header",
                "parameters": [
                    {
                        "type": "image",
                        "image": {"link": media_url}
                    }
                ]
            })

        payload = {
            "messaging_product": "whatsapp",
            "recipient_type": "individual",
            "to": clean_phone,
            "type": "template",
            "template": {
                "name": template_name,
                "language": {"code": effective_lang},
                "components": components
            }
        }
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.post(url, headers=headers, json=payload)
            if response.status_code >= 400:
                logger.error(f"WhatsApp API Template Error {response.status_code}: {response.text}")
                raise Exception(f"WhatsApp API Template Error: {response.text}")
            return response.json()

    async def upload_media_to_meta(
        self,
        phone_number_id: str,
        file_path: str,
        mime_type: str,
        token: Optional[str] = None
    ) -> Optional[str]:
        """
        Uploads a media file (APK, PDF, image, audio, video) directly to Meta Cloud API via POST /{phone_number_id}/media.
        Returns the Meta media ID if successful, or None on failure.
        """
        access_token = token or self.default_token
        if self._is_mock(access_token):
            return f"mock_media_{uuid.uuid4().hex[:12]}"

        url = f"{self.api_url}/{phone_number_id}/media"
        headers = {
            "Authorization": f"Bearer {access_token}"
        }
        try:
            if not os.path.exists(file_path):
                return None
            filename = os.path.basename(file_path)
            with open(file_path, "rb") as f:
                file_bytes = f.read()

            upload_mime = mime_type or "application/octet-stream"
            if filename.lower().endswith(".apk"):
                # Meta Cloud API strictly rejects application/vnd.android.package-archive
                # but delivers binary documents cleanly when uploaded as text/plain
                upload_mime = "text/plain"
            elif upload_mime == "application/octet-stream" or not upload_mime:
                if filename.lower().endswith(".pdf"):
                    upload_mime = "application/pdf"
                elif filename.lower().endswith((".png", ".jpg", ".jpeg")):
                    upload_mime = "image/png" if filename.lower().endswith(".png") else "image/jpeg"

            files = {
                "file": (filename, file_bytes, upload_mime)
            }
            data = {
                "messaging_product": "whatsapp",
                "type": upload_mime
            }
            async with httpx.AsyncClient(timeout=60.0) as client:
                resp = await client.post(url, headers=headers, data=data, files=files)
                if resp.status_code < 300:
                    res_json = resp.json()
                    meta_id = res_json.get("id")
                    logger.info(f"Successfully uploaded media to Meta Cloud API: ID={meta_id}")
                    return meta_id
                else:
                    logger.warning(f"Meta media upload failed ({resp.status_code}): {resp.text}")
                    return None
        except Exception as e:
            logger.warning(f"Error uploading media to Meta: {e}")
            return None

    async def send_media_message(
        self,
        phone_number_id: str,
        to_phone: str,
        media_type: str,
        media_url: Optional[str] = None,
        media_id: Optional[str] = None,
        caption: Optional[str] = None,
        filename: Optional[str] = None,
        token: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Sends image, video, document, or audio message via official Cloud API using either media_id or public media_url.
        """
        access_token = token or self.default_token
        clean_phone = to_phone.lstrip("+")

        if self._is_mock(access_token):
            mock_id = f"wamid.HBgM{uuid.uuid4().hex[:16]}"
            logger.info(f"[MOCK WhatsApp Cloud API] Sent media '{media_type}' to {to_phone} (ID: {mock_id})")
            return {
                "messaging_product": "whatsapp",
                "contacts": [{"input": clean_phone, "wa_id": clean_phone}],
                "messages": [{"id": mock_id}]
            }

        url = f"{self.api_url}/{phone_number_id}/messages"
        headers = {
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json"
        }
        media_payload: Dict[str, Any] = {}
        if media_id:
            media_payload["id"] = media_id
        elif media_url:
            media_payload["link"] = media_url

        if media_type == "document":
            effective_filename = filename or (media_url.split("/")[-1].split("?")[0] if media_url else "document.pdf")
            if not effective_filename or effective_filename == "link":
                effective_filename = "document.pdf"
            media_payload["filename"] = effective_filename

        if caption and media_type in ["image", "video", "document"]:
            media_payload["caption"] = caption

        payload = {
            "messaging_product": "whatsapp",
            "recipient_type": "individual",
            "to": clean_phone,
            "type": media_type,
            media_type: media_payload
        }
        async with httpx.AsyncClient(timeout=15.0) as client:
            response = await client.post(url, headers=headers, json=payload)
            if response.status_code >= 400:
                logger.error(f"WhatsApp API Media Error {response.status_code}: {response.text}")
                try:
                    err_json = response.json().get("error", {})
                    err_code = err_json.get("code")
                    err_msg = err_json.get("message", response.text)
                    if err_code == 190 or "expired" in err_msg.lower():
                        raise Exception("Meta WhatsApp Token Expired: Please generate a fresh token in Meta Developer Portal (developers.facebook.com) and update it in Settings.")
                    elif err_code == 131030 or "not in allowed list" in err_msg.lower():
                        raise Exception(f"Meta Sandbox: Recipient ({clean_phone}) is not in allowed test numbers list. Please add & verify under 'To' in Meta App Dashboard.")
                    elif err_code == 131047 or "24 hours" in err_msg.lower():
                        raise Exception("Template required: Outside 24-hour customer window.")
                    else:
                        raise Exception(f"WhatsApp Cloud API Media Error ({err_code}): {err_msg}")
                except Exception as inner_e:
                    if "Meta WhatsApp Token" in str(inner_e) or "Meta Sandbox" in str(inner_e) or "Template required" in str(inner_e):
                        raise inner_e
                    raise Exception(f"WhatsApp API Media Error: {response.text}")
            return response.json()

    def process_webhook_event(self, db: Session, payload: Dict[str, Any]) -> Dict[str, Any]:
        """
        Ingests official Meta WhatsApp Cloud API Webhook payload.
        Handles message delivery statuses and customer replies.
        Automatically updates opt-outs if STOP keywords are received.
        """
        results = {"processed_statuses": 0, "processed_messages": 0, "opt_outs": 0}
        entries = payload.get("entry", [])
        opt_out_keywords = {"STOP", "UNSUBSCRIBE", "REMOVE", "QUIT", "CANCEL", "ROK"}

        for entry in entries:
            changes = entry.get("changes", [])
            for change in changes:
                value = change.get("value", {})
                metadata = value.get("metadata", {})
                phone_number_id = metadata.get("phone_number_id")

                # 1. Process Status Updates (sent, delivered, read, failed)
                statuses = value.get("statuses", [])
                for status_item in statuses:
                    wamid = status_item.get("id")
                    st = status_item.get("status")  # sent, delivered, read, failed
                    timestamp = status_item.get("timestamp")

                    # Update Campaign Recipient if matched
                    from app.models.entities import CampaignRecipient, Campaign, RecipientStatus
                    recipient = db.query(CampaignRecipient).filter(CampaignRecipient.whatsapp_message_id == wamid).first()
                    if recipient:
                        if st == "delivered" and recipient.status != RecipientStatus.READ:
                            recipient.status = RecipientStatus.DELIVERED
                            recipient.delivered_at = datetime.utcnow()
                            if recipient.campaign:
                                recipient.campaign.delivered_count += 1
                        elif st == "read":
                            recipient.status = RecipientStatus.READ
                            recipient.read_at = datetime.utcnow()
                            if recipient.campaign:
                                recipient.campaign.read_count += 1
                        elif st == "failed":
                            recipient.status = RecipientStatus.FAILED
                            errors = status_item.get("errors", [])
                            err_msg = errors[0].get("title", "Delivery Failed") if errors else "API Error"
                            recipient.error_message = err_msg
                            if recipient.campaign:
                                recipient.campaign.failed_count += 1
                        db.commit()

                    # Update Chat Message if matched
                    msg = db.query(Message).filter(Message.whatsapp_message_id == wamid).first()
                    if msg:
                        msg.status = st.upper()
                        db.commit()

                    results["processed_statuses"] += 1

                # 2. Process Incoming Customer Messages
                messages = value.get("messages", [])
                contacts = value.get("contacts", [])
                sender_profile_name = contacts[0].get("profile", {}).get("name", "") if contacts else ""

                for msg_item in messages:
                    from_phone = "+" + msg_item.get("from", "").lstrip("+")
                    msg_id = msg_item.get("id")
                    msg_type_str = msg_item.get("type", "text")
                    text_body = ""
                    media_url = None

                    if msg_type_str == "text":
                        text_body = msg_item.get("text", {}).get("body", "")
                    elif msg_type_str in ["image", "video", "document", "audio"]:
                        media_data = msg_item.get(msg_type_str, {})
                        media_url = media_data.get("link") or media_data.get("id")
                        text_body = media_data.get("caption", f"[{msg_type_str.upper()}]")
                    elif msg_type_str == "button":
                        text_body = msg_item.get("button", {}).get("text", "")
                    elif msg_type_str == "interactive":
                        interactive = msg_item.get("interactive", {})
                        if "button_reply" in interactive:
                            text_body = interactive["button_reply"].get("title", "")
                        elif "list_reply" in interactive:
                            text_body = interactive["list_reply"].get("title", "")

                    # Find or auto-create contact matching any representation of the phone number
                    clean_digits = re.sub(r'\D', '', from_phone)
                    contact = db.query(Contact).filter(
                        (Contact.phone_number == from_phone) |
                        (Contact.whatsapp_number == from_phone) |
                        (Contact.phone_number == f"+{clean_digits}") |
                        (Contact.phone_number == clean_digits)
                    ).first()

                    # Fallback to suffix match (last 9 digits) for imported leads with various formatting
                    if not contact and len(clean_digits) >= 9:
                        contact = db.query(Contact).filter(
                            Contact.phone_number.like(f"%{clean_digits[-9:]}%")
                        ).first()

                    if not contact:
                        wa_acc = db.query(WhatsAppAccount).filter(WhatsAppAccount.phone_number_id == phone_number_id).first()
                        biz_id = wa_acc.business_id if wa_acc else 1
                        contact = Contact(
                            business_id=biz_id,
                            first_name=sender_profile_name or "Customer",
                            last_name="",
                            phone_number=from_phone,
                            whatsapp_number=from_phone,
                            lead_status="New Lead",
                            customer_status="Lead",
                            opt_in_status=OptStatus.OPTED_IN
                        )
                        db.add(contact)
                        db.commit()
                        db.refresh(contact)

                    logger.info(f"[INCOMING WHATSAPP MESSAGE] From: {from_phone} ({contact.first_name}) Body: '{text_body}'")
                    print(f"\n======================================================\n[INCOMING WHATSAPP MESSAGE RECEIVED]\nFrom: {from_phone} ({contact.first_name})\nMessage: {text_body}\n======================================================\n", flush=True)

                    contact.last_interaction = datetime.utcnow()

                    # Check for Opt-Out Keywords
                    upper_body = text_body.strip().upper()
                    if upper_body in opt_out_keywords:
                        contact.opt_in_status = OptStatus.OPTED_OUT
                        contact.opt_out_date = datetime.utcnow()
                        contact.opt_out_reason = f"Received opt-out keyword: {upper_body}"

                        # Record in opt_outs suppression table
                        opt_record = OptOut(
                            business_id=contact.business_id,
                            phone_number=from_phone,
                            reason="Unsubscribe keyword received via webhook",
                            keyword=upper_body,
                            opt_out_date=datetime.utcnow()
                        )
                        db.add(opt_record)
                        results["opt_outs"] += 1

                    # Get or Create Conversation
                    conversation = db.query(Conversation).filter(
                        Conversation.contact_id == contact.id
                    ).first()
                    if not conversation:
                        conversation = Conversation(
                            business_id=contact.business_id,
                            contact_id=contact.id,
                            status=ConversationStatus.OPEN,
                            unread_count=1,
                            last_message=text_body,
                            last_message_at=datetime.utcnow()
                        )
                        db.add(conversation)
                        db.commit()
                        db.refresh(conversation)
                    else:
                        conversation.unread_count += 1
                        conversation.last_message = text_body
                        conversation.last_message_at = datetime.utcnow()
                        if conversation.status == ConversationStatus.RESOLVED:
                            conversation.status = ConversationStatus.OPEN

                    # Save Message
                    new_msg = Message(
                        conversation_id=conversation.id,
                        direction=MessageDirection.INBOUND,
                        message_type=MessageType(msg_type_str) if msg_type_str in [e.value for e in MessageType] else MessageType.TEXT,
                        body=text_body,
                        media_url=media_url,
                        whatsapp_message_id=msg_id,
                        status="DELIVERED",
                        created_at=datetime.utcnow()
                    )
                    db.add(new_msg)
                    db.commit()

                    results["processed_messages"] += 1

        return results

    async def create_meta_template(
        self,
        waba_id: str,
        name: str,
        category: str,
        content: str,
        language: str = "en_US",
        header_text: Optional[str] = None,
        footer_text: Optional[str] = None,
        token: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Submits a message template directly to Meta WhatsApp Business Management (WhatsApp Manager).
        POST https://graph.facebook.com/v19.0/{waba_id}/message_templates
        """
        access_token = token or self.default_token
        clean_name = re.sub(r'[^a-z0-9_]', '_', name.lower().strip())

        cat_upper = category.upper()
        if "MARKET" in cat_upper:
            meta_category = "MARKETING"
        elif "UTIL" in cat_upper:
            meta_category = "UTILITY"
        elif "AUTH" in cat_upper:
            meta_category = "AUTHENTICATION"
        else:
            meta_category = "MARKETING"

        var_pattern = re.compile(r'\{\{\s*([a-zA-Z0-9_]+)\s*\}\}')
        found_vars = var_pattern.findall(content)

        body_text = content
        example_values = []
        for idx, v in enumerate(found_vars, start=1):
            body_text = re.sub(r'\{\{\s*' + re.escape(v) + r'\s*\}\}', f'{{{{{idx}}}}}', body_text, count=1)
            example_values.append("John" if "name" in v.lower() else "Acme")

        components = []
        if header_text:
            components.append({
                "type": "HEADER",
                "format": "TEXT",
                "text": header_text
            })

        body_comp: Dict[str, Any] = {
            "type": "BODY",
            "text": body_text
        }
        if example_values:
            body_comp["example"] = {
                "body_text": [example_values]
            }
        components.append(body_comp)

        if footer_text:
            components.append({
                "type": "FOOTER",
                "text": footer_text
            })

        if self._is_mock(access_token):
            mock_id = f"meta_tmpl_{uuid.uuid4().hex[:12]}"
            logger.info(f"[MOCK Meta Graph API] Created template '{clean_name}' under '{meta_category}' in WhatsApp Manager (ID: {mock_id})")
            return {
                "id": mock_id,
                "status": "APPROVED",
                "category": meta_category,
                "name": clean_name
            }

        url = f"{self.api_url}/{waba_id}/message_templates"
        headers = {
            "Authorization": f"Bearer {access_token}",
            "Content-Type": "application/json"
        }
        payload = {
            "name": clean_name,
            "category": meta_category,
            "language": language,
            "components": components
        }
        async with httpx.AsyncClient(timeout=20.0) as client:
            response = await client.post(url, headers=headers, json=payload)
            if response.status_code >= 400:
                logger.error(f"Meta Create Template Error ({response.status_code}): {response.text}")
                raise Exception(f"Meta Template Error: {response.text}")
            return response.json()

    async def fetch_meta_templates(
        self,
        waba_id: str,
        token: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        """
        Retrieves live templates and approval statuses from Meta WhatsApp Business Management.
        GET /{waba_id}/message_templates
        """
        access_token = token or self.default_token
        if self._is_mock(access_token):
            return []

        url = f"{self.api_url}/{waba_id}/message_templates?fields=name,status,category,language,components,id&limit=100"
        headers = {
            "Authorization": f"Bearer {access_token}"
        }
        try:
            async with httpx.AsyncClient(timeout=20.0) as client:
                response = await client.get(url, headers=headers)
                if response.status_code == 200:
                    return response.json().get("data", [])
                logger.warning(f"Meta Fetch Templates returned {response.status_code}: {response.text}")
                return []
        except Exception as e:
            logger.warning(f"Failed to fetch templates from Meta: {e}")
            return []

whatsapp_service = WhatsAppService()
