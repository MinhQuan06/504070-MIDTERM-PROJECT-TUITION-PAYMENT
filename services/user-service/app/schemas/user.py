from pydantic import BaseModel, ConfigDict, EmailStr


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    expires_in: int


class UserMe(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    user_id: int
    username: str
    full_name: str
    phone: str | None = None
    email: EmailStr
    role: str


class InternalUserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    user_id: int
    full_name: str
    email: EmailStr
    phone: str | None = None


class AdminUserResponse(UserMe):
    is_active: bool
