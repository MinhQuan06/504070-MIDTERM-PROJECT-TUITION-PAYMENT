from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone

from fastapi import Depends, FastAPI, Form, HTTPException, Path, Query, Request, status
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.core.dependencies import get_current_user, require_internal_api_key, require_roles
from app.core.rate_limit import limiter
from app.core.security import (
    create_access_token,
    verify_password,
    verify_unknown_user_password,
)
from app.database import Base, engine, get_db
from app.models.user import User
from app.schemas.user import AdminUserResponse, InternalUserResponse, TokenResponse, UserMe


# Số lần đăng nhập sai tối đa trước khi khóa tạm tài khoản.
MAX_FAILED_LOGIN_ATTEMPTS = 5
LOCKOUT_MINUTES = 15
GENERIC_LOGIN_ERROR = "Invalid username or password."


# Khởi tạo FastAPI và cấu hình chung-----------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(_: FastAPI):
    # Mặc định False: database được tạo bằng file db/user_db.sql.
    # Chỉ bật AUTO_CREATE_TABLES=true khi có chủ đích tạo bảng từ SQLAlchemy.
    if settings.AUTO_CREATE_TABLES:
        Base.metadata.create_all(bind=engine)
    yield


app = FastAPI(
    title="iBanking User/Auth Service",
    version="1.2.0",
    description="Đăng nhập, cấp JWT và cung cấp thông tin người dùng cho iBanking.",
    lifespan=lifespan,
)

# Bật giới hạn số request và trả response 429 khi vượt giới hạn.
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)


# 1. Authentication API -----------------------------------------------------------------------------

@app.post(
    "/api/v1/auth/login",
    response_model=TokenResponse,
    tags=["Authentication"],
    summary="Đăng nhập và nhận access token",
)
@limiter.limit("10/minute")
def login(
        request: Request,
        # Chỉ yêu cầu hai trường chính trên form login, không cần người dùng nhập các
        # trường OAuth2 phụ như scope, client_id hoặc client_secret.
        username: str = Form(min_length=1, max_length=50),
        password: str = Form(
            min_length=1,
            json_schema_extra={"format": "password"}
        ),
        db: Session = Depends(get_db),
) -> TokenResponse:
    """Kiểm tra tài khoản/mật khẩu, áp dụng khóa tạm và cấp JWT."""
    username = username.strip()
    user = db.scalar(select(User).where(User.username == username))
    # MySQL DATETIME không lưu timezone nên so sánh bằng datetime UTC dạng naive.
    now = datetime.now(timezone.utc).replace(tzinfo=None)

    if user is None:
        # Tốn thời gian bcrypt tương tự để hạn chế dò xem username có tồn tại.
        verify_unknown_user_password(password)
        raise HTTPException(status_code=401, detail=GENERIC_LOGIN_ERROR)

    password_is_valid = verify_password(password, user.password_hash)
    is_locked = user.locked_until is not None and user.locked_until > now

    # Dùng cùng thông báo để không tiết lộ tài khoản sai, bị khóa hoặc bị vô hiệu hóa.
    if is_locked or not user.is_active or not password_is_valid:
        if not is_locked and user.is_active and not password_is_valid:
            user.failed_login_attempts = (user.failed_login_attempts or 0) + 1
            if user.failed_login_attempts >= MAX_FAILED_LOGIN_ATTEMPTS:
                user.locked_until = now + timedelta(minutes=LOCKOUT_MINUTES)
            db.add(user)
            db.commit()
        raise HTTPException(status_code=401, detail=GENERIC_LOGIN_ERROR)

    # Đăng nhập thành công thì xóa bộ đếm đăng nhập sai.
    user.failed_login_attempts = 0
    user.locked_until = None
    db.add(user)
    db.commit()

    return TokenResponse(
        access_token=create_access_token(user.user_id),
        token_type="bearer",
        expires_in=settings.JWT_EXPIRE_MINUTES * 60,
    )


# 2. User API: xem hồ sơ của tài khoản đang đăng nhập-----------------------------------------------------------------------------

@app.get(
    "/api/v1/users/me",
    response_model=UserMe,
    tags=["Users"],
    summary="Lấy hồ sơ người dùng hiện tại",
)
def get_my_profile(current_user: User = Depends(get_current_user)) -> User:
    """Chỉ trả thông tin của user gắn với JWT hiện tại."""
    return current_user


# 3. Internal API: service khác lấy thông tin liên hệ của user-----------------------------------------------------------------------------

@app.get(
    "/internal/users/{user_id}",
    response_model=InternalUserResponse,
    tags=["Users"],
    summary="Lấy thông tin liên hệ của user (chỉ dành cho service nội bộ)",
    dependencies=[Depends(require_internal_api_key)],
)
def get_internal_user(
        user_id: int = Path(gt=0),
        db: Session = Depends(get_db),
) -> User:
    """Payment Service có thể dùng endpoint này để lấy email/SĐT người nộp tiền."""
    user = db.scalar(select(User).where(User.user_id == user_id))
    if user is None or not user.is_active:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found.")
    return user


# 4. Administration API: chỉ admin được liệt kê người dùng-----------------------------------------------------------------------------

@app.get(
    "/api/v1/admin/users",
    response_model=list[AdminUserResponse],
    tags=["Administration"],
    summary="Lấy danh sách người dùng (yêu cầu role admin)",
)
def list_users(
        db: Session = Depends(get_db),
        _admin: User = Depends(require_roles("admin")),
        limit: int = Query(default=50, ge=1, le=100),
        offset: int = Query(default=0, ge=0),
) -> list[User]:
    """Trả danh sách user có phân trang; schema không để lộ password_hash."""
    users = db.scalars(
        select(User).order_by(User.user_id).offset(offset).limit(limit)
    ).all()
    return list(users)


# 5. Health API: kiểm tra service có phản hồi-----------------------------------------------------------------------------

@app.get("/health", tags=["Health"], summary="Kiểm tra trạng thái service")
def health() -> dict[str, str]:
    # Endpoint liveness đơn giản; không kiểm tra sâu kết nối MySQL.
    return {"status": "ok"}
