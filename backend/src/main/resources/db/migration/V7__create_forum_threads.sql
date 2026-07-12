-- Forum threads (roadmap S-07, FR-014). The whole forum sits behind login, so
-- there is no status column — a thread is visible to any authenticated fan the
-- moment it is created. author_display_name is a denormalized JWT snapshot (the
-- safe label: name → email local-part → 'Kibic'); the raw email never persists.
-- post_count and last_activity_at are denormalized on the thread so rendering
-- the list needs no per-thread count/scan. The index backs the only list query
-- (newest activity first). Column lengths mirror ForumThread so Postgres, the
-- Hibernate validator (ddl-auto=validate) and H2 tests (create-drop) agree.
CREATE TABLE forum_threads (
    id                  BIGSERIAL      PRIMARY KEY,
    title               VARCHAR(200)   NOT NULL,
    author_uid          VARCHAR(128)   NOT NULL,
    author_display_name VARCHAR(100)   NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    last_activity_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    post_count          INT            NOT NULL DEFAULT 1
);
CREATE INDEX idx_forum_threads_last_activity_at
    ON forum_threads (last_activity_at DESC);
