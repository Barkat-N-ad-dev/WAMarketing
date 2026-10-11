import enum
from datetime import datetime
from sqlalchemy import (
    Column, Integer, String, Text, Boolean, Float, DateTime, ForeignKey, Table, Enum
)
from sqlalchemy.orm import relationship
from app.database import Base

class UserRole(str, enum.Enum):
    OWNER = "OWNER"
    ADMIN = "ADMIN"
    MANAGER = "MANAGER"
    AGENT = "AGENT"

class OptStatus(str, enum.Enum):
    OPTED_IN = "opted_in"
    OPTED_OUT = "opted_out"
    UNKNOWN = "unknown"

class CampaignStatus(str, enum.Enum):
    DRAFT = "DRAFT"
    SCHEDULED = "SCHEDULED"
    RUNNING = "RUNNING"
    PAUSED = "PAUSED"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"

class RecipientStatus(str, enum.Enum):
    PENDING = "PENDING"
    SENT = "SENT"
    DELIVERED = "DELIVERED"
    READ = "READ"
    FAILED = "FAILED"
    OPTED_OUT = "OPTED_OUT"

class ConversationStatus(str, enum.Enum):
    OPEN = "OPEN"
    PENDING = "PENDING"
    RESOLVED = "RESOLVED"

class MessageDirection(str, enum.Enum):
    INBOUND = "INBOUND"
    OUTBOUND = "OUTBOUND"

class MessageType(str, enum.Enum):
    TEXT = "text"
    IMAGE = "image"
    VIDEO = "video"
    DOCUMENT = "document"
    AUDIO = "audio"
    TEMPLATE = "template"
    INTERACTIVE = "interactive"

class LeadStage(str, enum.Enum):
    NEW_LEAD = "New Lead"
    CONTACTED = "Contacted"
    INTERESTED = "Interested"
    FOLLOW_UP = "Follow-up"
    NEGOTIATION = "Negotiation"
    CUSTOMER = "Customer"
    CLOSED = "Closed"

# Association table: Contacts <-> Tags
contact_tags = Table(
    "contact_tags",
    Base.metadata,
    Column("contact_id", Integer, ForeignKey("contacts.id", ondelete="CASCADE"), primary_key=True),
    Column("tag_id", Integer, ForeignKey("tags.id", ondelete="CASCADE"), primary_key=True)
)

class Business(Base):
    __tablename__ = "businesses"

    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), nullable=False)
    phone = Column(String(50), nullable=True)
    email = Column(String(255), nullable=True)
    website = Column(String(255), nullable=True)
    plan = Column(String(50), default="Starter")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    users = relationship("User", back_populates="business", cascade="all, delete-orphan")
    whatsapp_accounts = relationship("WhatsAppAccount", back_populates="business", cascade="all, delete-orphan")
    contacts = relationship("Contact", back_populates="business", cascade="all, delete-orphan")
    tags = relationship("Tag", back_populates="business", cascade="all, delete-orphan")
    campaigns = relationship("Campaign", back_populates="business", cascade="all, delete-orphan")
    templates = relationship("MessageTemplate", back_populates="business", cascade="all, delete-orphan")
    automations = relationship("Automation", back_populates="business", cascade="all, delete-orphan")
    media_files = relationship("MediaFile", back_populates="business", cascade="all, delete-orphan")

class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    email = Column(String(255), unique=True, index=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    full_name = Column(String(255), nullable=False)
    role = Column(Enum(UserRole), default=UserRole.OWNER, nullable=False)
    is_active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    business = relationship("Business", back_populates="users")

class WhatsAppAccount(Base):
    __tablename__ = "whatsapp_accounts"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    business_name = Column(String(255), nullable=False)
    phone_number = Column(String(50), nullable=False)
    waba_id = Column(String(100), nullable=False)  # WhatsApp Business Account ID
    phone_number_id = Column(String(100), nullable=False)  # WhatsApp Phone Number ID
    access_token = Column(Text, nullable=True)  # Kept secure on backend only
    status = Column(String(50), default="CONNECTED")  # CONNECTED, DISCONNECTED, PENDING
    is_default = Column(Boolean, default=False)
    profile_name = Column(String(255), nullable=True)
    profile_picture_url = Column(String(500), nullable=True)
    quality_rating = Column(String(50), default="GREEN")  # GREEN, YELLOW, RED, UNKNOWN
    code_verification_status = Column(String(50), default="VERIFIED")  # VERIFIED, NOT_VERIFIED, EXPIRED
    about = Column(String(255), nullable=True)
    vertical = Column(String(100), nullable=True)
    websites = Column(Text, nullable=True)
    connected_at = Column(DateTime, default=datetime.utcnow)

    business = relationship("Business", back_populates="whatsapp_accounts")
    campaigns = relationship("Campaign", back_populates="whatsapp_account")
    conversations = relationship("Conversation", back_populates="whatsapp_account")

class Tag(Base):
    __tablename__ = "tags"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(100), nullable=False)
    color = Column(String(20), default="#25D366")
    created_at = Column(DateTime, default=datetime.utcnow)

    business = relationship("Business", back_populates="tags")
    contacts = relationship("Contact", secondary=contact_tags, back_populates="tags")

