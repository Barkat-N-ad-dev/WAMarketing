import os
import uuid
import mimetypes
from fastapi import APIRouter, Depends, HTTPException, UploadFile, File, status
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
from typing import List

from app.database import get_db
from app.models.entities import MediaFile, User
from app.schemas.schemas import MediaFileResponse
from app.api.deps import get_current_user
from app.config import settings

router = APIRouter(prefix="/media", tags=["Media"])

# Block dangerous executable extensions unconditionally
DISALLOWED_EXTENSIONS = {
    ".exe", ".bat", ".cmd", ".sh", ".vbs", ".dll", ".com", ".scr", ".msi", ".ps1", ".pif", ".application"
}

# Supported file categories
SUPPORTED_EXTENSIONS_MAP = {
    # Images (WhatsApp supported)
    ".jpg": ("image", "image/jpeg", True, None),
    ".jpeg": ("image", "image/jpeg", True, None),
    ".png": ("image", "image/png", True, None),
    ".webp": ("image", "image/webp", True, None),
    ".gif": ("image", "image/gif", True, None),
    
    # Videos (WhatsApp supported)
    ".mp4": ("video", "video/mp4", True, None),
    ".3gp": ("video", "video/3gpp", True, None),
    ".mov": ("video", "video/quicktime", True, None),
    
    # Audio (WhatsApp supported)
    ".mp3": ("audio", "audio/mpeg", True, None),
    ".aac": ("audio", "audio/aac", True, None),
    ".ogg": ("audio", "audio/ogg", True, None),
    ".wav": ("audio", "audio/wav", True, None),
    
    # Documents (WhatsApp supported)
    ".pdf": ("document", "application/pdf", True, None),
    ".doc": ("document", "application/msword", True, None),
    ".docx": ("document", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", True, None),
    ".xls": ("document", "application/vnd.ms-excel", True, None),
    ".xlsx": ("document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", True, None),
    ".ppt": ("document", "application/vnd.ms-powerpoint", True, None),
    ".pptx": ("document", "application/vnd.openxmlformats-officedocument.presentationml.presentation", True, None),
    ".txt": ("document", "text/plain", True, None),
    ".csv": ("document", "text/csv", True, None),
    
    # APK & Package Files (Supported as WhatsApp document attachment)
    ".apk": ("document", "application/vnd.android.package-archive", True, None),
    
    # Archives (Supported as WhatsApp document attachment)
    ".zip": ("document", "application/zip", True, None)
}

@router.get("", response_model=List[MediaFileResponse])
def list_media(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    return db.query(MediaFile).filter(
        MediaFile.business_id == current_user.business_id
    ).order_by(MediaFile.id.desc()).all()

@router.get("/{file_id}", response_model=MediaFileResponse)
def get_media(file_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    media = db.query(MediaFile).filter(
        MediaFile.id == file_id,
        MediaFile.business_id == current_user.business_id
    ).first()
    if not media:
        raise HTTPException(status_code=404, detail="Media file not found")
    return media

@router.post("/upload", response_model=MediaFileResponse, status_code=status.HTTP_201_CREATED)
async def upload_media(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    original_filename = file.filename or "unknown_file"
    _, ext = os.path.splitext(original_filename.lower())

    # 1. Security Check: Explicitly block malicious executables
    if ext in DISALLOWED_EXTENSIONS:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Security violation: Executable and script file uploads ({ext}) are strictly prohibited."
        )

    # 2. Check Supported Extensions
    if ext not in SUPPORTED_EXTENSIONS_MAP:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported file format '{ext}'. Supported formats: PDF, Images (JPG, PNG, WEBP), Videos (MP4), Documents (DOCX, XLSX, TXT, CSV), APK files, and ZIP archives."
        )

    file_category, default_mime, is_wa_supported, wa_unsupported_reason = SUPPORTED_EXTENSIONS_MAP[ext]
    detected_mime = file.content_type if (file.content_type and file.content_type != "application/octet-stream") else default_mime

    # 3. Ensure upload directory exists
    os.makedirs(settings.UPLOAD_DIR, exist_ok=True)

    # Generate safe unique filename
    safe_name = f"{uuid.uuid4().hex[:12]}_{os.path.basename(original_filename)}"
    file_path = os.path.join(settings.UPLOAD_DIR, safe_name)

    # 4. Stream and validate file size (Limit: 100MB max)
    max_size_bytes = 100 * 1024 * 1024
    size = 0
    try:
        with open(file_path, "wb") as buffer:
            while chunk := await file.read(1024 * 1024):  # 1MB chunks
                size += len(chunk)
                if size > max_size_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_400_BAD_REQUEST,
                        detail="File exceeds maximum allowed upload size (100MB)."
                    )
                buffer.write(chunk)
    except Exception:
        if os.path.exists(file_path):
            os.remove(file_path)
        raise

    # 5. Persist record to database
    media = MediaFile(
        business_id=current_user.business_id,
        filename=original_filename,
        file_type=file_category,
        mime_type=detected_mime,
        file_size_bytes=size,
        storage_path=file_path,
        public_url=f"/static/uploads/{safe_name}",
        is_whatsapp_supported=is_wa_supported,
        whatsapp_unsupported_reason=wa_unsupported_reason
    )
    db.add(media)
    db.commit()
    db.refresh(media)
    return media

@router.get("/{file_id}/download")
def download_media(file_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    media = db.query(MediaFile).filter(
        MediaFile.id == file_id,
        MediaFile.business_id == current_user.business_id
    ).first()
    if not media:
        raise HTTPException(status_code=404, detail="Media file not found")
    
    if not os.path.exists(media.storage_path):
        raise HTTPException(status_code=404, detail="Physical media file no longer found on server")

    return FileResponse(
        path=media.storage_path,
        filename=media.filename,
        media_type=media.mime_type
    )

@router.delete("/{file_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_media(file_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    media = db.query(MediaFile).filter(
        MediaFile.id == file_id,
        MediaFile.business_id == current_user.business_id
    ).first()
    if not media:
        raise HTTPException(status_code=404, detail="Media file not found")

    if os.path.exists(media.storage_path):
        try:
            os.remove(media.storage_path)
        except Exception:
            pass

    db.delete(media)
    db.commit()
    return None
