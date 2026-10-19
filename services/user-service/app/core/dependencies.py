from collections.abc import Callable
import secrets

import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import APIKeyHeader, OAuth2PasswordBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models.user import User


oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login", auto_error=False)
internal_api_key_header = APIKeyHeader(name="X-Internal-API-Key", auto_error=False)


def _unauthorized() -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Invalid or expired authentication credentials.",
        headers={"WWW-Authenticate": "Bearer"},
    )


def get_current_user(
    token: str | None = Depends(oauth2_scheme),
    db: Session = Depends(get_db),
) -> User:
    """Validate JWT cryptographically, then load current account status/role from MySQL."""
    if not token:
        raise _unauthorized()

    try:
        payload = jwt.decode(
            token,
            settings.JWT_SECRET_KEY,
            algorithms=["HS256"],
            issuer=settings.JWT_ISSUER,
            audience=settings.JWT_AUDIENCE,
            options={"require": ["exp", "iat", "sub", "iss", "aud", "jti", "type"]},
        )
        if payload.get("type") != "access":
            raise _unauthorized()
        user_id = int(payload["sub"])
        if user_id <= 0:
            raise _unauthorized()
    except (jwt.PyJWTError, TypeError, ValueError, KeyError):
        raise _unauthorized() from None

    user = db.scalar(select(User).where(User.user_id == user_id))
    if user is None or not user.is_active:
        raise _unauthorized()
    return user


def require_roles(*allowed_roles: str) -> Callable:
    """Dependency factory for role-based access control (RBAC)."""
    normalized_roles = {role.strip().lower() for role in allowed_roles if role.strip()}
    if not normalized_roles:
        raise ValueError("At least one role must be specified.")

    def role_checker(current_user: User = Depends(get_current_user)) -> User:
        if current_user.role.lower() not in normalized_roles:
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="You do not have permission to access this resource.",
            )
        return current_user

    return role_checker


def require_internal_api_key(
    provided_key: str | None = Depends(internal_api_key_header),
) -> None:
    """Require a service-to-service secret; fail closed when the key is wrong/missing."""
    if not provided_key or not secrets.compare_digest(provided_key, settings.INTERNAL_API_KEY):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal service credentials.",
            headers={"WWW-Authenticate": "ApiKey"},
        )
