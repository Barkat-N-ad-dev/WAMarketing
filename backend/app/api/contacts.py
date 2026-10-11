from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File, Form, status
from sqlalchemy.orm import Session
from sqlalchemy import or_
from typing import List, Optional
import json

from app.database import get_db
from app.models.entities import Contact, Tag, User, OptStatus, contact_tags, Lead, LeadStage
from app.schemas.schemas import (
    ContactCreate, ContactUpdate, ContactResponse, TagCreate, TagResponse,
    ContactBulkTag, ContactBulkDelete, ContactImportResult,
    ContactPreviewResponse, ContactConfirmRequest
)
from app.api.deps import get_current_user
from app.utils.phone_validator import sanitize_phone_number
from app.services.contact_service import contact_service
from app.services.automation_service import automation_engine

router = APIRouter(prefix="/contacts", tags=["Contacts"])

@router.get("", response_model=List[ContactResponse])
def list_contacts(
    page: int = Query(1, ge=1),
    limit: int = Query(50, ge=1, le=500),
    search: Optional[str] = None,
    tag_id: Optional[int] = None,
    country: Optional[str] = None,
    city: Optional[str] = None,
    lead_status: Optional[str] = None,
    opt_in_status: Optional[OptStatus] = None,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    query = db.query(Contact).filter(Contact.business_id == current_user.business_id)

    if search:
        search_fmt = f"%{search.strip()}%"
        query = query.filter(
            or_(
                Contact.first_name.ilike(search_fmt),
                Contact.last_name.ilike(search_fmt),
                Contact.phone_number.ilike(search_fmt),
                Contact.email.ilike(search_fmt),
                Contact.company.ilike(search_fmt),
                Contact.city.ilike(search_fmt)
            )
        )
    if tag_id:
        query = query.join(Contact.tags).filter(Tag.id == tag_id)
    if country:
        query = query.filter(Contact.country == country)
    if city:
        query = query.filter(Contact.city == city)
    if lead_status:
        query = query.filter(Contact.lead_status == lead_status)
    if opt_in_status:
        query = query.filter(Contact.opt_in_status == opt_in_status)

    offset = (page - 1) * limit
    return query.order_by(Contact.id.desc()).offset(offset).limit(limit).all()

@router.post("", response_model=ContactResponse, status_code=status.HTTP_201_CREATED)
def create_contact(
    contact_in: ContactCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    valid, clean_phone, reason = sanitize_phone_number(contact_in.phone_number)
    if not valid:
        raise HTTPException(status_code=400, detail=f"Invalid phone number: {reason}")

    # Duplicate check within business
    existing = db.query(Contact).filter(
        Contact.business_id == current_user.business_id,
        Contact.phone_number == clean_phone
    ).first()
    if existing:
        raise HTTPException(status_code=400, detail="A contact with this phone number already exists.")

    contact = Contact(
        business_id=current_user.business_id,
        first_name=contact_in.first_name,
        last_name=contact_in.last_name or "",
        phone_number=clean_phone,
        whatsapp_number=clean_phone,
        email=contact_in.email,
        company=contact_in.company,
        city=contact_in.city,
        country=contact_in.country,
        notes=contact_in.notes or "",
        lead_status=contact_in.lead_status or "New Lead",
        customer_status=contact_in.customer_status or "Lead",
        opt_in_status=contact_in.opt_in_status or OptStatus.OPTED_IN
    )

    if contact_in.tag_ids:
        tags = db.query(Tag).filter(Tag.id.in_(contact_in.tag_ids), Tag.business_id == current_user.business_id).all()
        contact.tags = tags

    db.add(contact)
    db.commit()
    db.refresh(contact)

    # Trigger automation for new_lead
    automation_engine.trigger_event(db, "new_lead", contact)

    return contact

@router.get("/{contact_id}", response_model=ContactResponse)
def get_contact(
    contact_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    contact = db.query(Contact).filter(Contact.id == contact_id, Contact.business_id == current_user.business_id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    return contact

@router.put("/{contact_id}", response_model=ContactResponse)
def update_contact(
    contact_id: int,
    contact_in: ContactUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    contact = db.query(Contact).filter(Contact.id == contact_id, Contact.business_id == current_user.business_id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")

    update_data = contact_in.model_dump(exclude_unset=True)
    if "phone_number" in update_data and update_data["phone_number"]:
        valid, clean_phone, reason = sanitize_phone_number(update_data["phone_number"])
        if not valid:
            raise HTTPException(status_code=400, detail=f"Invalid phone number: {reason}")
        contact.phone_number = clean_phone
        contact.whatsapp_number = clean_phone

    if "tag_ids" in update_data and update_data["tag_ids"] is not None:
        tags = db.query(Tag).filter(Tag.id.in_(update_data["tag_ids"]), Tag.business_id == current_user.business_id).all()
        contact.tags = tags

    for key in ["first_name", "last_name", "email", "company", "city", "country", "notes", "lead_status", "customer_status", "opt_in_status"]:
        if key in update_data:
            setattr(contact, key, update_data[key])

    db.commit()
    db.refresh(contact)
    return contact

@router.delete("/{contact_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_contact(
    contact_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    contact = db.query(Contact).filter(Contact.id == contact_id, Contact.business_id == current_user.business_id).first()
    if not contact:
        raise HTTPException(status_code=404, detail="Contact not found")
    db.delete(contact)
    db.commit()
    return None

@router.post("/bulk-tag")
def bulk_tag(
    data: ContactBulkTag,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    contacts = db.query(Contact).filter(Contact.id.in_(data.contact_ids), Contact.business_id == current_user.business_id).all()
    tags = db.query(Tag).filter(Tag.id.in_(data.tag_ids), Tag.business_id == current_user.business_id).all()

    for c in contacts:
        for t in tags:
            if t not in c.tags:
                c.tags.append(t)
    db.commit()
    return {"message": f"Successfully tagged {len(contacts)} contacts."}

@router.post("/bulk-delete")
def bulk_delete(
    data: ContactBulkDelete,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    deleted_count = db.query(Contact).filter(
        Contact.id.in_(data.contact_ids),
        Contact.business_id == current_user.business_id
    ).delete(synchronize_session=False)
    db.commit()
    return {"message": f"Successfully deleted {deleted_count} contacts."}

@router.post("/preview", response_model=ContactPreviewResponse)
async def preview_contacts(
    file: UploadFile = File(...),
    column_mapping_json: Optional[str] = Form(None),
    default_country_code: Optional[str] = Form(None),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Parses CSV, XLSX, XLS, or PDF files and returns a structured validation preview
    with extracted fields, duplicates detected, and invalid numbers highlighted.
    """
    mapping = {}
    if column_mapping_json:
        try:
            mapping = json.loads(column_mapping_json)
        except Exception:
            pass

    content = await file.read()
    preview = contact_service.parse_preview(
        db=db,
        business_id=current_user.business_id,
        file_bytes=content,
        filename=file.filename,
        column_mapping=mapping,
        default_country_code=default_country_code
    )
    return preview

@router.post("/confirm-import", response_model=ContactImportResult)
def confirm_import_contacts(
    req: ContactConfirmRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Imports user-confirmed contacts from preview table into database,
    assigns tags, and creates associated CRM leads without freezing.
    """
    tag = None
    if req.tag_name:
        tag = db.query(Tag).filter(Tag.name == req.tag_name, Tag.business_id == current_user.business_id).first()
        if not tag:
            tag = Tag(name=req.tag_name, business_id=current_user.business_id, color="#10B981")
            db.add(tag)
            db.commit()
            db.refresh(tag)

    existing_phones = set(
        p[0] for p in db.query(Contact.phone_number).filter(Contact.business_id == current_user.business_id).all()
    )

    imported = 0
    duplicates = 0
    invalid = 0
    errors = []

    for rec in req.records:
        if rec.status == "INVALID":
            invalid += 1
            continue

        valid, clean_phone, reason = sanitize_phone_number(rec.phone)
        if not valid:
            invalid += 1
            errors.append(f"{rec.name}: {reason}")
            continue

        if clean_phone in existing_phones:
            duplicates += 1
            continue

        existing_phones.add(clean_phone)
        full_name = rec.name.strip()
        parts = full_name.split(" ", 1)
        first_name = parts[0] if parts and parts[0] else "Lead"
        last_name = parts[1] if len(parts) > 1 else ""

        contact = Contact(
            business_id=current_user.business_id,
            first_name=first_name,
            last_name=last_name,
            phone_number=clean_phone,
            whatsapp_number=clean_phone,
            email=rec.email or None,
            company=rec.company or "Enterprise",
            city=rec.city or "Global",
            country=rec.country or "Global",
            lead_status="New Lead",
            customer_status="Lead",
            opt_in_status=OptStatus.OPTED_IN,
            notes=f"Confirmed import from {req.source or 'Preview'}"
        )
        if tag:
            contact.tags.append(tag)
        db.add(contact)
        db.flush()
        imported += 1

        lead = Lead(
            business_id=current_user.business_id,
            contact_id=contact.id,
            stage=LeadStage.NEW_LEAD,
            deal_value=25000.0,
            notes=f"Auto-created during bulk import ({req.source or 'Contacts'})"
        )
        db.add(lead)

    db.commit()

    return ContactImportResult(
        total_records=len(req.records),
        imported=imported,
        duplicates=duplicates,
        invalid=invalid,
        skipped=len(req.records) - imported,
        errors=errors[:20]
    )

@router.post("/import", response_model=ContactImportResult)
async def import_contacts(
    file: UploadFile = File(...),
    column_mapping_json: Optional[str] = Form(None),
    default_country_code: Optional[str] = Form(None),
    tag_name: Optional[str] = Form(None),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    mapping = {}
    if column_mapping_json:
        try:
            mapping = json.loads(column_mapping_json)
        except Exception:
            pass

    content = await file.read()
    result = contact_service.parse_and_import(
        db=db,
        business_id=current_user.business_id,
        file_bytes=content,
        filename=file.filename,
        column_mapping=mapping,
        default_country_code=default_country_code,
        tag_name=tag_name
    )
    return result

# Tags API
@router.get("/tags/list", response_model=List[TagResponse])
def list_tags(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return db.query(Tag).filter(Tag.business_id == current_user.business_id).all()

@router.post("/tags/create", response_model=TagResponse)
def create_tag(tag_in: TagCreate, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    existing = db.query(Tag).filter(Tag.name == tag_in.name, Tag.business_id == current_user.business_id).first()
    if existing:
        return existing
    tag = Tag(business_id=current_user.business_id, name=tag_in.name, color=tag_in.color)
    db.add(tag)
    db.commit()
    db.refresh(tag)
    return tag
