-- News posts (roadmap S-01). The status column ships now so S-02/S-03 add enum
-- values, not columns; the index backs the only list query (PUBLISHED, newest
-- first). content is VARCHAR(10000) to keep Postgres, the Hibernate validator
-- (ddl-auto=validate) and H2 tests (create-drop) in agreement.
CREATE TABLE news_posts (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(200)   NOT NULL,
    content      VARCHAR(10000) NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    published_at TIMESTAMPTZ,            -- NULL for future drafts/proposals
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_news_posts_status_published_at
    ON news_posts (status, published_at DESC);
