from sqlalchemy import select

from app.core.security import hash_password
from app.database import SessionLocal
from app.models.user import User


DEMO_ACCOUNTS = [
    {
        "username": "demo",
        "password": "Demo123",
        "full_name": "Demo Student",
        "phone": "0900000000",
        "email": "demo@example.com",
        "role": "student",
    },
    {
        "username": "admin",
        "password": "AdminDemo123",
        "full_name": "Demo Administrator",
        "phone": "0900000001",
        "email": "admin@example.com",
        "role": "admin",
    },
]


def main() -> None:
    with SessionLocal() as db:
        for item in DEMO_ACCOUNTS:
            existing = db.scalar(select(User).where(User.username == item["username"]))
            if existing:
                print(f"Skipped existing account: {item['username']}")
                continue
            db.add(
                User(
                    username=item["username"],
                    password_hash=hash_password(item["password"]),
                    full_name=item["full_name"],
                    phone=item["phone"],
                    email=item["email"],
                    role=item["role"],
                    is_active=True,
                    failed_login_attempts=0,
                )
            )
            print(f"Created demo account: {item['username']} (role={item['role']})")
        db.commit()

    print("Seed complete. Demo passwords are for local development only.")


if __name__ == "__main__":
    main()
