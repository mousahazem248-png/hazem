"""
Ultra Downloader — Optional Metadata & Policy Compliance Backend Service (FastAPI).

Why this Backend is Optional:
- The Android app performs oEmbed, OpenGraph, and HTTP Range downloads directly on-device by default.
- This optional backend is used ONLY when an organization or developer wants centralized oEmbed/Link
  metadata resolution, SSRF firewalling, rate-limiting, or server-side connector rule updates without
  shipping a new APK.
- Zero social media credentials or user files are ever stored on this server.
"""
import os
import time
from collections import defaultdict
from typing import Dict, List, Optional
from urllib.parse import urlparse, parse_qs

from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field

from .security import (
    ALLOWED_PLATFORMS,
    SSRFSecurityError,
    sanitize_filename,
    validate_safe_external_url,
)

app = FastAPI(
    title="Ultra Downloader Optional Metadata API",
    version="1.0.0",
    description="SSRF-hardened, policy-compliant media link analyzer for YouTube, TikTok, Instagram, and Direct HTTPS media."
)

# Simple in-memory sliding-window rate limiter per client IP
RATE_LIMIT_REQUESTS = int(os.getenv("ULTRA_RATE_LIMIT_PER_MIN", "60"))
_request_Timestamps: Dict[str, List[float]] = defaultdict(list)


class AnalyzeRequest(BaseModel):
    url: str = Field(..., description="Public HTTPS URL to inspect")
    resolve_dns: bool = Field(default=False, description="Whether to perform live DNS resolution during validation")


class FormatItem(BaseModel):
    format_id: str
    quality_label: str
    resolution_height: Optional[int]
    extension: str
    mime_type: str
    download_url: str
    expected_size_bytes: Optional[int] = None
    supports_range_resume: bool = True


class AnalyzeResponse(BaseModel):
    source_url: str
    canonical_url: str
    platform: str
    media_id: str
    title: str
    author_name: str
    safe_filename: str
    authorization_status: str
    is_download_permitted: bool
    available_formats: List[FormatItem]
    policy_message_ar: Optional[str] = None
    policy_message_en: Optional[str] = None
    official_alternative_url: Optional[str] = None


def _check_rate_limit(client_ip: str):
    now = time.time()
    window_start = now - 60.0
    history = [t for t in _request_Timestamps[client_ip] if t >= window_start]
    if len(history) >= RATE_LIMIT_REQUESTS:
        raise HTTPException(status_code=429, detail="Rate limit exceeded. Please try again in a minute.")
    history.append(now)
    _request_Timestamps[client_ip] = history


@app.get("/health")
def health_check():
    return {"status": "healthy", "service": "ultra-downloader-backend", "version": "1.0.0"}


