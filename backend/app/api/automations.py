from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List

from app.database import get_db
from app.models.entities import Automation, AutomationRun, User
from app.schemas.schemas import AutomationCreate, AutomationUpdate, AutomationResponse
from app.api.deps import get_current_user

router = APIRouter(prefix="/automations", tags=["Automations"])

@router.get("", response_model=List[AutomationResponse])
def list_automations(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return db.query(Automation).filter(
        Automation.business_id == current_user.business_id
    ).order_by(Automation.id.desc()).all()

@router.post("", response_model=AutomationResponse, status_code=status.HTTP_201_CREATED)
def create_automation(
    auto_in: AutomationCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    auto = Automation(
        business_id=current_user.business_id,
        name=auto_in.name,
        trigger_type=auto_in.trigger_type,
        nodes_json=auto_in.nodes_json,
        is_active=auto_in.is_active
    )
    db.add(auto)
    db.commit()
    db.refresh(auto)
    return auto

@router.get("/{auto_id}", response_model=AutomationResponse)
def get_automation(auto_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    auto = db.query(Automation).filter(
        Automation.id == auto_id,
        Automation.business_id == current_user.business_id
    ).first()
    if not auto:
        raise HTTPException(status_code=404, detail="Automation not found")
    return auto

@router.put("/{auto_id}", response_model=AutomationResponse)
def update_automation(
    auto_id: int,
    auto_in: AutomationUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    auto = db.query(Automation).filter(
        Automation.id == auto_id,
        Automation.business_id == current_user.business_id
    ).first()
    if not auto:
        raise HTTPException(status_code=404, detail="Automation not found")

    update_data = auto_in.model_dump(exclude_unset=True)
    for k, v in update_data.items():
        setattr(auto, k, v)

    db.commit()
    db.refresh(auto)
    return auto

@router.delete("/{auto_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_automation(auto_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    auto = db.query(Automation).filter(
        Automation.id == auto_id,
        Automation.business_id == current_user.business_id
    ).first()
    if not auto:
        raise HTTPException(status_code=404, detail="Automation not found")
    db.delete(auto)
    db.commit()
    return None
