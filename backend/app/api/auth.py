from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from datetime import timedelta
from app.database import get_db
from app.models.entities import User, Business, UserRole
from app.schemas.schemas import UserCreate, UserLogin, Token, UserResponse
from app.utils.security import hash_password, verify_password, create_access_token, create_refresh_token, decode_token
from app.api.deps import get_current_user

router = APIRouter(prefix="/auth", tags=["Authentication"])

@router.post("/register", response_model=Token)
def register(user_in: UserCreate, db: Session = Depends(get_db)):
    # Check if email exists
    existing = db.query(User).filter(User.email == user_in.email).first()
    if existing:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="A user with this email address already exists."
        )

    # Create Business tenant
    business = Business(
        name=user_in.business_name,
        email=user_in.email,
        plan="Starter"
    )
    db.add(business)
    db.commit()
    db.refresh(business)

    # Create User
    user = User(
        business_id=business.id,
        email=user_in.email,
        password_hash=hash_password(user_in.password),
        full_name=user_in.full_name,
        role=UserRole.OWNER,
        is_active=True
    )
    db.add(user)
    db.commit()
    db.refresh(user)

    # Issue tokens
    access_token = create_access_token(data={"sub": str(user.id), "role": user.role.value, "biz": business.id})
    refresh_token = create_refresh_token(data={"sub": str(user.id)})

    return Token(
        access_token=access_token,
        refresh_token=refresh_token,
        token_type="bearer",
        user={
            "id": user.id,
            "business_id": business.id,
            "business_name": business.name,
            "email": user.email,
            "full_name": user.full_name,
            "role": user.role.value
        }
    )

@router.post("/login", response_model=Token)
def login(login_in: UserLogin, db: Session = Depends(get_db)):
    user = db.query(User).filter(User.email == login_in.email).first()
    if not user or not verify_password(login_in.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Incorrect email or password."
        )
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="User account has been suspended."
        )

    access_token = create_access_token(data={"sub": str(user.id), "role": user.role.value, "biz": user.business_id})
    refresh_token = create_refresh_token(data={"sub": str(user.id)})

    return Token(
        access_token=access_token,
        refresh_token=refresh_token,
        token_type="bearer",
        user={
            "id": user.id,
            "business_id": user.business_id,
            "business_name": user.business.name if user.business else "WAMarketing",
            "email": user.email,
            "full_name": user.full_name,
            "role": user.role.value
        }
    )

@router.get("/me", response_model=UserResponse)
def get_profile(current_user: User = Depends(get_current_user)):
    return current_user

@router.post("/refresh")
def refresh_token(refresh_token: str, db: Session = Depends(get_db)):
    payload = decode_token(refresh_token)
    if not payload or payload.get("type") != "refresh":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid refresh token."
        )
    user_id = payload.get("sub")
    user = db.query(User).filter(User.id == int(user_id)).first()
    if not user or not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User invalid or inactive."
        )
    access_token = create_access_token(data={"sub": str(user.id), "role": user.role.value, "biz": user.business_id})
    return {"access_token": access_token, "token_type": "bearer"}
