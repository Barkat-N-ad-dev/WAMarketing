from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from app.database import get_db
from app.models.entities import Conversation, Message, User
from app.schemas.schemas import (
    AICampaignWriteRequest, AICampaignWriteResponse,
    AITranslateRequest, AITranslateResponse,
    AIReplySuggestRequest, AIReplySuggestResponse,
    AISummarizeRequest, AISummarizeResponse,
    AIChatRequest, AIChatResponse
)
from app.api.deps import get_current_user
from app.services.ai_service import ai_service

router = APIRouter(prefix="/ai", tags=["AI Assistant"])

@router.post("/campaign-writer", response_model=AICampaignWriteResponse)
def write_campaign(req: AICampaignWriteRequest, current_user: User = Depends(get_current_user)):
    res = ai_service.generate_campaign_content(
        prompt=req.prompt,
        product=req.product_or_service or "",
        audience=req.target_audience or "",
        language=req.language
    )
    return AICampaignWriteResponse(
        main_message=res["main_message"],
        short_version=res["short_version"],
        professional_version=res["professional_version"],
        follow_up=res["follow_up"],
        cta=res["cta"],
        suggested_variables=res["suggested_variables"]
    )

@router.post("/translate", response_model=AITranslateResponse)
def translate_text(req: AITranslateRequest, current_user: User = Depends(get_current_user)):
    translated = ai_service.translate_message(req.text, req.target_language)
    return AITranslateResponse(
        original_text=req.text,
        target_language=req.target_language,
        translated_text=translated
    )

@router.post("/reply-suggest", response_model=AIReplySuggestResponse)
def suggest_reply(req: AIReplySuggestRequest, current_user: User = Depends(get_current_user)):
    replies = ai_service.suggest_replies(req.last_customer_message)
    return AIReplySuggestResponse(
        professional_reply=replies["professional_reply"],
        friendly_reply=replies["friendly_reply"],
        sales_reply=replies["sales_reply"],
        support_reply=replies["support_reply"]
    )

@router.post("/summarize", response_model=AISummarizeResponse)
def summarize_conversation(
    req: AISummarizeRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    conv = db.query(Conversation).filter(
        Conversation.id == req.conversation_id,
        Conversation.business_id == current_user.business_id
    ).first()
    if not conv:
        raise HTTPException(status_code=404, detail="Conversation not found")

    messages = [m.body for m in conv.messages[-10:]]
    summary = ai_service.summarize_conversation(messages)

    return AISummarizeResponse(
        customer_intent=summary["customer_intent"],
        important_information=summary["important_information"],
        suggested_next_action=summary["suggested_next_action"]
    )

@router.post("/chat", response_model=AIChatResponse)
def chat_with_assistant(
    req: AIChatRequest,
    current_user: User = Depends(get_current_user)
):
    """
    In-App AI Assistant for user support, WhatsApp Cloud API guidance,
    marketing campaign advice, and developer profile information.
    """
    history_dicts = [{"role": m.role, "content": m.content} for m in (req.history or [])]
    res = ai_service.chat_assistant(message=req.message, history=history_dicts, gemini_api_key=req.gemini_api_key)
    return AIChatResponse(
        reply=res["reply"],
        actions=res.get("actions", []),
        developer_info=res.get("developer_info")
    )
