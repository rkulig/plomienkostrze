-- E2E test-flow probe table (deploy-plan Phase C). Temporary: dropped by a
-- later migration once real features land. VARCHAR(1024) mirrors the API cap.
CREATE TABLE test_messages (
    id         BIGSERIAL PRIMARY KEY,
    content    VARCHAR(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
