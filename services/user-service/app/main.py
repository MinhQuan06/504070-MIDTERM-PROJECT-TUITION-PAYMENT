from contextlib import asynccontextmanager

from fastapi import FastAPI
from slowapi import _rate_limit_exceeded_handler
from slowapi.errors import RateLimitExceeded

from app.api import admin, auth, users
from app.config import settings
from app.core.rate_limit import limiter
from app.database import Base, engine
from app.models import User  # noqa: F401 - registers the SQLAlchemy model with Base.


@asynccontextmanager
async def lifespan(_: FastAPI):
    # Disabled by default. Create the schema by importing db/user_db.sql before startup.
    if settings.AUTO_CREATE_TABLES:
        Base.metadata.create_all(bind=engine)
    yield


app = FastAPI(
    title="iBanking User/Auth Service",
    version="1.1.0",
    description=(
        "Authentication and user profile service. Internal endpoints must not be "
        "exposed through the public API Gateway."
    ),
    lifespan=lifespan,
)
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

app.include_router(auth.router)
app.include_router(users.router)
app.include_router(admin.router)


@app.get("/health", tags=["Health"], summary="Basic liveness check")
def health() -> dict[str, str]:
    return {"status": "ok"}
