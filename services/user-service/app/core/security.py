from datetime import datetime, timedelta, timezone
import secrets

import bcrypt
import jwt

from app.config import settings


# Generate once on process start. It is only used to make unknown-user login
# attempts perform a password check similar to known-user attempts.
_DUMMY_PASSWORD_HASH = bcrypt.hashpw(
    b"not-a-real-user-password",
    bcrypt.gensalt(rounds=12),
).decode("utf-8")


def hash_password(password: str) -> str:
    raw_password = password.encode("utf-8")
    if not raw_password or len(raw_password) > 72:
        raise ValueError("Password must be between 1 and 72 UTF-8 bytes when using bcrypt.")
    return bcrypt.hashpw(raw_password, bcrypt.gensalt(rounds=12)).decode("utf-8")


def verify_password(password: str, password_hash: str) -> bool:
    try:
        raw_password = password.encode("utf-8")
        if len(raw_password) > 72:
            return False
        return bcrypt.checkpw(raw_password, password_hash.encode("utf-8"))
    except (ValueError, TypeError, UnicodeError):
        return False


def verify_unknown_user_password(password: str) -> None:
    """Perform one bcrypt check for an unknown username to reduce timing leaks."""
    verify_password(password, _DUMMY_PASSWORD_HASH)


def create_access_token(user_id: int) -> str:
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(minutes=settings.JWT_EXPIRE_MINUTES)
    payload = {
        "sub": str(user_id),
        "iss": settings.JWT_ISSUER,
        "aud": settings.JWT_AUDIENCE,
        "iat": now,
        "exp": expires_at,
        "jti": secrets.token_urlsafe(16),
        "type": "access",
    }
    return jwt.encode(payload, settings.JWT_SECRET_KEY, algorithm="HS256")
