"""
SSRF (Server-Side Request Forgery) & URL Hardening for Ultra Downloader Optional Backend.
- Enforces HTTPS-only URLs.
- Rejects embedded credentials, non-standard ports, and private/loopback/link-local/metadata IPv4 & IPv6 addresses.
- Performs DNS resolution validation against `ipaddress` private/reserved blocks before any outbound HTTP call.
"""
import ipaddress
import re
import socket
from urllib.parse import urlparse


ALLOWED_PLATFORMS = {
    "youtube.com": "YOUTUBE",
    "www.youtube.com": "YOUTUBE",
    "m.youtube.com": "YOUTUBE",
    "music.youtube.com": "YOUTUBE",
    "youtu.be": "YOUTUBE",
    "youtube-nocookie.com": "YOUTUBE",
    "tiktok.com": "TIKTOK",
    "www.tiktok.com": "TIKTOK",
    "vm.tiktok.com": "TIKTOK",
    "vt.tiktok.com": "TIKTOK",
    "m.tiktok.com": "TIKTOK",
    "instagram.com": "INSTAGRAM",
    "www.instagram.com": "INSTAGRAM",
    "instagr.am": "INSTAGRAM",
}

FORBIDDEN_HOSTNAMES = {
    "localhost",
    "localhost.localdomain",
    "ip6-localhost",
    "ip6-loopback",
    "metadata.google.internal",
    "metadata",
    "instance-data",
}


class SSRFSecurityError(ValueError):
    """Raised when a URL violates HTTPS or SSRF network boundaries."""


def is_ip_private_or_reserved(ip_str: str) -> bool:
    try:
        addr = ipaddress.ip_address(ip_str)
    except ValueError:
        return True

    if (
        addr.is_private
        or addr.is_loopback
        or addr.is_link_local
        or addr.is_multicast
        or addr.is_reserved
        or addr.is_unspecified
    ):
        return True

    # Carrier-Grade NAT 100.64.0.0/10 (RFC 6598)
    if isinstance(addr, ipaddress.IPv4Address):
        if addr in ipaddress.IPv4Network("100.64.0.0/10"):
            return True
        if addr in ipaddress.IPv4Network("169.254.0.0/16"):
            return True
    return False


def validate_safe_external_url(raw_url: str, resolve_dns: bool = True) -> str:
    if not raw_url or not raw_url.strip():
        raise ValueError("URL cannot be empty")

    cleaned = raw_url.strip()
    if len(cleaned) > 2048:
        raise ValueError("URL exceeds maximum allowed length (2048 chars)")

    parsed = urlparse(cleaned)
    if parsed.scheme.lower() != "https":
        raise SSRFSecurityError(f"Only encrypted https:// URLs are allowed (got '{parsed.scheme}://')")

    if parsed.username or parsed.password:
        raise SSRFSecurityError("Embedded credentials in URLs are forbidden")

    host = (parsed.hostname or "").lower().rstrip(".")
    if not host:
        raise ValueError("URL is missing a valid hostname")

    if (
        host in FORBIDDEN_HOSTNAMES
        or host.endswith(".local")
        or host.endswith(".internal")
        or host.endswith(".lan")
        or host.endswith(".corp")
    ):
        raise SSRFSecurityError(f"Internal hostname '{host}' is blocked by SSRF policy")

    # Check if host is a literal IP
    is_literal_ip = False
    try:
        ipaddress.ip_address(host)
        is_literal_ip = True
    except ValueError:
        is_literal_ip = False

    if is_literal_ip and is_ip_private_or_reserved(host):
        raise SSRFSecurityError(f"Literal private/reserved IP '{host}' is blocked by SSRF policy")

    if resolve_dns:
        try:
            addr_info = socket.getaddrinfo(host, parsed.port or 443, proto=socket.IPPROTO_TCP)
            for family, _, _, _, sockaddr in addr_info:
                ip_resolved = sockaddr[0]
                if is_ip_private_or_reserved(ip_resolved):
                    raise SSRFSecurityError(
                        f"Host '{host}' resolved to internal/reserved IP '{ip_resolved}'"
                    )
        except socket.gaierror:
            # If DNS cannot resolve in offline test mode, literal checks above still apply
            pass

    if parsed.port not in (None, 443):
        raise SSRFSecurityError(f"Non-standard HTTPS port '{parsed.port}' is blocked")

    return cleaned


def sanitize_filename(raw_title: str, extension: str = "mp4") -> str:
    ext = extension.lower().strip().lstrip(".")
    if ext not in {"mp4", "m4a", "mp3", "webm", "mov"}:
        ext = "mp4"

    cleaned = re.sub(r"(\.\.|/|\\|\x00|[<>:\"|?*\r\n\t`$;&|])", "_", raw_title or "")
    cleaned = re.sub(r"\s+", " ", cleaned).strip(" .")
    if not cleaned or cleaned.lower() in {"con", "prn", "aux", "nul"}:
        cleaned = "ultra_media_item"
    cleaned = cleaned[:80].strip(" .")
    return f"{cleaned}.{ext}"
