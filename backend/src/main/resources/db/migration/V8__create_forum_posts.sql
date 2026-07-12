-- Forum posts (roadmap S-07, FR-014). Flat, chronological replies; the thread
-- opener is just the first post. thread_id is a plain FK to forum_threads
-- (matching the news codebase's avoidance of heavy JPA associations). body is
-- VARCHAR(10000) to match ForumPost and the news content contract. The index
-- backs the only detail query (a thread's posts, oldest first). New tables only
-- — forward-only and backward-compatible against the previous app revision.
CREATE TABLE forum_posts (
    id                  BIGSERIAL      PRIMARY KEY,
    thread_id           BIGINT         NOT NULL REFERENCES forum_threads (id),
    author_uid          VARCHAR(128)   NOT NULL,
    author_display_name VARCHAR(100)   NOT NULL,
    body                VARCHAR(10000) NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);
CREATE INDEX idx_forum_posts_thread_id_created_at
    ON forum_posts (thread_id, created_at);
