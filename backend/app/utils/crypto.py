"""Cryptographic utilities for the backend."""
import base64
import os
from typing import Optional
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.exceptions import InvalidTag

from app.config import settings


class AESGCMCrypto:
    """AES-256-GCM encryption and decryption for sensitive credentials like RTSP URLs."""

    def __init__(self, secret_key: str):
        """
        Initialize with a 32-byte hex or plain string secret key.
        Pads or truncates to 32 bytes to ensure AES-256 compliance.
        """
        if not secret_key:
            raise ValueError("Secret key cannot be empty.")
        try:
            self.key = bytes.fromhex(secret_key)
            if len(self.key) != 32:
                self.key = self.key.ljust(32, b"\0")[:32]
        except ValueError:
            self.key = secret_key.encode("utf-8").ljust(32, b"\0")[:32]

        self.aesgcm = AESGCM(self.key)

    def encrypt(self, data: str) -> str:
        """
        Encrypt plain text data using AES-256-GCM with a random 12-byte nonce.
        Returns base64-encoded string of (nonce + ciphertext + auth_tag).
        """
        if data is None:
            raise ValueError("Data to encrypt cannot be None.")
        nonce = os.urandom(12)
        ciphertext = self.aesgcm.encrypt(nonce, data.encode("utf-8"), None)
        return base64.b64encode(nonce + ciphertext).decode("utf-8")

    def decrypt(self, encrypted_data: str) -> str:
        """
        Decrypt base64-encoded ciphertext including the 12-byte nonce.
        Verifies authentication tag and returns original string.
        Raises ValueError on tampered data or decryption failures.
        """
        if not encrypted_data:
            raise ValueError("Encrypted data cannot be empty.")
        try:
            data = base64.b64decode(encrypted_data.encode("utf-8"))
            if len(data) < 12:
                raise ValueError("Ciphertext too short to contain valid nonce.")
            nonce, ciphertext = data[:12], data[12:]
            decrypted = self.aesgcm.decrypt(nonce, ciphertext, None)
            return decrypted.decode("utf-8")
        except (ValueError, InvalidTag, TypeError) as e:
            raise ValueError("Decryption failed: invalid ciphertext or authentication tag.") from e


def encrypt_rtsp_url(url: str, secret_key: Optional[str] = None) -> str:
    """Encrypt an RTSP URL using the global RTSP secret key."""
    key = secret_key or settings.RTSP_SECRET_KEY
    crypto = AESGCMCrypto(secret_key=key)
    return crypto.encrypt(url)


def decrypt_rtsp_url(encrypted_url: str, secret_key: Optional[str] = None) -> str:
    """Decrypt an encrypted RTSP URL token using the global RTSP secret key."""
    key = secret_key or settings.RTSP_SECRET_KEY
    crypto = AESGCMCrypto(secret_key=key)
    return crypto.decrypt(encrypted_url)
