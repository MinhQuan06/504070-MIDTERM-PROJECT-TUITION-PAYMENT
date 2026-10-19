from fastapi import APIRouter, Depends, Query
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.dependencies import require_roles
from app.database import get_db
from app.models.user import User
from app.schemas.user import AdminUserResponse


router = APIRouter(prefix="/api/v1/admin", tags=["Administration"])


@router.get(
    "/users",
    response_model=list[AdminUserResponse],
    summary="List users (admin role required)",
)
def list_users(
    db: Session = Depends(get_db),
    _admin: User = Depends(require_roles("admin")),
    limit: int = Query(default=50, ge=1, le=100),
    offset: int = Query(default=0, ge=0),
) -> list[User]:
    return list(
        db.scalars(
            select(User).order_by(User.user_id).offset(offset).limit(limit)
        ).all()
    )
