import io
import re
import pandas as pd
from typing import Dict, Any, List, Optional
from sqlalchemy.orm import Session
from app.models.entities import Contact, Tag, OptStatus, Lead, LeadStage
from app.utils.phone_validator import sanitize_phone_number
try:
    import pypdf
except ImportError:
    pypdf = None

class ContactService:

    @staticmethod
    def extract_records_from_file(
        file_bytes: bytes,
        filename: str,
        column_mapping: Optional[Dict[str, str]] = None,
        default_country_code: Optional[str] = None
    ) -> List[Dict[str, Any]]:
        """
        Parses CSV, XLSX, XLS, and PDF files into a list of normalized raw records.
        """
        lower_name = filename.lower()
        records: List[Dict[str, Any]] = []

        if lower_name.endswith((".xlsx", ".xls")):
            try:
                engine = "openpyxl" if lower_name.endswith(".xlsx") else "xlrd"
                df = pd.read_excel(io.BytesIO(file_bytes), engine=engine, dtype=str)
                records = ContactService._df_to_records(df, column_mapping)
            except Exception as e:
                # Fallback to default engine
                df = pd.read_excel(io.BytesIO(file_bytes), dtype=str)
                records = ContactService._df_to_records(df, column_mapping)

        elif lower_name.endswith(".csv") or lower_name.endswith(".txt"):
            # Try multiple encodings, delimiters, and headerless formats
            for enc in ["utf-8-sig", "utf-8", "latin1", "cp1252"]:
                parsed = False
                for sep in [None, ",", ";", "\t", "|"]:
                    try:
                        if sep is None:
                            df = pd.read_csv(io.BytesIO(file_bytes), encoding=enc, sep=None, engine="python", dtype=str)
                        else:
                            df = pd.read_csv(io.BytesIO(file_bytes), encoding=enc, sep=sep, dtype=str)
                        
                        # Check if header actually contained phone numbers (headerless file)
                        has_phone_in_headers = any(re.search(r"\d{7,}", str(c)) for c in df.columns)
                        if has_phone_in_headers:
                            if sep is None:
                                df = pd.read_csv(io.BytesIO(file_bytes), encoding=enc, sep=None, engine="python", header=None, dtype=str)
                            else:
                                df = pd.read_csv(io.BytesIO(file_bytes), encoding=enc, sep=sep, header=None, dtype=str)

                        rec = ContactService._df_to_records(df, column_mapping)
                        if rec:
                            records = rec
                            parsed = True
                            break
                    except Exception:
                        continue
                if parsed:
                    break

        elif lower_name.endswith(".pdf"):
            records = ContactService._extract_from_pdf(file_bytes)

        return records

    @staticmethod
    def _df_to_records(df: pd.DataFrame, column_mapping: Optional[Dict[str, str]] = None) -> List[Dict[str, Any]]:
        df = df.fillna("")
        if df.empty:
            return []

        normalized_cols = {str(col).strip().lower(): col for col in df.columns}
        name_col = None
        phone_col = None
        email_col = None
        company_col = None
        city_col = None
        country_col = None

        if column_mapping:
            name_col = column_mapping.get("name") or column_mapping.get("first_name")
            phone_col = column_mapping.get("phone") or column_mapping.get("phone_number")
            email_col = column_mapping.get("email")
            company_col = column_mapping.get("company")
            city_col = column_mapping.get("city")
            country_col = column_mapping.get("country")
        else:
            for col_lower, orig_col in normalized_cols.items():
                if any(k in col_lower for k in ["phone", "mobile", "whatsapp", "cell", "number", "tel", "contact_no", "msisdn"]):
                    if not phone_col:
                        phone_col = orig_col
                elif any(k in col_lower for k in ["name", "contact", "person", "client", "lead", "full_name"]):
                    if not name_col:
                        name_col = orig_col
                elif "email" in col_lower:
                    email_col = orig_col
                elif any(k in col_lower for k in ["comp", "org", "business", "company"]):
                    company_col = orig_col
                elif "city" in col_lower:
                    city_col = orig_col
                elif "country" in col_lower:
                    country_col = orig_col

        # Fallback if no phone col found by header name: scan data rows across columns
        if not phone_col:
            for c in df.columns:
                # Check up to first 10 rows
                sample_rows = df[c].dropna().astype(str).head(10).tolist()
                phone_like_count = sum(1 for v in sample_rows if re.search(r"(\+?\d[\d\s\-\(\)]{6,}\d)", v.strip()))
                if phone_like_count > 0:
                    phone_col = c
                    break

        # If only one column exists in the entire sheet, and it has numbers, it's the phone column
        if not phone_col and len(df.columns) == 1:
            phone_col = df.columns[0]

        # If name column wasn't identified, pick another column that isn't the phone column
        if not name_col:
            for c in df.columns:
                if c != phone_col:
                    name_col = c
                    break

        records = []
        for idx, row in df.iterrows():
            raw_phone = str(row[phone_col]).strip() if phone_col and phone_col in df.columns else ""
            raw_name = str(row[name_col]).strip() if name_col and name_col in df.columns else f"Lead {idx + 1}"
            raw_email = str(row[email_col]).strip() if email_col and email_col in df.columns else ""
            raw_company = str(row[company_col]).strip() if company_col and company_col in df.columns else "Enterprise"
            raw_city = str(row[city_col]).strip() if city_col and city_col in df.columns else "Global"
            raw_country = str(row[country_col]).strip() if country_col and country_col in df.columns else "Global"

            # If raw_name is empty or just numbers, give a clean default
            if not raw_name or re.fullmatch(r"[\d\+\s\-]+", raw_name):
                raw_name = f"Lead {idx + 1}"

            records.append({
                "row_index": idx + 1,
                "name": raw_name,
                "phone": raw_phone,
                "email": raw_email,
                "company": raw_company,
                "city": raw_city,
                "country": raw_country
            })

        return records

    @staticmethod
    def _extract_from_pdf(file_bytes: bytes) -> List[Dict[str, Any]]:
        records = []
        try:
            if not pypdf:
                return []
            reader = pypdf.PdfReader(io.BytesIO(file_bytes))
            phone_pattern = re.compile(r"(\+?\d{1,4}[-.\s]?)?\(?\d{2,4}\)?[-.\s]?\d{3,4}[-.\s]?\d{3,4}")
            email_pattern = re.compile(r"[\w\.-]+@[\w\.-]+\.\w+")

            row_counter = 1
            for page_num, page in enumerate(reader.pages):
                text = page.extract_text() or ""
                lines = [l.strip() for l in text.split("\n") if l.strip()]

                for line in lines:
                    phone_match = phone_pattern.search(line)
                    if phone_match:
                        raw_phone = phone_match.group(0).strip()
                        cleaned_line = line.replace(raw_phone, "")
                        email_match = email_pattern.search(cleaned_line)
                        raw_email = email_match.group(0).strip() if email_match else ""
                        if raw_email:
                            cleaned_line = cleaned_line.replace(raw_email, "")

                        # Remainder is candidate name or company
                        name_candidate = re.sub(r"[^\w\s]", " ", cleaned_line).strip()
                        name_parts = [p for p in name_candidate.split() if len(p) > 1]
                        name = " ".join(name_parts[:3]) if name_parts else f"Lead {row_counter}"

                        records.append({
                            "row_index": row_counter,
                            "name": name,
                            "phone": raw_phone,
                            "email": raw_email,
                            "company": "PDF Document Lead",
                            "city": "Global",
                            "country": "Global",
                            "confidence": "HIGH" if len(raw_phone) >= 9 else "LOW"
                        })
                        row_counter += 1
        except Exception:
            pass

        return records

    @staticmethod
    def parse_preview(
        db: Session,
        business_id: int,
        file_bytes: bytes,
        filename: str,
        column_mapping: Optional[Dict[str, str]] = None,
        default_country_code: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Analyzes uploaded file (.csv, .xlsx, .xls, .pdf) and returns structured preview with validation.
        """
        raw_records = ContactService.extract_records_from_file(
            file_bytes=file_bytes,
            filename=filename,
            column_mapping=column_mapping,
            default_country_code=default_country_code
        )

        existing_phones = set(
            p[0] for p in db.query(Contact.phone_number).filter(Contact.business_id == business_id).all()
        )

        seen_in_batch = set()
        validated_records = []
        valid_count = 0
        duplicate_count = 0
        invalid_count = 0
        review_count = 0

        for r in raw_records:
            phone_raw = r.get("phone", "")
            valid, clean_phone, reason = sanitize_phone_number(phone_raw, default_country_code)

            status = "VALID"
            validation_reason = "Ready to import"

            if not valid:
                status = "INVALID"
                validation_reason = reason or "Malformed phone number"
                invalid_count += 1
            elif clean_phone in existing_phones:
                status = "DUPLICATE"
                validation_reason = "Already exists in database"
                duplicate_count += 1
            elif clean_phone in seen_in_batch:
                status = "DUPLICATE"
                validation_reason = "Duplicate entry in current file"
                duplicate_count += 1
            else:
                seen_in_batch.add(clean_phone)
                if r.get("confidence") == "LOW":
                    status = "REQUIRES_REVIEW"
                    validation_reason = "Low confidence OCR/PDF parsing"
                    review_count += 1
                else:
                    valid_count += 1

            validated_records.append({
                "id": r.get("row_index", 1),
                "name": r.get("name", ""),
                "phone": clean_phone if valid else phone_raw,
                "original_phone": phone_raw,
                "email": r.get("email", ""),
                "company": r.get("company", ""),
                "city": r.get("city", ""),
                "country": r.get("country", ""),
                "status": status,
                "reason": validation_reason
            })

        return {
            "filename": filename,
            "total_records": len(raw_records),
            "valid_count": valid_count,
            "duplicate_count": duplicate_count,
            "invalid_count": invalid_count,
            "review_count": review_count,
            "records": validated_records
        }

    @staticmethod
    def parse_and_import(
        db: Session,
        business_id: int,
        file_bytes: bytes,
        filename: str,
        column_mapping: Dict[str, str] = None,
        default_country_code: Optional[str] = None,
        tag_name: Optional[str] = None
    ) -> ContactImportResult:
        preview = ContactService.parse_preview(
            db=db,
            business_id=business_id,
            file_bytes=file_bytes,
            filename=filename,
            column_mapping=column_mapping,
            default_country_code=default_country_code
        )

        imported = 0
        tag = None
        if tag_name:
            tag = db.query(Tag).filter(Tag.name == tag_name, Tag.business_id == business_id).first()
            if not tag:
                tag = Tag(name=tag_name, business_id=business_id, color="#10B981")
                db.add(tag)
                db.commit()
                db.refresh(tag)

        for rec in preview["records"]:
            if rec["status"] in ["VALID", "REQUIRES_REVIEW"]:
                full_name = rec["name"].strip()
                parts = full_name.split(" ", 1)
                first_name = parts[0] if parts and parts[0] else "Lead"
                last_name = parts[1] if len(parts) > 1 else ""

                contact = Contact(
                    business_id=business_id,
                    first_name=first_name,
                    last_name=last_name,
                    phone_number=rec["phone"],
                    whatsapp_number=rec["phone"],
                    email=rec["email"] or None,
                    company=rec["company"] or "Enterprise",
                    city=rec["city"] or "Global",
                    country=rec["country"] or "Global",
                    lead_status="New Lead",
                    customer_status="Lead",
                    opt_in_status=OptStatus.OPTED_IN,
                    notes=f"Imported from {filename}"
                )
                if tag:
                    contact.tags.append(tag)

                db.add(contact)
                db.flush()
                imported += 1

                # Also generate CRM Lead
                lead = Lead(
                    business_id=business_id,
                    contact_id=contact.id,
                    stage=LeadStage.NEW_LEAD,
                    deal_value=25000.0,
                    notes=f"Auto-created from import: {filename}"
                )
                db.add(lead)

        db.commit()

        return ContactImportResult(
            total_records=preview["total_records"],
            imported=imported,
            duplicates=preview["duplicate_count"],
            invalid=preview["invalid_count"],
            skipped=preview["total_records"] - imported,
            errors=[f"{r['name']}: {r['reason']}" for r in preview["records"] if r["status"] == "INVALID"][:20]
        )

contact_service = ContactService()
