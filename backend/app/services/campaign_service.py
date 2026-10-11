import re
import asyncio
import json
import logging
from datetime import datetime
from typing import List, Dict, Any, Optional
from sqlalchemy.orm import Session
from app.models.entities import (
    Campaign, CampaignRecipient, Contact, OptStatus,
    CampaignStatus, RecipientStatus, WhatsAppAccount, OptOut
)
from app.services.whatsapp_service import whatsapp_service
from app.config import settings

logger = logging.getLogger(__name__)

class CampaignService:
    @staticmethod
    def personalize_text(template_text: str, contact: Contact, extra_vars: Optional[Dict[str, Any]] = None) -> str:
        """
        Replaces variables in message content with contact details.
        Supports: {{first_name}}, {{last_name}}, {{company}}, {{city}}, {{country}}, {{phone_number}}, etc.
        """
        context = {
            "first_name": contact.first_name or "",
            "last_name": contact.last_name or "",
            "name": f"{contact.first_name} {contact.last_name}".strip(),
            "phone": contact.phone_number,
            "phone_number": contact.phone_number,
            "company": contact.company or "valued customer",
            "city": contact.city or "your area",
            "country": contact.country or "",
            "lead_status": contact.lead_status or "",
        }
        if extra_vars:
            context.update(extra_vars)

        def replacer(match):
            var_name = match.group(1).strip()
            return str(context.get(var_name, f"{{{{{var_name}}}}}"))

        # Matches {{variable_name}}
        return re.sub(r'\{\{\s*([a-zA-Z0-9_]+)\s*\}\}', replacer, template_text)

    @staticmethod
    def extract_variables(text: str) -> List[str]:
        return list(set(re.findall(r'\{\{\s*([a-zA-Z0-9_]+)\s*\}\}', text)))

    @classmethod
    async def execute_campaign(cls, db: Session, campaign_id: int):
        """
        Executes campaign queue safely:
        1. Validates audience & filters
        2. Filters out OPTED_OUT contacts and global suppression list
        3. Generates personalized messages
        4. Sends via WhatsApp Cloud API with rate pacing
        5. Updates campaign delivery statistics
        """
        campaign = db.query(Campaign).filter(Campaign.id == campaign_id).first()
        if not campaign:
            logger.error(f"Campaign {campaign_id} not found for execution")
            return

        # Fetch sender WhatsApp account
        wa_account = campaign.whatsapp_account
        if not wa_account:
            wa_account = db.query(WhatsAppAccount).filter(
                WhatsAppAccount.business_id == campaign.business_id,
                WhatsAppAccount.status == "CONNECTED"
            ).first()

        phone_number_id = wa_account.phone_number_id if wa_account else settings.WHATSAPP_PHONE_NUMBER_ID
        access_token = wa_account.access_token if wa_account else settings.WHATSAPP_ACCESS_TOKEN

        campaign.status = CampaignStatus.RUNNING
        campaign.started_at = datetime.utcnow()
        db.commit()

        # Build Audience Query
        query = db.query(Contact).filter(
            Contact.business_id == campaign.business_id,
            Contact.opt_in_status == OptStatus.OPTED_IN  # STRICT OPT-IN COMPLIANCE
        )

        filter_dict = {}
        if campaign.audience_filter:
            try:
                filter_dict = json.loads(campaign.audience_filter) if isinstance(campaign.audience_filter, str) else campaign.audience_filter
            except Exception:
                pass

        if filter_dict.get("country"):
            query = query.filter(Contact.country == filter_dict["country"])
        if filter_dict.get("city"):
            query = query.filter(Contact.city == filter_dict["city"])
        if filter_dict.get("lead_status"):
            query = query.filter(Contact.lead_status == filter_dict["lead_status"])
        if filter_dict.get("tag_ids"):
            from app.models.entities import Tag
            query = query.join(Contact.tags).filter(Tag.id.in_(filter_dict["tag_ids"]))

        contacts = query.all()
        # Global suppression check
        suppressed_phones = set(row[0] for row in db.query(OptOut.phone_number).filter(OptOut.business_id == campaign.business_id).all())
        eligible_contacts = [c for c in contacts if c.phone_number not in suppressed_phones]

        campaign.total_recipients = len(eligible_contacts)
        db.commit()

        for contact in eligible_contacts:
            personalized_msg = cls.personalize_text(campaign.message_content, contact)

            recipient = CampaignRecipient(
                campaign_id=campaign.id,
                contact_id=contact.id,
                phone_number=contact.phone_number,
                personalized_text=personalized_msg,
                status=RecipientStatus.PENDING
            )
            db.add(recipient)
            db.commit()
            db.refresh(recipient)

            try:
                # Dispatch through WhatsApp Cloud API
                if campaign.template:
                    # Send official template
                    params = [contact.first_name or "valued customer"]
                    resp = await whatsapp_service.send_template_message(
                        phone_number_id=phone_number_id,
                        to_phone=contact.phone_number,
                        template_name=campaign.template.name,
                        language_code=campaign.template.language or "en",
                        parameters=params,
                        token=access_token
                    )
                    # If an APK or media file is also attached to the campaign, send it directly
                    if campaign.media_url:
                        lower_m = campaign.media_url.lower()
                        media_type = "document" if any(lower_m.endswith(ext) for ext in [".apk", ".pdf", ".zip", ".docx"]) else ("audio" if any(lower_m.endswith(ext) for ext in [".mp3", ".ogg", ".wav", ".aac", ".m4a"]) else "image")
                        fname = campaign.media_url.split("/")[-1].split("?")[0]
                        await whatsapp_service.send_media_message(
                            phone_number_id=phone_number_id,
                            to_phone=contact.phone_number,
                            media_type=media_type,
                            media_url=campaign.media_url,
                            filename=fname or "file",
                            token=access_token
                        )
                elif campaign.media_url:
                    # Media message with caption
                    lower_m = campaign.media_url.lower()
                    media_type = "document" if any(lower_m.endswith(ext) for ext in [".apk", ".pdf", ".zip", ".docx"]) else ("audio" if any(lower_m.endswith(ext) for ext in [".mp3", ".ogg", ".wav", ".aac", ".m4a"]) else "image")
                    fname = campaign.media_url.split("/")[-1].split("?")[0]
                    resp = await whatsapp_service.send_media_message(
                        phone_number_id=phone_number_id,
                        to_phone=contact.phone_number,
                        media_type=media_type,
                        media_url=campaign.media_url,
                        filename=fname or "file",
                        caption=personalized_msg,
                        token=access_token
                    )
                else:
                    # Text message
                    resp = await whatsapp_service.send_text_message(
                        phone_number_id=phone_number_id,
                        to_phone=contact.phone_number,
                        message_text=personalized_msg,
                        token=access_token
                    )

                wamid = resp.get("messages", [{}])[0].get("id")
                recipient.status = RecipientStatus.SENT
                recipient.whatsapp_message_id = wamid
                recipient.sent_at = datetime.utcnow()
                campaign.sent_count += 1

            except Exception as e:
                logger.error(f"Error sending message to {contact.phone_number}: {e}")
                recipient.status = RecipientStatus.FAILED
                recipient.error_message = str(e)
                campaign.failed_count += 1

            db.commit()
            # Rate limiting interval (compliance with WhatsApp tier limits)
            await asyncio.sleep(settings.DEFAULT_RATE_LIMIT_PAUSE_SECONDS)

        campaign.status = CampaignStatus.COMPLETED
        campaign.completed_at = datetime.utcnow()
        db.commit()

campaign_service = CampaignService()
