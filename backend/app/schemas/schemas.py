from pydantic import BaseModel, EmailStr, Field
from typing import Optional, List, Dict, Any
from datetime import datetime
from app.models.entities import UserRole, OptStatus, CampaignStatus, RecipientStatus, ConversationStatus, MessageDirection, MessageType, LeadStage

# Auth & User
class UserCreate(BaseModel):
    business_name: str
    email: EmailStr
    password: str = Field(..., min_length=6)
    full_name: str

class UserLogin(BaseModel):
    email: EmailStr
    password: str

class Token(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user: Dict[str, Any]

class UserResponse(BaseModel):
    id: int
    business_id: int
    email: str
    full_name: str
    role: UserRole
    is_active: bool
    created_at: datetime
    class Config:
        from_attributes = True

class BusinessResponse(BaseModel):
    id: int
    name: str
    phone: Optional[str] = None
    email: Optional[str] = None
    website: Optional[str] = None
    plan: str
    class Config:
        from_attributes = True

# WhatsApp Account
class WhatsAppAccountCreate(BaseModel):
    business_name: str
    phone_number: str
    waba_id: str
    phone_number_id: str
    access_token: Optional[str] = None
    is_default: bool = False

class WhatsAppAccountResponse(BaseModel):
    id: int
    business_id: int
    business_name: str
    phone_number: str
    waba_id: str
    phone_number_id: str
    status: str
    is_default: bool
    profile_name: Optional[str] = None
    profile_picture_url: Optional[str] = None
    quality_rating: Optional[str] = "GREEN"
    code_verification_status: Optional[str] = "VERIFIED"
    about: Optional[str] = None
    vertical: Optional[str] = None
    websites: Optional[str] = None
    connected_at: datetime
    class Config:
        from_attributes = True

# Tags
class TagCreate(BaseModel):
    name: str
    color: str = "#25D366"

class TagResponse(BaseModel):
    id: int
    name: str
    color: str
    created_at: datetime
    class Config:
        from_attributes = True

# Contact
class ContactCreate(BaseModel):
    first_name: str
    last_name: Optional[str] = ""
    phone_number: str
    email: Optional[str] = None
    company: Optional[str] = None
    city: Optional[str] = None
    country: Optional[str] = None
    notes: Optional[str] = ""
    lead_status: Optional[str] = "New Lead"
    customer_status: Optional[str] = "Lead"
    opt_in_status: Optional[OptStatus] = OptStatus.OPTED_IN
    tag_ids: Optional[List[int]] = []

class ContactUpdate(BaseModel):
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    phone_number: Optional[str] = None
    email: Optional[str] = None
    company: Optional[str] = None
    city: Optional[str] = None
    country: Optional[str] = None
    notes: Optional[str] = None
    lead_status: Optional[str] = None
    customer_status: Optional[str] = None
    opt_in_status: Optional[OptStatus] = None
    tag_ids: Optional[List[int]] = None

class ContactResponse(BaseModel):
    id: int
    business_id: int
    first_name: str
    last_name: str
    phone_number: str
    whatsapp_number: str
    email: Optional[str] = None
    company: Optional[str] = None
    city: Optional[str] = None
    country: Optional[str] = None
    notes: str
    lead_status: str
    customer_status: str
    opt_in_status: OptStatus
    opt_out_date: Optional[datetime] = None
    last_interaction: Optional[datetime] = None
    tags: List[TagResponse] = []
    created_at: datetime
    updated_at: datetime
    class Config:
        from_attributes = True

class ContactBulkTag(BaseModel):
    contact_ids: List[int]
    tag_ids: List[int]

class ContactBulkDelete(BaseModel):
    contact_ids: List[int]

class ContactImportResult(BaseModel):
    total_records: int
    imported: int
    duplicates: int
    invalid: int
    skipped: int
    errors: List[str] = []

class ContactPreviewRecord(BaseModel):
    id: int
    name: str
    phone: str
    original_phone: str
    email: Optional[str] = None
    company: Optional[str] = None
    city: Optional[str] = None
    country: Optional[str] = None
    status: str
    reason: str

class ContactPreviewResponse(BaseModel):
    filename: str
    total_records: int
    valid_count: int
    duplicate_count: int
    invalid_count: int
    review_count: int
    records: List[ContactPreviewRecord]

class ContactConfirmRecord(BaseModel):
    id: Optional[int] = None
    name: str
    phone: str
    email: Optional[str] = None
    company: Optional[str] = None
    city: Optional[str] = None
    country: Optional[str] = None
    status: Optional[str] = "VALID"

class ContactConfirmRequest(BaseModel):
    records: List[ContactConfirmRecord]
    tag_name: Optional[str] = None
    source: Optional[str] = "Import"

# Templates
class TemplateCreate(BaseModel):
    name: str
    category: str = "Marketing"
    language: str = "en"
    content: str
    header_text: Optional[str] = None
    footer_text: Optional[str] = None
    template_type: str = "Local App Template"  # Local App Template vs WhatsApp Approved Template
    variables: Optional[List[str]] = []

class TemplateUpdate(BaseModel):
    name: Optional[str] = None
    category: Optional[str] = None
    content: Optional[str] = None
    header_text: Optional[str] = None
    footer_text: Optional[str] = None
    template_type: Optional[str] = None
    status: Optional[str] = None
    variables: Optional[List[str]] = None

class TemplateResponse(BaseModel):
    id: int
    business_id: int
    name: str
    category: str
    language: str
    content: str
    header_text: Optional[str] = None
    footer_text: Optional[str] = None
    variables: str
    template_type: str
    status: str
    meta_template_id: Optional[str] = None
    created_at: datetime
    updated_at: datetime
    class Config:
        from_attributes = True

# Campaigns
class CampaignCreate(BaseModel):
    name: str
    whatsapp_account_id: Optional[int] = None
    template_id: Optional[int] = None
    media_id: Optional[int] = None
    audience_filter: Optional[Dict[str, Any]] = {}
    message_content: str
    media_url: Optional[str] = None
    scheduled_at: Optional[datetime] = None
    time_zone: str = "UTC"

class CampaignUpdate(BaseModel):
    name: Optional[str] = None
    status: Optional[CampaignStatus] = None
    message_content: Optional[str] = None
    scheduled_at: Optional[datetime] = None

class CampaignResponse(BaseModel):
    id: int
    business_id: int
    whatsapp_account_id: Optional[int] = None
    template_id: Optional[int] = None
    media_id: Optional[int] = None
    name: str
    status: CampaignStatus
    message_content: str
    media_url: Optional[str] = None
    scheduled_at: Optional[datetime] = None
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    total_recipients: int
    sent_count: int
    delivered_count: int
    read_count: int
    failed_count: int
    reply_count: int
    opt_out_count: int
    created_at: datetime
    updated_at: datetime
    class Config:
        from_attributes = True

class CampaignDetailResponse(CampaignResponse):
    recipients_sample: List[Dict[str, Any]] = []

class PersonalizePreviewRequest(BaseModel):
    message_template: str
    sample_contact: Dict[str, Any]

class PersonalizePreviewResponse(BaseModel):
    original: str
    personalized: str
    detected_variables: List[str]

# Media
class MediaFileResponse(BaseModel):
    id: int
    business_id: int
    filename: str
    file_type: str
    mime_type: str
    file_size_bytes: int
    public_url: Optional[str] = None
    is_whatsapp_supported: bool = True
    whatsapp_unsupported_reason: Optional[str] = None
    created_at: datetime
    class Config:
        from_attributes = True

# Messages & Conversations
class MessageCreate(BaseModel):
    conversation_id: Optional[int] = None
    contact_id: Optional[int] = None
    to_phone: Optional[str] = None
    body: Optional[str] = ""
    message_type: MessageType = MessageType.TEXT
    media_url: Optional[str] = None
    template_id: Optional[int] = None
    template_name: Optional[str] = None
    template_language: Optional[str] = "en"
    parameters: Optional[List[str]] = None

class MessageResponse(BaseModel):
    id: int
    conversation_id: int
    direction: MessageDirection
    message_type: MessageType
    body: str
    media_url: Optional[str] = None
    whatsapp_message_id: Optional[str] = None
    status: str
    error_message: Optional[str] = None
    is_read: bool
    created_at: datetime
    class Config:
        from_attributes = True

class ConversationResponse(BaseModel):
    id: int
    business_id: int
    contact_id: int
    contact_name: str
    phone_number: str
    status: ConversationStatus
    assigned_user_id: Optional[int] = None
    unread_count: int
    last_message: str
    last_message_at: datetime
    internal_notes: str
    tags: List[TagResponse] = []
    created_at: datetime
    class Config:
        from_attributes = True

class ConversationUpdate(BaseModel):
    status: Optional[ConversationStatus] = None
    assigned_user_id: Optional[int] = None
    internal_notes: Optional[str] = None

# CRM
class LeadCreate(BaseModel):
    contact_id: int
    stage: LeadStage = LeadStage.NEW_LEAD
    deal_value: float = 0.0
    notes: Optional[str] = ""
    next_follow_up: Optional[datetime] = None

class LeadUpdate(BaseModel):
    stage: Optional[LeadStage] = None
    deal_value: Optional[float] = None
    notes: Optional[str] = None
    next_follow_up: Optional[datetime] = None

class LeadResponse(BaseModel):
    id: int
    contact_id: int
    contact_name: str
    phone_number: str
    company: Optional[str] = None
    stage: LeadStage
    deal_value: float
    notes: str
    next_follow_up: Optional[datetime] = None
    created_at: datetime
    updated_at: datetime
    class Config:
        from_attributes = True

# Automation
class AutomationCreate(BaseModel):
    name: str
    trigger_type: str
    nodes_json: str = "[]"
    is_active: bool = True

class AutomationUpdate(BaseModel):
    name: Optional[str] = None
    trigger_type: Optional[str] = None
    nodes_json: Optional[str] = None
    is_active: Optional[bool] = None

class AutomationResponse(BaseModel):
    id: int
    name: str
    trigger_type: str
    nodes_json: str
    is_active: bool
    total_runs: int
    created_at: datetime
    class Config:
        from_attributes = True

# AI Assistant
class AICampaignWriteRequest(BaseModel):
    prompt: str
    product_or_service: Optional[str] = None
    target_audience: Optional[str] = None
    language: str = "en"

class AICampaignWriteResponse(BaseModel):
    main_message: str
    short_version: str
    professional_version: str
    follow_up: str
    cta: str
    suggested_variables: List[str]

class AITranslateRequest(BaseModel):
    text: str
    target_language: str  # English, Urdu, Roman Urdu, Arabic, Spanish, French

class AITranslateResponse(BaseModel):
    original_text: str
    target_language: str
    translated_text: str

class AIReplySuggestRequest(BaseModel):
    conversation_id: int
    last_customer_message: str

class AIReplySuggestResponse(BaseModel):
    professional_reply: str
    friendly_reply: str
    sales_reply: str
    support_reply: str

class AISummarizeRequest(BaseModel):
    conversation_id: int

class AISummarizeResponse(BaseModel):
    customer_intent: str
    important_information: str
    suggested_next_action: str

class AIChatMessage(BaseModel):
    role: str # "user" or "assistant"
    content: str

class AIChatRequest(BaseModel):
    message: str
    history: Optional[List[AIChatMessage]] = []
    gemini_api_key: Optional[str] = None

class AIChatResponse(BaseModel):
    reply: str
    actions: Optional[List[str]] = []
    developer_info: Optional[str] = "Developed by Barkat Ali (https://barkatai.ihsanraza.com/#about)"

# Analytics
class ChartDataPoint(BaseModel):
    label: str
    value: float

class DashboardStatsResponse(BaseModel):
    total_contacts: int
    active_campaigns: int
    scheduled_campaigns: int
    messages_sent: int
    messages_delivered: int
    messages_failed: int
    replies: int
    opt_outs: int
    leads: int
    customers: int
    delivery_rate_percent: float
    response_rate_percent: float
    messages_over_time: List[ChartDataPoint]
    campaign_performance: List[ChartDataPoint]
    contact_growth: List[ChartDataPoint]
