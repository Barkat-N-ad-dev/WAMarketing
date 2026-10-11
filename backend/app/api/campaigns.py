import asyncio
import json
from fastapi import APIRouter, Depends, HTTPException, BackgroundTasks, status
from sqlalchemy.orm import Session
from typing import List
from datetime import datetime

from app.database import get_db
from app.models.entities import Campaign, CampaignStatus, User, Contact, WhatsAppAccount
from app.schemas.schemas import (
    CampaignCreate, CampaignUpdate, CampaignResponse, CampaignDetailResponse,
    PersonalizePreviewRequest, PersonalizePreviewResponse
)
from app.api.deps import get_current_user
from app.services.campaign_service import campaign_service

router = APIRouter(prefix="/campaigns", tags=["Campaigns"])

@router.get("", response_model=List[CampaignResponse])
def list_campaigns(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return db.query(Campaign).filter(
        Campaign.business_id == current_user.business_id
    ).order_by(Campaign.id.desc()).all()

@router.post("", response_model=CampaignResponse, status_code=status.HTTP_201_CREATED)
def create_campaign(
    campaign_in: CampaignCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    campaign = Campaign(
        business_id=current_user.business_id,
        whatsapp_account_id=campaign_in.whatsapp_account_id,
        template_id=campaign_in.template_id,
        media_id=campaign_in.media_id,
        name=campaign_in.name,
        status=CampaignStatus.SCHEDULED if campaign_in.scheduled_at else CampaignStatus.DRAFT,
        audience_filter=json.dumps(campaign_in.audience_filter or {}),
        message_content=campaign_in.message_content,
        media_url=campaign_in.media_url,
        scheduled_at=campaign_in.scheduled_at,
        time_zone=campaign_in.time_zone
    )
    db.add(campaign)
    db.commit()
    db.refresh(campaign)
    return campaign

@router.get("/{campaign_id}", response_model=CampaignDetailResponse)
def get_campaign(campaign_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    campaign = db.query(Campaign).filter(
        Campaign.id == campaign_id,
        Campaign.business_id == current_user.business_id
    ).first()
    if not campaign:
        raise HTTPException(status_code=404, detail="Campaign not found")

    recipients_sample = []
    for r in campaign.recipients[:50]:
        recipients_sample.append({
            "id": r.id,
            "contact_name": f"{r.contact.first_name} {r.contact.last_name}" if r.contact else "",
            "phone_number": r.phone_number,
            "status": r.status.value,
            "sent_at": r.sent_at.isoformat() if r.sent_at else None,
            "delivered_at": r.delivered_at.isoformat() if r.delivered_at else None,
            "error_message": r.error_message
        })

    resp = CampaignDetailResponse.model_validate(campaign)
    resp.recipients_sample = recipients_sample
    return resp

@router.post("/{campaign_id}/launch")
def launch_campaign(
    campaign_id: int,
    background_tasks: BackgroundTasks,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    campaign = db.query(Campaign).filter(
        Campaign.id == campaign_id,
        Campaign.business_id == current_user.business_id
    ).first()
    if not campaign:
        raise HTTPException(status_code=404, detail="Campaign not found")

    if campaign.status in [CampaignStatus.RUNNING, CampaignStatus.COMPLETED]:
        raise HTTPException(status_code=400, detail=f"Campaign is already {campaign.status.value}")

    # Launch execution asynchronously
    background_tasks.add_task(campaign_service.execute_campaign, db, campaign.id)
    return {"message": "Campaign launched successfully. Messages are being dispatched."}

@router.post("/{campaign_id}/pause")
def pause_campaign(campaign_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    campaign = db.query(Campaign).filter(Campaign.id == campaign_id, Campaign.business_id == current_user.business_id).first()
    if not campaign:
        raise HTTPException(status_code=404, detail="Campaign not found")
    campaign.status = CampaignStatus.PAUSED
    db.commit()
    return {"message": "Campaign paused."}

@router.post("/{campaign_id}/cancel")
def cancel_campaign(campaign_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    campaign = db.query(Campaign).filter(Campaign.id == campaign_id, Campaign.business_id == current_user.business_id).first()
    if not campaign:
        raise HTTPException(status_code=404, detail="Campaign not found")
    campaign.status = CampaignStatus.CANCELLED
    db.commit()
    return {"message": "Campaign cancelled."}

@router.post("/preview-personalization", response_model=PersonalizePreviewResponse)
def preview_personalization(req: PersonalizePreviewRequest):
    # Dummy contact representation for preview
    class DummyContact:
        def __init__(self, data):
            self.first_name = data.get("first_name", "Alex")
            self.last_name = data.get("last_name", "Smith")
            self.phone_number = data.get("phone_number", "+1234567890")
            self.company = data.get("company", "Apex Global")
            self.city = data.get("city", "Dubai")
            self.country = data.get("country", "UAE")
            self.lead_status = data.get("lead_status", "Hot Lead")

    dummy = DummyContact(req.sample_contact)
    personalized = campaign_service.personalize_text(req.message_template, dummy, req.sample_contact)
    variables = campaign_service.extract_variables(req.message_template)

    return PersonalizePreviewResponse(
        original=req.message_template,
        personalized=personalized,
        detected_variables=variables
    )
