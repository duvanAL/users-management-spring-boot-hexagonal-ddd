CREATE TABLE IF NOT EXISTS domain_event_outbox (
  event_id UUID PRIMARY KEY,
  event_json TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_domain_event_outbox_due ON domain_event_outbox(next_attempt_at, created_at);
