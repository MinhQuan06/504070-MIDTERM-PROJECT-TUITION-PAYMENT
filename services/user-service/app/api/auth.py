from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.security import OAuth2PasswordRequestForm
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.core.rate_limit import limiter
from app.core.security import create_access_token, verify_password, verify_unknown_user_password
from app.database import get_db
from app.models.user import User
from app.schemas.user import TokenResponse


router = APIRouter(prefix="/api/v1/auth", tags=["Authentication"])
MAX_FAILED_LOGIN_ATTEMPTS = 5
LOCKOUT_MINUTES = 15
GENERIC_LOGIN_ERROR = "Invalid username or password."


@router.post("/login", response_model=TokenResponse, summary="Log in and receive an access token")
@limiter.limit("10/minute")
def login(
    request: Request,
    form_data: OAuth2PasswordRequestForm = Depends(),
    db: Session = Depends(get_db),
) -> TokenResponse:
    username = form_data.username.strip()
    user = db.scalar(select(User).where(User.username == username).with_for_update())
    now = datetime.now(timezone.utc).replace(tzinfo=None)  # Naive UTC for MySQL DATETIME.

    if user is None:
        verify_unknown_user_password(form_data.password)
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=GENERIC_LOGIN_ERROR)

    password_is_valid = verify_password(form_data.password, user.password_hash)
    is_locked = user.locked_until is not None and user.locked_until > now

    # Same response for wrong password, locked account, and inactive account to
    # avoid revealing account status to an unauthenticated caller.
    if is_locked or not user.is_active or not password_is_valid:
        if not is_locked and user.is_active and not password_is_valid:
            user.failed_login_attempts = (user.failed_login_attempts or 0) + 1
            if user.failed_login_attempts >= MAX_FAILED_LOGIN_ATTEMPTS:
                user.locked_until = now + timedelta(minutes=LOCKOUT_MINUTES)
            db.add(user)
            db.commit()
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail=GENERIC_LOGIN_ERROR)

    # Successful authentication clears the account-level failure counter.
    user.failed_login_attempts = 0
    user.locked_until = None
    db.add(user)
    db.commit()

    return TokenResponse(
        access_token=create_access_token(user.user_id),
        token_type="bearer",
        expires_in=settings.JWT_EXPIRE_MINUTES * 60,
    )
