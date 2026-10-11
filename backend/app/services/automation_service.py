import json
import logging
from datetime import datetime
from typing import Dict, Any, List
from sqlalchemy.orm import Session
from app.models.entities import (
    Automation, AutomationRun, Contact, Tag, Lead, LeadStage,
    OptStatus, Conversation
)
from app.services.whatsapp_service import whatsapp_service
from app.services.ai_service import ai_service

logger = logging.getLogger(__name__)

class AutomationEngine:
    @staticmethod
    def trigger_event(db: Session, event_type: str, contact: Contact, context_data: Dict[str, Any] = None):
        """
        Evaluates active automations matching event_type (e.g. 'new_lead', 'tag_added', 'inbound_message')
        and executes their node pipeline.
        """
        automations = db.query(Automation).filter(
            Automation.business_id == contact.business_id,
            Automation.trigger_type == event_type,
            Automation.is_active == True
        ).all()

        for auto in automations:
            try:
                nodes = json.loads(auto.nodes_json) if isinstance(auto.nodes_json, str) else auto.nodes_json
                if not nodes:
                    continue

                run = AutomationRun(
                    automation_id=auto.id,
                    contact_id=contact.id,
                    status="RUNNING",
                    logs_json="[]"
                )
                db.add(run)
                db.commit()
                db.refresh(run)

                logs = []
                for node in nodes:
                    node_type = node.get("type")
                    node_data = node.get("data", {})
                    log_entry = {"time": datetime.utcnow().isoformat(), "node": node_type}

                    if node_type == "add_tag":
                        tag_name = node_data.get("tag_name")
                        if tag_name:
                            tag = db.query(Tag).filter(Tag.business_id == contact.business_id, Tag.name == tag_name).first()
                            if not tag:
                                tag = Tag(business_id=contact.business_id, name=tag_name)
                                db.add(tag)
                                db.commit()
                                db.refresh(tag)
                            if tag not in contact.tags:
                                contact.tags.append(tag)
                                db.commit()
                            log_entry["detail"] = f"Added tag {tag_name}"

                    elif node_type == "update_crm":
                        new_stage = node_data.get("stage", "Contacted")
                        lead = db.query(Lead).filter(Lead.contact_id == contact.id).first()
                        if not lead:
                            lead = Lead(
                                business_id=contact.business_id,
                                contact_id=contact.id,
                                stage=LeadStage.CONTACTED
                            )
                            db.add(lead)
                        else:
                            try:
                                lead.stage = LeadStage(new_stage)
                            except Exception:
                                pass
                        contact.lead_status = new_stage
                        db.commit()
                        log_entry["detail"] = f"CRM updated to stage {new_stage}"

                    elif node_type == "send_whatsapp":
                        # Send WhatsApp message or template if opted in
                        if contact.opt_in_status == OptStatus.OPTED_IN:
                            msg_body = node_data.get("message", f"Hello {contact.first_name}, thank you for contacting us!")
                            # Simulated / asynchronous dispatch
                            log_entry["detail"] = f"Queued WhatsApp message: {msg_body[:30]}..."
                        else:
                            log_entry["detail"] = "Skipped send: contact is not opted in"

                    logs.append(log_entry)

                run.status = "COMPLETED"
                run.completed_at = datetime.utcnow()
                run.logs_json = json.dumps(logs)
                auto.total_runs += 1
                db.commit()

            except Exception as e:
                logger.error(f"Error running automation {auto.id}: {e}")
                if 'run' in locals():
                    run.status = "FAILED"
                    run.completed_at = datetime.utcnow()
                    db.commit()

automation_engine = AutomationEngine()
