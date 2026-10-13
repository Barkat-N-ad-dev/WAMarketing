import json
import re
import logging
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List, Optional

from app.database import get_db
from app.models.entities import MessageTemplate, User, WhatsAppAccount
from app.schemas.schemas import TemplateCreate, TemplateUpdate, TemplateResponse
from app.api.deps import get_current_user
from app.services.whatsapp_service import whatsapp_service

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/templates", tags=["Templates"])

@router.get("", response_model=List[TemplateResponse])
def list_templates(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return db.query(MessageTemplate).filter(
        MessageTemplate.business_id == current_user.business_id
    ).order_by(MessageTemplate.id.desc()).all()

@router.post("", response_model=TemplateResponse, status_code=status.HTTP_201_CREATED)
async def create_template(
    template_in: TemplateCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    # Normalize name to Meta compliant lowercase snake_case
    clean_name = re.sub(r'[^a-z0-9_]', '_', template_in.name.lower().strip())
    
    # Normalize category: Marketing vs Utility
    cat_upper = template_in.category.upper()
    if "UTIL" in cat_upper:
        clean_category = "Utility"
        meta_category = "UTILITY"
    elif "AUTH" in cat_upper:
        clean_category = "Authentication"
        meta_category = "AUTHENTICATION"
    else:
        clean_category = "Marketing"
        meta_category = "MARKETING"

    # Status determination
    initial_status = "APPROVED" if template_in.template_type == "Local App Template" else "PENDING"
    meta_id = None

    # If submitting for WhatsApp Business Manager approval
    if template_in.template_type == "WhatsApp Approved Template":
        # Check for active connected WhatsApp Account (with fallback to any account with access token)
        wa_account = db.query(WhatsAppAccount).filter(
            WhatsAppAccount.business_id == current_user.business_id,
            WhatsAppAccount.status == "CONNECTED"
        ).first() or db.query(WhatsAppAccount).filter(
            WhatsAppAccount.access_token.isnot(None)
        ).first()

        acc_token = wa_account.access_token if wa_account else whatsapp_service.default_token
        waba_target = (wa_account.waba_id if wa_account and wa_account.waba_id else None) or (wa_account.phone_number_id if wa_account else None) or whatsapp_service.default_waba_id

        if acc_token and waba_target:
            try:
                meta_res = await whatsapp_service.create_meta_template(
                    waba_id=waba_target,
                    name=clean_name,
                    category=meta_category,
                    content=template_in.content,
                    language=template_in.language or "en_US",
                    header_text=template_in.header_text,
                    footer_text=template_in.footer_text,
                    button_text=template_in.button_text,
                    button_url=template_in.button_url,
                    token=acc_token
                )
                meta_id = str(meta_res.get("id", ""))
                initial_status = meta_res.get("status", "PENDING")
                logger.info(f"Template '{clean_name}' successfully submitted to Meta (ID: {meta_id}, Status: {initial_status})")
            except Exception as e:
                logger.error(f"Meta template submission notice: {e}")
                initial_status = "PENDING"
        else:
            initial_status = "PENDING"

    template = MessageTemplate(
        business_id=current_user.business_id,
        name=clean_name,
        category=clean_category,
        language=template_in.language or "en_US",
        content=template_in.content,
        header_text=template_in.header_text,
        footer_text=template_in.footer_text,
        button_text=template_in.button_text,
        button_url=template_in.button_url,
        template_type=template_in.template_type,
        status=initial_status,
        meta_template_id=meta_id,
        variables=json.dumps(template_in.variables or [])
    )
    db.add(template)
    db.commit()
    db.refresh(template)
    return template

@router.post("/sync-meta", response_model=List[TemplateResponse])
async def sync_meta_templates(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Pulls live approved templates directly from Meta WhatsApp Business Management
    and updates their approval statuses in the database.
    """
    wa_account = db.query(WhatsAppAccount).filter(
        WhatsAppAccount.business_id == current_user.business_id,
        WhatsAppAccount.status == "CONNECTED"
    ).first() or db.query(WhatsAppAccount).filter(
        WhatsAppAccount.access_token.isnot(None)
    ).first()

    acc_token = wa_account.access_token if wa_account else whatsapp_service.default_token
    waba_target = (wa_account.waba_id if wa_account and wa_account.waba_id else None) or (wa_account.phone_number_id if wa_account else None) or whatsapp_service.default_waba_id

    if acc_token and waba_target:
        meta_templates = await whatsapp_service.fetch_meta_templates(waba_target, token=acc_token)
        for mt in meta_templates:
            mt_name = mt.get("name")
            mt_status = mt.get("status", "APPROVED")
            mt_category = mt.get("category", "MARKETING").capitalize()
            mt_lang = mt.get("language", "en_US")
            mt_id = str(mt.get("id", ""))

            # Extract body text and buttons from components if available
            body_text = ""
            btn_text = None
            btn_url = None
            for comp in mt.get("components", []):
                if comp.get("type") == "BODY":
                    body_text = comp.get("text", "")
                elif comp.get("type") == "BUTTONS":
                    buttons = comp.get("buttons", [])
                    if buttons:
                        first_btn = buttons[0]
                        btn_text = first_btn.get("text")
                        btn_url = first_btn.get("url")

            existing = db.query(MessageTemplate).filter(
                MessageTemplate.business_id == current_user.business_id,
                MessageTemplate.name == mt_name
            ).first()

            if existing:
                existing.status = mt_status
                existing.category = mt_category
                existing.meta_template_id = mt_id
                if body_text and not existing.content:
                    existing.content = body_text
                if btn_text:
                    existing.button_text = btn_text
                if btn_url:
                    existing.button_url = btn_url
            else:
                new_t = MessageTemplate(
                    business_id=current_user.business_id,
                    name=mt_name,
                    category=mt_category,
                    language=mt_lang,
                    content=body_text or f"Meta pre-approved template {mt_name}",
                    button_text=btn_text,
                    button_url=btn_url,
                    template_type="WhatsApp Approved Template",
                    status=mt_status,
                    meta_template_id=mt_id,
                    variables=json.dumps([])
                )
                db.add(new_t)
        db.commit()

    return db.query(MessageTemplate).filter(
        MessageTemplate.business_id == current_user.business_id
    ).order_by(MessageTemplate.id.desc()).all()

@router.get("/{template_id}", response_model=TemplateResponse)
def get_template(template_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    tmpl = db.query(MessageTemplate).filter(
        MessageTemplate.id == template_id,
        MessageTemplate.business_id == current_user.business_id
    ).first()
    if not tmpl:
        raise HTTPException(status_code=404, detail="Template not found")
    return tmpl

@router.put("/{template_id}", response_model=TemplateResponse)
def update_template(
    template_id: int,
    template_in: TemplateUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    tmpl = db.query(MessageTemplate).filter(
        MessageTemplate.id == template_id,
        MessageTemplate.business_id == current_user.business_id
    ).first()
    if not tmpl:
        raise HTTPException(status_code=404, detail="Template not found")

    update_dict = template_in.model_dump(exclude_unset=True)
    if "variables" in update_dict and update_dict["variables"] is not None:
        tmpl.variables = json.dumps(update_dict["variables"])
        del update_dict["variables"]

    for k, v in update_dict.items():
        setattr(tmpl, k, v)

    db.commit()
    db.refresh(tmpl)
    return tmpl

@router.delete("/{template_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_template(template_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    tmpl = db.query(MessageTemplate).filter(
        MessageTemplate.id == template_id,
        MessageTemplate.business_id == current_user.business_id
    ).first()
    if not tmpl:
        raise HTTPException(status_code=404, detail="Template not found")
    db.delete(tmpl)
    db.commit()
    return None
