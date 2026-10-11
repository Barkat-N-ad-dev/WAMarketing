import re
from typing import Optional, Tuple

def sanitize_phone_number(raw_phone: str, default_country_code: Optional[str] = None) -> Tuple[bool, Optional[str], Optional[str]]:
    """
    Validates and standardizes a phone number into E.164 format.
    Preserves all original international country codes without altering them.
    Returns: (is_valid, normalized_number, error_reason)
    """
    if not raw_phone or not isinstance(raw_phone, str):
        return False, None, "Phone number is empty"

    cleaned = re.sub(r'[\s\-\(\)\.]', '', raw_phone.strip())
    
    # 1. Numbers starting with + (already international notation, preserve digits)
    if cleaned.startswith("+"):
        digits = cleaned[1:]
    # 2. Numbers starting with 00 (international prefix notation, e.g. 0092... or 001...)
    elif cleaned.startswith("00"):
        digits = cleaned[2:]
    # 3. Pakistani local mobile numbers starting with 03 (e.g. 03091234567 -> 923091234567)
    elif cleaned.startswith("03") and len(cleaned) == 11:
        digits = "92" + cleaned[1:]
    # 4. Other numbers starting with leading 0 trunk prefix
    elif cleaned.startswith("0"):
        if default_country_code and default_country_code.strip():
            prefix = default_country_code.strip().lstrip("+")
            digits = prefix + cleaned.lstrip("0")
        else:
            # Strip the leading 0 trunk prefix without guessing
            digits = cleaned.lstrip("0")
    # 5. Raw digits without + (e.g. 923093697069, 15551977671, 971501234567)
    else:
        # If standard length (>= 10 digits), it already contains the country code
        if len(cleaned) >= 10:
            digits = cleaned
        elif default_country_code and default_country_code.strip():
            prefix = default_country_code.strip().lstrip("+")
            digits = prefix + cleaned
        else:
            digits = cleaned

    if not digits.isdigit():
        return False, None, "Contains non-numeric characters"

    # E.164 standard: max 15 digits, min 7 digits
    if len(digits) < 7 or len(digits) > 15:
        return False, None, f"Invalid length ({len(digits)} digits). Must be between 7 and 15 digits"

    return True, f"+{digits}", None

