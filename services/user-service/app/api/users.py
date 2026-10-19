from fastapi import APIRouter, Depends, HTTPException, Path, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.dependencies import get_current_user, require_internal_api_key
from app.database import get_db
from app.models.user import User
from app.schemas.user import InternalUserResponse, UserMe


router = APIRouter(tags=["Users"])


@router.get("/api/v1/users/me", response_model=UserMe, summary="Get the authenticated user's profile")
def get_me(current_user: User = Depends(get_current_user)) -> UserMe:
    return current_user


@router.get(
    "/internal/users/{user_id}",
    response_model=InternalUserResponse,
    summary="Get a user's payment contact details (internal service only)",
    dependencies=[Depends(require_internal_api_key)],
)
def get_internal_user(
    user_id: int = Path(gt=0),
    db: Session = Depends(get_db),
) -> User:
    user = db.scalar(select(User).where(User.user_id == user_id))
    if user is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")
    if not user.is_active:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")
    return user
