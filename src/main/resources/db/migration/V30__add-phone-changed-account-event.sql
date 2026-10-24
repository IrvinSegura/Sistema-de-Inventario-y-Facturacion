-- The phone recovers the password by SMS just like the email does, so changing it is recorded too.
-- account_event is new and small: the check is replaced in one step.
ALTER TABLE account_event
    DROP CONSTRAINT ck_account_event_type,
    ADD CONSTRAINT ck_account_event_type CHECK (type IN ('CREATED', 'EMAIL_CHANGED', 'PHONE_CHANGED',
        'ROLE_CHANGED', 'DEACTIVATED', 'REACTIVATED', 'PASSWORD_RESET', 'PASSWORD_CHANGED', 'PASSWORD_RECOVERED'));
