from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List, Optional
from pydantic import BaseModel

from app.database import get_db
from app.models.entities import WhatsAppAccount, Business, User
from app.schemas.schemas import WhatsAppAccountCreate, WhatsAppAccountResponse, BusinessResponse
from app.api.deps import get_current_user
from app.services.whatsapp_service import whatsapp_service
from app.config import settings

router = APIRouter(prefix="/settings", tags=["Settings"])

class VerifyWhatsAppCredentialsRequest(BaseModel):
    phone_number_id: str
    access_token: Optional[str] = None

class VerifyWhatsAppCredentialsResponse(BaseModel):
    valid: bool
    phone_number_id: str
    display_phone_number: Optional[str] = None
    verified_name: Optional[str] = None
    quality_rating: Optional[str] = None
    code_verification_status: Optional[str] = None
    error: Optional[str] = None

@router.post("/whatsapp-accounts/verify-credentials", response_model=VerifyWhatsAppCredentialsResponse)
async def verify_whatsapp_credentials(
    req: VerifyWhatsAppCredentialsRequest,
    current_user: User = Depends(get_current_user)
):
    """
    Validates WhatsApp Phone Number ID and Access Token against Meta Graph API
    in real time without requiring saving first.
    """
    token = req.access_token or settings.WHATSAPP_ACCESS_TOKEN
    details = await whatsapp_service.fetch_phone_number_details(
        phone_number_id=req.phone_number_id,
        token=token
    )
    if not details.get("display_phone_number") and details.get("quality_rating") == "UNKNOWN":
        return VerifyWhatsAppCredentialsResponse(
            valid=False,
            phone_number_id=req.phone_number_id,
            error="Could not authenticate with Meta Graph API. Please verify Phone Number ID and Access Token."
        )
    return VerifyWhatsAppCredentialsResponse(
        valid=True,
        phone_number_id=req.phone_number_id,
        display_phone_number=details.get("display_phone_number"),
        verified_name=details.get("verified_name"),
        quality_rating=details.get("quality_rating"),
        code_verification_status=details.get("code_verification_status")
    )

@router.get("/whatsapp-accounts", response_model=List[WhatsAppAccountResponse])
def list_whatsapp_accounts(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    accounts = db.query(WhatsAppAccount).filter(
        WhatsAppAccount.business_id == current_user.business_id
    ).all()
    # Note: access_token is never returned in response schema
    return accounts

@router.post("/whatsapp-accounts", response_model=WhatsAppAccountResponse, status_code=status.HTTP_201_CREATED)
async def connect_whatsapp_account(
    acc_in: WhatsAppAccountCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    token = acc_in.access_token or settings.WHATSAPP_ACCESS_TOKEN
    
    # Query Meta Graph API to verify credentials and fetch verified account & profile metadata
    meta_profile = await whatsapp_service.fetch_complete_account_profile(
        phone_number_id=acc_in.phone_number_id,
        token=token
    )

    # Check if this is the first account or should be default
    existing_count = db.query(WhatsAppAccount).filter(WhatsAppAccount.business_id == current_user.business_id).count()
    should_be_default = acc_in.is_default or (existing_count == 0)

    if should_be_default:
        db.query(WhatsAppAccount).filter(WhatsAppAccount.business_id == current_user.business_id).update({"is_default": False})

    acc = WhatsAppAccount(
        business_id=current_user.business_id,
        business_name=acc_in.business_name,
        phone_number=meta_profile.get("display_phone_number") or acc_in.phone_number,
        waba_id=acc_in.waba_id,
        phone_number_id=acc_in.phone_number_id,
        access_token=token,
        status="CONNECTED",
        is_default=should_be_default,
        profile_name=meta_profile.get("verified_name") or acc_in.business_name,
        profile_picture_url=meta_profile.get("profile_picture_url"),
        quality_rating=meta_profile.get("quality_rating", "GREEN"),
        code_verification_status=meta_profile.get("code_verification_status", "VERIFIED"),
        about=meta_profile.get("about"),
        vertical=meta_profile.get("vertical"),
        websites=meta_profile.get("websites")
    )
    db.add(acc)
    db.commit()
    db.refresh(acc)
    return acc

@router.post("/whatsapp-accounts/{account_id}/sync", response_model=WhatsAppAccountResponse)
async def sync_whatsapp_account_profile(
    account_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    acc = db.query(WhatsAppAccount).filter(
        WhatsAppAccount.id == account_id,
        WhatsAppAccount.business_id == current_user.business_id
    ).first()
    if not acc:
        raise HTTPException(status_code=404, detail="WhatsApp account not found")

    meta_profile = await whatsapp_service.fetch_complete_account_profile(
        phone_number_id=acc.phone_number_id,
        token=acc.access_token
    )

    if meta_profile.get("verified_name"):
        acc.profile_name = meta_profile.get("verified_name")
    if meta_profile.get("display_phone_number"):
        acc.phone_number = meta_profile.get("display_phone_number")
    acc.quality_rating = meta_profile.get("quality_rating", acc.quality_rating or "GREEN")
    acc.code_verification_status = meta_profile.get("code_verification_status", acc.code_verification_status or "VERIFIED")
    if meta_profile.get("profile_picture_url"):
        acc.profile_picture_url = meta_profile.get("profile_picture_url")
    if meta_profile.get("about"):
        acc.about = meta_profile.get("about")
    if meta_profile.get("vertical"):
        acc.vertical = meta_profile.get("vertical")
    if meta_profile.get("websites"):
        acc.websites = meta_profile.get("websites")

    db.commit()
    db.refresh(acc)
    return acc

@router.post("/whatsapp-accounts/{account_id}/set-default", response_model=WhatsAppAccountResponse)
def set_default_whatsapp_account(
    account_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    acc = db.query(WhatsAppAccount).filter(
        WhatsAppAccount.id == account_id,
        WhatsAppAccount.business_id == current_user.business_id
    ).first()
    if not acc:
        raise HTTPException(status_code=404, detail="WhatsApp account not found")

    db.query(WhatsAppAccount).filter(WhatsAppAccount.business_id == current_user.business_id).update({"is_default": False})
    acc.is_default = True
    db.commit()
    db.refresh(acc)
    return acc

@router.delete("/whatsapp-accounts/{account_id}", status_code=status.HTTP_204_NO_CONTENT)
def disconnect_whatsapp_account(
    account_id: int,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    acc = db.query(WhatsAppAccount).filter(
        WhatsAppAccount.id == account_id,
        WhatsAppAccount.business_id == current_user.business_id
    ).first()
    if not acc:
        raise HTTPException(status_code=404, detail="WhatsApp account not found")

    # Soft disconnect so historical analytics, message history, and campaign stats remain preserved
    acc.status = "DISCONNECTED"
    acc.is_default = False
    db.commit()
    return None

@router.get("/business", response_model=BusinessResponse)
def get_business_profile(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    biz = db.query(Business).filter(Business.id == current_user.business_id).first()
    if not biz:
        raise HTTPException(status_code=404, detail="Business not found")
    return biz
