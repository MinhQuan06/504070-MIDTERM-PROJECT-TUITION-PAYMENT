-- Run this ONCE against an existing user_db whose users table predates these fields.
-- Back up the database first. Do not run this migration against a newly created table
-- using the updated db/user_db.sql, because those columns already exist.

USE user_db;

ALTER TABLE users
    ADD COLUMN role VARCHAR(30) NOT NULL DEFAULT 'student' AFTER email,
    ADD COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1 AFTER role,
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0 AFTER is_active,
    ADD COLUMN locked_until DATETIME NULL AFTER failed_login_attempts,
    ADD COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER locked_until,
    ADD KEY ix_users_role (role);