@app.post("/api/v1/analyze", response_model=AnalyzeResponse)
def analyze_link(payload: AnalyzeRequest, request: Request):
    client_ip = request.client.host if request.client else "127.0.0.1"
    _check_rate_limit(client_ip)

    try:
        safe_url = validate_safe_external_url(payload.url, resolve_dns=payload.resolve_dns)
    except SSRFSecurityError as ssrf_err:
        raise HTTPException(status_code=403, detail=str(ssrf_err))
    except ValueError as val_err:
        raise HTTPException(status_code=400, detail=str(val_err))

    parsed = urlparse(safe_url)
    host = (parsed.hostname or "").lower().rstrip(".")
    path_lower = (parsed.path or "").lower()

    platform = ALLOWED_PLATFORMS.get(host)
    if not platform:
        if any(path_lower.endswith(f".{ext}") for ext in ("mp4", "m4a", "mp3", "webm", "mov")):
            platform = "DIRECT_MEDIA"
        else:
            raise HTTPException(status_code=422, detail=f"Unsupported platform domain: {host}")

    if platform == "YOUTUBE":
        qs = parse_qs(parsed.query)
        vid = (qs.get("v", [None])[0]) or parsed.path.strip("/").split("/")[-1]
        if not vid or len(vid) != 11:
            raise HTTPException(status_code=400, detail="Invalid 11-character YouTube Video ID")
        canonical = f"https://www.youtube.com/watch?v={vid}"
        title = f"YouTube Video ({vid})"
        return AnalyzeResponse(
            source_url=safe_url,
            canonical_url=canonical,
            platform="YOUTUBE",
            media_id=vid,
            title=title,
            author_name="YouTube Creator",
            safe_filename=sanitize_filename(title, "mp4"),
            authorization_status="RESTRICTED_BY_PLATFORM_TOS",
            is_download_permitted=False,
            available_formats=[],
            policy_message_ar="وفقاً لشروط خدمة YouTube، لا يُسمح للتطبيقات الخارجية بسحب البث المباشر إلا للمحتوى المملوك لك عبر YouTube Studio أو ميزة التنزيل الرسمية داخل تطبيق YouTube.",
            policy_message_en="Per YouTube Terms of Service, raw stream extraction is restricted unless exported from YouTube Studio or downloaded via the official YouTube app.",
            official_alternative_url=canonical,
        )

    if platform == "TIKTOK":
        vid = parsed.path.strip("/").split("/")[-1] or "tiktok_clip"
        title = f"TikTok Public Video ({vid})"
        return AnalyzeResponse(
            source_url=safe_url,
            canonical_url=safe_url,
            platform="TIKTOK",
            media_id=vid,
            title=title,
            author_name="TikTok Creator",
            safe_filename=sanitize_filename(title, "mp4"),
            authorization_status="RESTRICTED_BY_PLATFORM_TOS",
            is_download_permitted=False,
            available_formats=[],
            policy_message_ar="يرجى استخدام خيار حفظ الفيديو الرسمي داخل تطبيق TikTok عندما يسمح صانع المحتوى بذلك.",
            policy_message_en="Please use the official Save Video feature inside TikTok when permitted by the creator.",
            official_alternative_url=safe_url,
        )

    if platform == "INSTAGRAM":
        parts = [p for p in parsed.path.strip("/").split("/") if p]
        if len(parts) < 2 or parts[0] not in {"reel", "reels", "p", "tv"}:
            raise HTTPException(status_code=400, detail="Invalid Instagram Reel or Post shortcode URL")
        shortcode = parts[1]
        canonical = f"https://www.instagram.com/reel/{shortcode}/"
        title = f"Instagram Reel ({shortcode})"
        return AnalyzeResponse(
            source_url=safe_url,
            canonical_url=canonical,
            platform="INSTAGRAM",
            media_id=shortcode,
            title=title,
            author_name="Instagram Creator",
            safe_filename=sanitize_filename(title, "mp4"),
            authorization_status="RESTRICTED_BY_PLATFORM_TOS",
            is_download_permitted=False,
            available_formats=[],
            policy_message_ar="لا يقوم التطبيق بتجاوز تسجيل الدخول أو الحماية الخاصة بـ Instagram. استخدم ميزة التنزيل الرسمية للـ Reels العامة.",
            policy_message_en="Ultra Downloader never bypasses Instagram login or privacy controls. Use the official Instagram download option for eligible public Reels.",
            official_alternative_url=canonical,
        )

    # DIRECT_MEDIA
    filename = parsed.path.strip("/").split("/")[-1] or "direct_video.mp4"
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else "mp4"
    title = filename.rsplit(".", 1)[0].replace("_", " ").replace("-", " ") or "Direct Media"
    mime = "audio/mpeg" if ext == "mp3" else ("audio/mp4" if ext == "m4a" else "video/mp4")

    return AnalyzeResponse(
        source_url=safe_url,
        canonical_url=safe_url,
        platform="DIRECT_MEDIA",
        media_id=str(abs(hash(safe_url)) % 10000000),
        title=title,
        author_name=host,
        safe_filename=sanitize_filename(title, ext),
        authorization_status="AUTHORIZED_DIRECT",
        is_download_permitted=True,
        available_formats=[
            FormatItem(
                format_id=f"direct_{ext}",
                quality_label=f"Original Source ({ext.upper()})",
                resolution_height=720 if ext == "mp4" else None,
                extension=ext,
                mime_type=mime,
                download_url=safe_url,
                expected_size_bytes=None,
                supports_range_resume=True,
            )
        ],
    )
