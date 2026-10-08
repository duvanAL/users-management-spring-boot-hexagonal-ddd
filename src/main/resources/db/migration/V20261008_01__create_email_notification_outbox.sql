CREATE TABLE IF NOT EXISTS email_notification_outbox (
    notification_id UUID PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    destination_email VARCHAR(320) NOT NULL,
    destination_name VARCHAR(200),
    subject VARCHAR(500) NOT NULL,
    html_content TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_email_notification_outbox_due
    ON email_notification_outbox (next_attempt_at, created_at);
