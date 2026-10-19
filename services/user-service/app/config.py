from functools import lru_cache

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Application settings loaded from environment variables or .env."""

    DATABASE_URL: str
    JWT_SECRET_KEY: str = Field(min_length=32)
    JWT_ISSUER: str = "ibanking-user-auth"
    JWT_AUDIENCE: str = "ibanking-api"
    JWT_EXPIRE_MINUTES: int = Field(default=30, ge=5, le=120)
    INTERNAL_API_KEY: str = Field(min_length=32)
    AUTO_CREATE_TABLES: bool = False

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    @field_validator("JWT_SECRET_KEY", "INTERNAL_API_KEY")
    @classmethod
    def reject_example_secrets(cls, value: str) -> str:
        upper_value = value.upper()
        if any(marker in upper_value for marker in ("CHANGE_ME", "THAY_BANG", "REPLACE_ME")):
            raise ValueError("Replace example secrets with random values before starting the service.")
        return value


@lru_cache

def get_settings() -> Settings:
    return Settings()


settings = get_settings()
