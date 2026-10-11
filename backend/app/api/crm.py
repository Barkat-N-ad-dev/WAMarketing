from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List, Dict, Any

from app.database import get_db
from app.models.entities import Lead, Contact, User, LeadStage
from app.schemas.schemas import LeadCreate, LeadUpdate, LeadResponse
from app.api.deps import get_current_user

router = APIRouter(prefix="/crm", tags=["CRM"])

@router.get("/leads", response_model=List[LeadResponse])
def list_leads(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    leads = db.query(Lead).filter(Lead.business_id == current_user.business_id).all()
    results = []
    for l in leads:
        c_name = f"{l.contact.first_name} {l.contact.last_name}".strip() if l.contact else "Lead"
        phone = l.contact.phone_number if l.contact else ""
        comp = l.contact.company if l.contact else None
        results.append(LeadResponse(
            id=l.id,
            contact_id=l.contact_id,
            contact_name=c_name,
            phone_number=phone,
            company=comp,
            stage=l.stage,
            deal_value=l.deal_value,
            notes=l.notes or "",
            next_follow_up=l.next_follow_up,
            created_at=l.created_at,
            updated_at=l.updated_at
        ))
    return results

@router.post("/leads", response_model=LeadResponse, status_code=status.HTTP_201_CREATED)
def create_lead(lead_in: LeadCreate, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    contact = db.query(Contact).filter(Contact.id == lead_in.contact_id, Contact.business_id == current_user.business_id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")

    existing = db.query(Lead).filter(Lead.contact_id == lead_in.contact_id).first()
    if existing:
        raise HTTPException(status_code=400, detail="Lead entry already exists for this contact")

    lead = Lead(
        business_id=current_user.business_id,
        contact_id=lead_in.contact_id,
        stage=lead_in.stage,
        deal_value=lead_in.deal_value,
        notes=lead_in.notes or "",
        next_follow_up=lead_in.next_follow_up
    )
    contact.lead_status = lead_in.stage.value
    db.add(lead)
    db.commit()
    db.refresh(lead)

    c_name = f"{contact.first_name} {contact.last_name}".strip()
    return LeadResponse(
        id=lead.id,
        contact_id=lead.contact_id,
        contact_name=c_name,
        phone_number=contact.phone_number,
        company=contact.company,
        stage=lead.stage,
        deal_value=lead.deal_value,
        notes=lead.notes,
        next_follow_up=lead.next_follow_up,
        created_at=lead.created_at,
        updated_at=lead.updated_at
    )

@router.put("/leads/{lead_id}", response_model=LeadResponse)
def update_lead(
    lead_id: int,
    lead_in: LeadUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    lead = db.query(Lead).filter(Lead.id == lead_id, Lead.business_id == current_user.business_id).first()
    if not lead:
        raise HTTPException(status_code=404, detail="Lead not found")

    update_data = lead_in.model_dump(exclude_unset=True)
    for k, v in update_data.items():
        setattr(lead, k, v)

    if lead_in.stage and lead.contact:
        lead.contact.lead_status = lead_in.stage.value
        if lead_in.stage == LeadStage.CUSTOMER:
            lead.contact.customer_status = "Customer"

    db.commit()
    db.refresh(lead)

    c_name = f"{lead.contact.first_name} {lead.contact.last_name}".strip() if lead.contact else "Lead"
    phone = lead.contact.phone_number if lead.contact else ""
    return LeadResponse(
        id=lead.id,
        contact_id=lead.contact_id,
        contact_name=c_name,
        phone_number=phone,
        company=lead.contact.company if lead.contact else None,
        stage=lead.stage,
        deal_value=lead.deal_value,
        notes=lead.notes,
        next_follow_up=lead.next_follow_up,
        created_at=lead.created_at,
        updated_at=lead.updated_at
    )
