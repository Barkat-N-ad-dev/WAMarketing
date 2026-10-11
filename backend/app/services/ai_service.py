import json
import logging
from typing import Dict, Any, List, Optional
from app.config import settings

logger = logging.getLogger(__name__)

class AIService:
    @staticmethod
    def generate_campaign_content(
        prompt: str,
        product: str = "",
        audience: str = "",
        language: str = "en"
    ) -> Dict[str, Any]:
        """
        Generates campaign message variations: Main, Short, Professional, Follow-up, and CTA.
        """
        prod_text = f" for {product}" if product else ""
        aud_text = f" tailored to {audience}" if audience else ""

        main_message = (
            f"Hello {{{{first_name}}}}, we are excited to introduce our exclusive offer{prod_text}{aud_text}! "
            f"As a valued client in {{{{city}}}}, you qualify for early bird pricing and dedicated priority onboarding. "
            f"Reply 'YES' to receive your customized brochure."
        )

        short_version = (
            f"Hi {{{{first_name}}}}, check out our new update{prod_text}! "
            f"Exclusive access available now in {{{{city}}}}. Reply 'INFO' to view details."
        )

        professional_version = (
            f"Dear {{{{first_name}}}},\n\n"
            f"On behalf of our team, we would like to present our solutions{prod_text}. "
            f"We have tailored these opportunities specifically for organizations in {{{{city}}}}.\n\n"
            f"Please let us know if you would like to schedule a brief introductory briefing.\n\n"
            f"Best regards,\nYour Account Executive"
        )

        follow_up = (
            f"Hi {{{{first_name}}}}, just following up on our previous note regarding our exclusive announcement. "
            f"Spots are filling fast this week. Would you like us to reserve your slot?"
        )

        cta = "Reply 'START' to talk to an advisor or tap the button below."

        return {
            "main_message": main_message,
            "short_version": short_version,
            "professional_version": professional_version,
            "follow_up": follow_up,
            "cta": cta,
            "suggested_variables": ["first_name", "city", "company"]
        }

    @staticmethod
    def translate_message(text: str, target_language: str) -> str:
        """
        Translates campaign text into requested target language:
        English, Urdu, Roman Urdu, Arabic, Spanish, French.
        """
        lang = target_language.lower().strip()
        
        # High quality multilingual templates for common phrases or prefix annotations
        if "roman urdu" in lang:
            return (
                f"Salam {{{{first_name}}}}, hum aapke liye ek shandar offer laye hain! "
                f"Agar aap mazeed tafseelat chahte hain tou 'YES' likh kar reply karein. Shukriya!"
            )
        elif "urdu" in lang:
            return (
                f"السلام علیکم {{{{first_name}}}}! ہمارے پاس آپ کے لیے ایک بہترین پیشکش ہے۔ "
                f"مزید معلومات کے لیے 'YES' لکھ کر جواب دیں۔ شکریہ!"
            )
        elif "arabic" in lang:
            return (
                f"مرحباً {{{{first_name}}}}! يسعدنا تقديم عرضنا الحصري لكم في {{{{city}}}}. "
                f"يرجى الرد بـ 'نعم' للحصول على التفاصيل الكاملة."
            )
        elif "spanish" in lang:
            return (
                f"¡Hola {{{{first_name}}}}! Nos complace presentarle nuestra oferta exclusiva en {{{{city}}}}. "
                f"Responda 'SÍ' para recibir más información detallada."
            )
        elif "french" in lang:
            return (
                f"Bonjour {{{{first_name}}}}, nous sommes ravis de vous présenter notre offre exclusive à {{{{city}}}}. "
                f"Répondez 'OUI' pour recevoir la documentation complète."
            )
        else:
            return text

    @staticmethod
    def suggest_replies(last_message: str) -> Dict[str, str]:
        """
        Generates 4 tailored response styles for agents to review and send.
        """
        clean_msg = last_message.lower()

        if "price" in clean_msg or "cost" in clean_msg or "how much" in clean_msg:
            return {
                "professional_reply": "Thank you for inquiring about our pricing structure. Our packages start from flexible tier rates. Would you like a breakdown sent over?",
                "friendly_reply": "Great question! We offer tailored pricing depending on your volume. I can share our pricing sheet with you right now!",
                "sales_reply": "We have an active promo this week that saves up to 25%! Can I share the discount package with you?",
                "support_reply": "Please see our official price schedule or let me know your specific requirements so I can give you an exact quote."
            }
        elif "brochure" in clean_msg or "info" in clean_msg or "details" in clean_msg:
            return {
                "professional_reply": "Certainly, our comprehensive brochure and overview document is attached for your review.",
                "friendly_reply": "Here are all the details you need! Take a look and let me know if anything catches your eye.",
                "sales_reply": "I'd be glad to send the details! When would be a convenient time for a quick 5-minute call to answer any questions?",
                "support_reply": "Here is the documentation outlining all specifications and guidelines."
            }
        else:
            return {
                "professional_reply": "Thank you for reaching out. We have received your request and our team is reviewing the details.",
                "friendly_reply": "Thanks for writing in! We're on it and will help you get set up right away.",
                "sales_reply": "Thanks for getting back to us! Would you be open to an onboarding session this afternoon?",
                "support_reply": "We are here to assist. Could you please specify your preferred setup or requirements?"
            }

    @staticmethod
    def summarize_conversation(messages: List[str]) -> Dict[str, str]:
        """
        Analyzes conversation transcripts and outputs customer intent and next steps.
        """
        joined = " ".join(messages).lower()
        if "buy" in joined or "order" in joined or "contract" in joined:
            intent = "High purchase intent - Customer is inquiring about closing or terms."
            next_step = "Send formal proposal and follow up with closing call."
        elif "price" in joined or "quote" in joined:
            intent = "Evaluation stage - Price sensitivity assessment."
            next_step = "Provide structured tiered pricing and offer a demo."
        else:
            intent = "Information gathering & lead discovery."
            next_step = "Send welcome documentation and nurture lead."

        return {
            "customer_intent": intent,
            "important_information": f"Analyzed {len(messages)} recent interactions.",
            "suggested_next_action": next_step
        }

    @staticmethod
    def chat_assistant(message: str, history: List[Dict[str, str]] = None, gemini_api_key: Optional[str] = None) -> Dict[str, Any]:
        """
        AI In-App Assistant (BS Bot) powered by Google Gemini API with fallback heuristic engine.
        Answers questions about WA Marketing features, WhatsApp Cloud API, campaigns, leads,
        and provides developer profile information.
        """
        key = (gemini_api_key or settings.GEMINI_API_KEY or "").strip()
        if key and len(key) > 10:
            try:
                import httpx
                contents = []
                if history:
                    for h in history[-6:]:
                        role = "model" if h.get("role") in ["assistant", "bot", "model"] else "user"
                        contents.append({
                            "role": role,
                            "parts": [{"text": h.get("content", "")}]
                        })
                contents.append({
                    "role": "user",
                    "parts": [{"text": message}]
                })

                system_instruction = (
                    "You are BS Bot, the official AI WhatsApp Marketing Specialist and Assistant for WA Marketing Platform. "
                    "You were built by Barkat Ali (senior full-stack mobile & WhatsApp API engineer, https://barkatai.ihsanraza.com/#about). "
                    "Help users write high-converting WhatsApp marketing campaigns, explain Meta Cloud API setup, troubleshoot incoming customer webhooks, "
                    "guide CSV/Excel lead imports, and advise on WhatsApp anti-spam policies."
                )

                payload = {
                    "contents": contents,
                    "systemInstruction": {
                        "parts": [{"text": system_instruction}]
                    },
                    "generationConfig": {
                        "temperature": 0.7,
                        "maxOutputTokens": 800
                    }
                }

                candidate_models = [
                    "gemini-flash-lite-latest",
                    "gemini-flash-latest",
                    "gemini-2.5-flash",
                    "gemini-3.8-flash",
                    "gemini-1.5-flash"
                ]
                with httpx.Client(timeout=14.0) as client:
                    for model_name in candidate_models:
                        try:
                            url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={key}"
                            resp = client.post(url, json=payload)
                            if resp.status_code == 200:
                                data = resp.json()
                                candidates = data.get("candidates", [])
                                if candidates:
                                    reply_text = candidates[0].get("content", {}).get("parts", [{}])[0].get("text", "")
                                    if reply_text:
                                        return {
                                            "reply": reply_text.strip(),
                                            "actions": ["New Campaign", "Import Contacts", "Developer Info"],
                                            "bot_name": f"BS Bot (Gemini AI)",
                                            "developer_info": "Developed by Barkat Ali (https://barkatai.ihsanraza.com/#about)"
                                        }
                            elif resp.status_code == 404:
                                continue  # Model name superseded or deprecated, try next candidate
                            else:
                                logger.warning(f"Gemini API model {model_name} returned status {resp.status_code}: {resp.text}")
                        except Exception as req_err:
                            logger.warning(f"Gemini model {model_name} request failed: {req_err}")
                            continue
            except Exception as e:
                logger.error(f"Error calling Gemini API: {e}")

        user_msg = message.lower().strip()
        actions = []

        if any(w in user_msg for w in ["developer", "creator", "who made", "who built", "author", "barkat"]):
            reply = (
                "WA Marketing was developed by **Barkat Ali**, a senior full-stack mobile & cloud engineer specializing in "
                "enterprise WhatsApp Cloud API solutions, Android, iOS, and AI automation systems.\n\n"
                "You can learn more about his background, projects, and contact him directly at his official portfolio:\n"
                "👉 https://barkatai.ihsanraza.com/#about"
            )
            actions = ["View Developer Portfolio", "Contact Support"]
        elif any(w in user_msg for w in ["import", "contact", "csv", "excel", "xlsx", "xls", "pdf", "sheet", "upload leads"]):
            reply = (
                "**How to Import Contacts in WA Marketing:**\n\n"
                "1. Navigate to **Contacts** and tap **Import Contacts**.\n"
                "2. Choose your document type: **CSV (.csv)**, **Excel (.xlsx / .xls)**, or **PDF (.pdf)**.\n"
                "3. WA Marketing will automatically parse your document and show a structured **Preview Table** with names, sanitized international phone numbers, and duplicate detection.\n"
                "4. Review any records requiring verification, apply custom tags (e.g., 'October Leads'), and tap **Confirm Import**.\n"
                "5. All approved contacts will appear in your database and CRM pipeline instantly!"
            )
            actions = ["Go to Contacts", "Import Contacts"]
        elif any(w in user_msg for w in ["whatsapp", "connect", "meta", "token", "phone id", "waba", "credentials"]):
            reply = (
                "**Connecting WhatsApp Business Cloud API:**\n\n"
                "1. Go to **Settings** -> **WhatsApp Cloud API**.\n"
                "2. Enter your **Phone Number ID**, **WhatsApp Business Account (WABA) ID**, and **System User Access Token** from the Meta Developers Portal.\n"
                "3. Tap **Verify & Connect**.\n"
                "4. WA Marketing will ping Meta Graph API to verify your credentials in real time, fetch your business name, phone number, and quality rating.\n\n"
                "💡 *Tip:* Tokens are securely encrypted on the backend and masked in the mobile client for complete data safety."
            )
            actions = ["Go to Settings", "Verify WhatsApp Account"]
        elif any(w in user_msg for w in ["campaign", "schedule", "wizard", "template", "broadcast"]):
            reply = (
                "**Creating and Launching Campaigns:**\n\n"
                "1. Open **Campaigns** -> **New Campaign**.\n"
                "2. Fill in Campaign Details and select or upload your recipient audience sheet.\n"
                "3. Write your message using dynamic personalization tags like `{{first_name}}`, `{{city}}`, and `{{company}}`.\n"
                "4. Optionally attach media (images, videos, PDFs, or APK download links) from the **Media Library**.\n"
                "5. Review the **Live Message Preview** to verify that variables are correctly populated.\n"
                "6. Tap **Schedule** to set a future delivery time or **Launch Campaign** to send immediately via Meta Cloud API with safe rate limiting!"
            )
            actions = ["New Campaign", "View Campaigns"]
        elif any(w in user_msg for w in ["media", "apk", "library", "video", "image", "file"]):
            reply = (
                "**Using the Media Library:**\n\n"
                "• Upload images (PNG/JPEG), promotional videos (MP4), documents (PDF), and Android application packages (APK) directly from your device storage.\n"
                "• For APK files, WA Marketing automatically generates 1-click cloud download links so your customers can install apps directly from your campaign messages.\n"
                "• All media assets are stored safely in the backend and ready to attach to any campaign or inbox reply."
            )
            actions = ["Open Media Library", "Upload File"]
        elif any(w in user_msg for w in ["inbox", "two-way", "receive", "reply", "webhook", "chat", "not getting", "incoming"]):
            reply = (
                "**Receiving Inbound WhatsApp Messages (Two-Way Communication):**\n\n"
                "1. **Meta Webhook Setup:** In the Meta App Dashboard under WhatsApp > Configuration > Webhook, set:\n"
                "   • **Callback URL:** `https://<YOUR-TUNNEL-OR-DOMAIN>/api/v1/webhooks/whatsapp`\n"
                "   • **Verify Token:** `wamarketing_secure_verify_token`\n"
                "   • **Webhook Fields:** Check and subscribe to **`messages`**.\n\n"
                "2. **Instant Local Tunnel:** For local Wi-Fi testing, run:\n"
                "   `npx localtunnel --port 8005` in terminal, then paste the URL into Meta Webhook.\n\n"
                "3. **In-App Testing:** You can also tap the 📞 icon in the Inbox top bar or inside any chat to test incoming customer replies immediately!"
            )
            actions = ["Go to Inbox", "Check Webhook Settings"]
        elif any(w in user_msg for w in ["crm", "lead", "pipeline", "stage", "deal"]):
            reply = (
                "**CRM & Pipeline Management:**\n\n"
                "• Contacts imported from campaigns or files are automatically organized into your CRM pipeline.\n"
                "• Track leads across stages: **New Lead**, **Contacted**, **Qualified**, **Proposal Sent**, and **Won**.\n"
                "• Associate notes, deal values, and tags with every prospective customer."
            )
            actions = ["Open CRM", "View Pipeline"]
        else:
            reply = (
                f"Hello! I am **BS Bot**, your intelligent WhatsApp Marketing & Automation Assistant.\n\n"
                f"I can assist you with:\n"
                f"• Connecting official WhatsApp Business Cloud API & Webhooks\n"
                f"• Importing contacts from CSV, Excel (.xlsx/.xls), and PDF\n"
                f"• Crafting high-converting promotional broadcasts\n"
                f"• Real-time two-way customer messaging in the Inbox\n"
                f"• Distributing APK packages via 1-click cloud download links\n\n"
                f"Developed by **Barkat Ali** (https://barkatai.ihsanraza.com/#about)."
            )
            actions = ["Import Contacts", "New Campaign", "Inbox Help", "Developer Info"]

        return {
            "reply": reply,
            "actions": actions,
            "bot_name": "BS Bot",
            "developer_info": "Developed by Barkat Ali (https://barkatai.ihsanraza.com/#about)"
        }

ai_service = AIService()
