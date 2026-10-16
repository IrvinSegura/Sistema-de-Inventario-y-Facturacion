-- SUPERVISOR: runs the store like an admin but can't manage user accounts (see Role).
ALTER TABLE app_user DROP CONSTRAINT ck_app_user_role;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'SUPERVISOR', 'SELLER'));
