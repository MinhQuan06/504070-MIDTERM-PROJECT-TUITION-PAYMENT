"""Small unit tests for token and password helpers (no MySQL needed)."""
from datetime import datetime, timedelta, timezone
import os

# Settings are read during imports. Set test-only values before importing app code.
os.environ.setdefault("DATABASE_URL", "mysql+pymysql://test:test@127.0.0.1:3306/user_db")
os.environ.setdefault("JWT_SECRET_KEY", "unit-test-jwt-secret-0123456789-abcdef")
os.environ.setdefault("JWT_ISSUER", "ibanking-user-auth")
os.environ.setdefault("JWT_AUDIENCE", "ibanking-api")
os.environ.setdefault("JWT_EXPIRE_MINUTES", "30")
os.environ.setdefault("INTERNAL_API_KEY", "unit-test-internal-secret-0123456789-abcdef")
os.environ.setdefault("AUTO_CREATE_TABLES", "false")

import jwt

from app.config import settings
from app.core.security import create_access_token, hash_password, verify_password


def test_password_hash_and_verify() -> None:
    hashed = hash_password("UnitTest123!")
    assert hashed != "UnitTest123!"
    assert verify_password("UnitTest123!", hashed)
    assert not verify_password("WrongPassword", hashed)


def test_access_token_contains_required_claims() -> None:
    token = create_access_token(42)
    payload = jwt.decode(
        token,
        settings.JWT_SECRET_KEY,
        algorithms=["HS256"],
        issuer=settings.JWT_ISSUER,
        audience=settings.JWT_AUDIENCE,
    )
    assert payload["sub"] == "42"
    assert payload["type"] == "access"
    assert "exp" in payload and "jti" in payload
