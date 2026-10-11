import os
from dotenv import load_dotenv
from pydantic_settings import BaseSettings
from typing import Optional

load_dotenv()

class Settings(BaseSettings):
    APP_NAME: str = "WAMarketing Platform"
    APP_ENV: str = "development"
    DEBUG: bool = True
    SECRET_KEY: str = "wamarketing-super-secret-jwt-key-change-in-production-2026"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 1440  # 24 hours
    REFRESH_TOKEN_EXPIRE_DAYS: int = 30

    # Database
    DATABASE_URL: str = os.getenv("DATABASE_URL", "sqlite:///./wamarketing.db")

    # WhatsApp Business Cloud API
    WHATSAPP_API_URL: str = "https://graph.facebook.com"
    WHATSAPP_API_VERSION: str = "v19.0"
    WHATSAPP_ACCESS_TOKEN: Optional[str] = os.getenv("WHATSAPP_ACCESS_TOKEN") or os.getenv("DEFAULT_ACCESS_TOKEN") or "mock_wa_access_token"
    WHATSAPP_PHONE_NUMBER_ID: Optional[str] = os.getenv("WHATSAPP_PHONE_NUMBER_ID") or os.getenv("DEFAULT_PHONE_NUMBER_ID") or "1181137018426157"
    WHATSAPP_BUSINESS_ACCOUNT_ID: Optional[str] = os.getenv("WHATSAPP_BUSINESS_ACCOUNT_ID") or os.getenv("DEFAULT_WABA_ID") or "2080401999220434"
    WHATSAPP_WEBHOOK_VERIFY_TOKEN: str = os.getenv("WHATSAPP_WEBHOOK_VERIFY_TOKEN", "wamarketing_secure_verify_token")
    WHATSAPP_APP_SECRET: Optional[str] = os.getenv("WHATSAPP_APP_SECRET", "mock_app_secret")

    # AI Configuration (Gemini API / Fallback heuristic engine)
    GEMINI_API_KEY: Optional[str] = os.getenv("GEMINI_API_KEY", "")

    # Storage
    UPLOAD_DIR: str = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "uploads")
    MAX_FILE_SIZE_MB: int = 25

    # Rate Limiting & Queue
    MAX_MESSAGES_PER_SECOND: int = 20
    DEFAULT_RATE_LIMIT_PAUSE_SECONDS: float = 0.05

    class Config:
        env_file = ".env"
        extra = "ignore"

settings = Settings()
