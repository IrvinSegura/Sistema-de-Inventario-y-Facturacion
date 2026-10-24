-- An admin can reset a user's password to a temporary one, which the user must change before doing
-- anything else (PasswordChangeInterceptor). app_user is small and rarely written; the constant default is a
-- catalog change (no rewrite) and covers the old instance's inserts during a deploy.
ALTER TABLE app_user ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT false;
