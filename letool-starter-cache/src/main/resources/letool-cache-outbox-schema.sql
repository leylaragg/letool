CREATE TABLE letool_cache_outbox (
    event_id VARCHAR(64) PRIMARY KEY,
    cache_name VARCHAR(200) NOT NULL,
    serialized_key VARCHAR(1000) NOT NULL,
    fence_token VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL,
    next_attempt_at TIMESTAMP NOT NULL,
    lease_owner VARCHAR(100),
    lease_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_letool_cache_outbox_claim
    ON letool_cache_outbox (status, next_attempt_at, lease_until, created_at);
