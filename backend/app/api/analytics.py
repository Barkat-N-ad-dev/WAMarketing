from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from app.database import get_db
from app.models.entities import User
from app.schemas.schemas import DashboardStatsResponse
from app.api.deps import get_current_user
from app.services.analytics_service import analytics_service

router = APIRouter(prefix="/analytics", tags=["Analytics"])

@router.get("/dashboard", response_model=DashboardStatsResponse)
def get_dashboard_analytics(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    return analytics_service.get_dashboard_stats(db, current_user.business_id)