class Contact(Base):
    __tablename__ = "contacts"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    first_name = Column(String(100), nullable=False)
    last_name = Column(String(100), default="")
    phone_number = Column(String(50), nullable=False, index=True)
    whatsapp_number = Column(String(50), nullable=False, index=True)
    email = Column(String(255), nullable=True)
    company = Column(String(255), nullable=True)
    city = Column(String(100), nullable=True)
    country = Column(String(100), nullable=True)
    notes = Column(Text, default="")
    lead_status = Column(String(50), default="New Lead")
    customer_status = Column(String(50), default="Lead")
    opt_in_status = Column(Enum(OptStatus), default=OptStatus.OPTED_IN, nullable=False, index=True)
    opt_out_date = Column(DateTime, nullable=True)
    opt_out_reason = Column(String(255), nullable=True)
    last_interaction = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    business = relationship("Business", back_populates="contacts")
    tags = relationship("Tag", secondary=contact_tags, back_populates="contacts")
    conversations = relationship("Conversation", back_populates="contact", cascade="all, delete-orphan")
    lead_entry = relationship("Lead", back_populates="contact", uselist=False, cascade="all, delete-orphan")

class MessageTemplate(Base):
    __tablename__ = "message_templates"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(150), nullable=False)
    category = Column(String(50), default="Marketing")  # Marketing, Utility, Authentication, Follow-up, Welcome, Appointment, Support
    language = Column(String(20), default="en")
    content = Column(Text, nullable=False)
    header_text = Column(String(255), nullable=True)
    footer_text = Column(String(255), nullable=True)
    variables = Column(Text, default="[]")  # JSON array string e.g. ["first_name", "company"]
    template_type = Column(String(50), default="Local App Template")  # "Local App Template" vs "WhatsApp Approved Template"
    button_text = Column(String(100), nullable=True)
    button_url = Column(String(500), nullable=True)
    status = Column(String(50), default="APPROVED")  # APPROVED, PENDING, REJECTED, DRAFT
    meta_template_id = Column(String(100), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    business = relationship("Business", back_populates="templates")
    campaigns = relationship("Campaign", back_populates="template")

class MediaFile(Base):
    __tablename__ = "media_files"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    filename = Column(String(255), nullable=False)
    file_type = Column(String(50), nullable=False)  # image, video, document, audio
    mime_type = Column(String(100), nullable=False)
    file_size_bytes = Column(Integer, default=0)
    storage_path = Column(String(500), nullable=False)
    public_url = Column(String(500), nullable=True)
    meta_media_id = Column(String(100), nullable=True)  # WhatsApp Cloud API uploaded media ID
    is_whatsapp_supported = Column(Boolean, default=True)
    whatsapp_unsupported_reason = Column(String(255), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    business = relationship("Business", back_populates="media_files")

class Campaign(Base):
    __tablename__ = "campaigns"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    whatsapp_account_id = Column(Integer, ForeignKey("whatsapp_accounts.id", ondelete="SET NULL"), nullable=True)
    template_id = Column(Integer, ForeignKey("message_templates.id", ondelete="SET NULL"), nullable=True)
    media_id = Column(Integer, ForeignKey("media_files.id", ondelete="SET NULL"), nullable=True)
    name = Column(String(255), nullable=False)
    status = Column(Enum(CampaignStatus), default=CampaignStatus.DRAFT, nullable=False, index=True)
    audience_filter = Column(Text, default="{}")  # JSON string of criteria (tags, country, lead_status)
    message_content = Column(Text, nullable=False)
    media_url = Column(String(500), nullable=True)
    scheduled_at = Column(DateTime, nullable=True)
    time_zone = Column(String(50), default="UTC")
    started_at = Column(DateTime, nullable=True)
    completed_at = Column(DateTime, nullable=True)
    
    # Aggregated metrics
    total_recipients = Column(Integer, default=0)
    sent_count = Column(Integer, default=0)
    delivered_count = Column(Integer, default=0)
    read_count = Column(Integer, default=0)
    failed_count = Column(Integer, default=0)
    reply_count = Column(Integer, default=0)
    opt_out_count = Column(Integer, default=0)

    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    business = relationship("Business", back_populates="campaigns")
    whatsapp_account = relationship("WhatsAppAccount", back_populates="campaigns")
    template = relationship("MessageTemplate", back_populates="campaigns")
    recipients = relationship("CampaignRecipient", back_populates="campaign", cascade="all, delete-orphan")

class CampaignRecipient(Base):
    __tablename__ = "campaign_recipients"

    id = Column(Integer, primary_key=True, index=True)
    campaign_id = Column(Integer, ForeignKey("campaigns.id", ondelete="CASCADE"), nullable=False, index=True)
    contact_id = Column(Integer, ForeignKey("contacts.id", ondelete="CASCADE"), nullable=False, index=True)
    phone_number = Column(String(50), nullable=False)
    personalized_text = Column(Text, nullable=False)
    status = Column(Enum(RecipientStatus), default=RecipientStatus.PENDING, nullable=False, index=True)
    whatsapp_message_id = Column(String(100), nullable=True, index=True)
    error_message = Column(Text, nullable=True)
    sent_at = Column(DateTime, nullable=True)
    delivered_at = Column(DateTime, nullable=True)
    read_at = Column(DateTime, nullable=True)

    campaign = relationship("Campaign", back_populates="recipients")
    contact = relationship("Contact")

class Conversation(Base):
    __tablename__ = "conversations"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    whatsapp_account_id = Column(Integer, ForeignKey("whatsapp_accounts.id", ondelete="SET NULL"), nullable=True)
    contact_id = Column(Integer, ForeignKey("contacts.id", ondelete="CASCADE"), nullable=False, index=True)
    status = Column(Enum(ConversationStatus), default=ConversationStatus.OPEN, nullable=False, index=True)
    assigned_user_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    unread_count = Column(Integer, default=0)
    last_message = Column(Text, default="")
    last_message_at = Column(DateTime, default=datetime.utcnow, index=True)
    internal_notes = Column(Text, default="")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    contact = relationship("Contact", back_populates="conversations")
    whatsapp_account = relationship("WhatsAppAccount", back_populates="conversations")
    messages = relationship("Message", back_populates="conversation", cascade="all, delete-orphan")

class Message(Base):
    __tablename__ = "messages"

    id = Column(Integer, primary_key=True, index=True)
    conversation_id = Column(Integer, ForeignKey("conversations.id", ondelete="CASCADE"), nullable=False, index=True)
    direction = Column(Enum(MessageDirection), nullable=False)  # INBOUND, OUTBOUND
    message_type = Column(Enum(MessageType), default=MessageType.TEXT, nullable=False)
    body = Column(Text, nullable=False)
    media_url = Column(String(500), nullable=True)
    whatsapp_message_id = Column(String(100), nullable=True, index=True)
    status = Column(String(50), default="SENT")  # SENT, DELIVERED, READ, FAILED
    error_message = Column(Text, nullable=True)
    is_read = Column(Boolean, default=False)
    created_at = Column(DateTime, default=datetime.utcnow, index=True)

    conversation = relationship("Conversation", back_populates="messages")

class Lead(Base):
    __tablename__ = "leads"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    contact_id = Column(Integer, ForeignKey("contacts.id", ondelete="CASCADE"), unique=True, nullable=False)
    stage = Column(Enum(LeadStage), default=LeadStage.NEW_LEAD, nullable=False, index=True)
    deal_value = Column(Float, default=0.0)
    notes = Column(Text, default="")
    next_follow_up = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    contact = relationship("Contact", back_populates="lead_entry")

class Automation(Base):
    __tablename__ = "automations"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    name = Column(String(255), nullable=False)
    trigger_type = Column(String(50), nullable=False)  # new_lead, tag_added, inbound_message, form_submitted
    nodes_json = Column(Text, nullable=False, default="[]")  # Visual builder JSON graph
    is_active = Column(Boolean, default=True)
    total_runs = Column(Integer, default=0)
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    business = relationship("Business", back_populates="automations")
    runs = relationship("AutomationRun", back_populates="automation", cascade="all, delete-orphan")

class AutomationRun(Base):
    __tablename__ = "automation_runs"

    id = Column(Integer, primary_key=True, index=True)
    automation_id = Column(Integer, ForeignKey("automations.id", ondelete="CASCADE"), nullable=False, index=True)
    contact_id = Column(Integer, ForeignKey("contacts.id", ondelete="CASCADE"), nullable=False, index=True)
    status = Column(String(50), default="RUNNING")  # RUNNING, COMPLETED, FAILED
    current_node_id = Column(String(50), nullable=True)
    logs_json = Column(Text, default="[]")
    started_at = Column(DateTime, default=datetime.utcnow)
    completed_at = Column(DateTime, nullable=True)

    automation = relationship("Automation", back_populates="runs")
    contact = relationship("Contact")

class OptOut(Base):
    __tablename__ = "opt_outs"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    phone_number = Column(String(50), nullable=False, index=True)
    reason = Column(String(255), default="Customer requested unsubscribe")
    keyword = Column(String(50), default="STOP")
    opt_out_date = Column(DateTime, default=datetime.utcnow)

class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(Integer, primary_key=True, index=True)
    business_id = Column(Integer, ForeignKey("businesses.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    action = Column(String(100), nullable=False)
    entity_type = Column(String(100), nullable=False)
    entity_id = Column(String(100), nullable=True)
    details = Column(Text, default="")
    ip_address = Column(String(50), nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)
