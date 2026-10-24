import hashlib
import os
import sys
import tempfile
import threading
import time
import unittest
import xml.etree.ElementTree as ET
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.request import Request, urlopen

# Add backend parent directory to sys.path
BACKEND_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
PROJECT_ROOT = os.path.abspath(os.path.join(BACKEND_ROOT, ".."))
sys.path.insert(0, BACKEND_ROOT)

from fastapi.testclient import TestClient
from app.main import app
from app.security import (
    SSRFSecurityError,
    is_ip_private_or_reserved,
    sanitize_filename,
    validate_safe_external_url,
)


class RangeMediaMockHandler(BaseHTTPRequestHandler):
    """Real local HTTP server supporting Range: bytes=<start>- (HTTP 206) and simulated network drop."""
    payload = b"\x00\x00\x00\x18ftypmp42\x00\x00\x00\x00mp42isom" + (b"ULTRA_VIDEO_STREAM_DATA_" * 400)
    drop_first_request = False
    requests_received = 0

    def log_message(self, format, *args):
        pass  # keep test output clean

    def do_GET(self):
        RangeMediaMockHandler.requests_received += 1
        data = RangeMediaMockHandler.payload
        total_len = len(data)
        range_header = self.headers.get("Range")

        if RangeMediaMockHandler.drop_first_request and RangeMediaMockHandler.requests_received == 1:
            # Simulate mid-stream connection drop after sending 256 bytes
            self.send_response(200)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(total_len))
            self.end_headers()
            self.wfile.write(data[:256])
            self.wfile.flush()
            return

        if range_header and range_header.startswith("bytes="):
            start_str = range_header.split("=")[1].split("-")[0]
            start_offset = int(start_str) if start_str else 0
            sliced = data[start_offset:]
            self.send_response(206)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Range", f"bytes {start_offset}-{total_len - 1}/{total_len}")
            self.send_header("Content-Length", str(len(sliced)))
            self.end_headers()
            self.wfile.write(sliced)
        else:
            self.send_response(200)
            self.send_header("Content-Type", "video/mp4")
            self.send_header("Accept-Ranges", "bytes")
            self.send_header("Content-Length", str(total_len))
            self.end_headers()
            self.wfile.write(data)


class UltraDownloaderVerificationTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.client = TestClient(app)

    def test_01_backend_health_endpoint(self):
        resp = self.client.get("/health")
        self.assertEqual(resp.status_code, 200)
        self.assertEqual(resp.json()["status"], "healthy")

    def test_02_ssrf_and_protocol_blocking(self):
        malicious_urls = [
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://127.0.0.1/secret.mp4",
            "https://localhost/admin",
            "https://10.0.0.42/private.mp4",
            "https://172.16.0.1/internal.mp4",
            "https://192.168.1.100/router.mp4",
            "https://169.254.169.254/latest/meta-data/",
            "https://100.64.10.1/cgnat.mp4",
            "https://[::1]/ipv6_loopback.mp4",
            "https://admin:password@www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com:8080/watch?v=dQw4w9WgXcQ",
            "file:///etc/shadow",
        ]
        for url in malicious_urls:
            with self.subTest(url=url):
                with self.assertRaises((SSRFSecurityError, ValueError)):
                    validate_safe_external_url(url, resolve_dns=False)
                api_resp = self.client.post("/api/v1/analyze", json={"url": url, "resolve_dns": False})
                self.assertIn(api_resp.status_code, (400, 403))

    def test_03_youtube_connector_policy_compliance(self):
        resp = self.client.post(
            "/api/v1/analyze",
            json={"url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "resolve_dns": False},
        )
        self.assertEqual(resp.status_code, 200)
        body = resp.json()
        self.assertEqual(body["platform"], "YOUTUBE")
        self.assertEqual(body["media_id"], "dQw4w9WgXcQ")
        self.assertEqual(body["authorization_status"], "RESTRICTED_BY_PLATFORM_TOS")
        self.assertFalse(body["is_download_permitted"])
        self.assertIsNotNone(body["policy_message_ar"])
        self.assertIsNotNone(body["official_alternative_url"])

    def test_04_tiktok_and_instagram_connector_analysis(self):
        tt_resp = self.client.post(
            "/api/v1/analyze",
            json={"url": "https://www.tiktok.com/@creator/video/7321987654321", "resolve_dns": False},
        )
        self.assertEqual(tt_resp.status_code, 200)
        self.assertEqual(tt_resp.json()["platform"], "TIKTOK")

        ig_resp = self.client.post(
            "/api/v1/analyze",
            json={"url": "https://www.instagram.com/reel/C8abc123XYZ/", "resolve_dns": False},
        )
        self.assertEqual(ig_resp.status_code, 200)
        self.assertEqual(ig_resp.json()["platform"], "INSTAGRAM")
        self.assertEqual(ig_resp.json()["media_id"], "C8abc123XYZ")

    def test_05_direct_media_authorized_analysis(self):
        dm_resp = self.client.post(
            "/api/v1/analyze",
            json={"url": "https://cdn.example.org/media/open_source_1080p.mp4", "resolve_dns": False},
        )
        self.assertEqual(dm_resp.status_code, 200)
        body = dm_resp.json()
        self.assertEqual(body["platform"], "DIRECT_MEDIA")
        self.assertTrue(body["is_download_permitted"])
        self.assertEqual(len(body["available_formats"]), 1)
        self.assertEqual(body["available_formats"][0]["extension"], "mp4")

    def test_06_path_traversal_sanitization(self):
        dirty = "../../../etc/passwd\x00;rm -rf /|<script>"
        clean = sanitize_filename(dirty, "mp4")
        self.assertNotIn("..", clean)
        self.assertNotIn("/", clean)
        self.assertNotIn("\\", clean)
        self.assertNotIn("\x00", clean)
        self.assertTrue(clean.endswith(".mp4"))

    def test_07_real_http_range_pause_resume_and_network_recovery(self):
        server = HTTPServer(("127.0.0.1", 0), RangeMediaMockHandler)
        port = server.server_port
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()

        try:
            RangeMediaMockHandler.drop_first_request = True
            RangeMediaMockHandler.requests_received = 0
            url = f"http://127.0.0.1:{port}/sample_video.mp4"

            with tempfile.TemporaryDirectory() as tmpdir:
                part_file = os.path.join(tmpdir, "video.mp4.part")

                # Attempt 1: Server drops connection after 256 bytes (raises IncompleteRead with partial bytes)
                from http.client import IncompleteRead
                req1 = Request(url)
                try:
                    with urlopen(req1) as r1:
                        chunk1 = r1.read()
                except IncompleteRead as ir:
                    chunk1 = ir.partial
                with open(part_file, "wb") as f:
                    f.write(chunk1)
                self.assertEqual(os.path.getsize(part_file), 256)

                # Attempt 2: Resume using Range: bytes=256-
                existing = os.path.getsize(part_file)
                req2 = Request(url, headers={"Range": f"bytes={existing}-"})
                with urlopen(req2) as r2:
                    self.assertEqual(r2.status, 206)
                    chunk2 = r2.read()
                with open(part_file, "ab") as f:
                    f.write(chunk2)

                # Verify full payload and SHA-256 integrity
                with open(part_file, "rb") as f:
                    final_bytes = f.read()
                self.assertEqual(final_bytes, RangeMediaMockHandler.payload)
                self.assertEqual(
                    hashlib.sha256(final_bytes).hexdigest(),
                    hashlib.sha256(RangeMediaMockHandler.payload).hexdigest(),
                )
        finally:
            server.shutdown()
            server.server_close()

    def test_08_arabic_and_english_localization_parity(self):
        en_xml = os.path.join(PROJECT_ROOT, "app/src/main/res/values/strings.xml")
        ar_xml = os.path.join(PROJECT_ROOT, "app/src/main/res/values-ar/strings.xml")

        en_tree = ET.parse(en_xml).getroot()
        ar_tree = ET.parse(ar_xml).getroot()

        en_keys = {el.attrib["name"] for el in en_tree.findall("string")}
        ar_keys = {el.attrib["name"] for el in ar_tree.findall("string")}

        self.assertEqual(
            en_keys,
            ar_keys,
            f"Mismatch between English and Arabic string keys: diff={en_keys ^ ar_keys}",
        )
        self.assertGreaterEqual(len(en_keys), 45)


if __name__ == "__main__":
    unittest.main(verbosity=2)
