-- S-02: close the S-01 review invariant (F3) at the database level — a
-- PUBLISHED post must carry published_at (defense in depth next to the
-- NewsPost.published() factory). Additive and backward-compatible: the
-- previous revision has no write path and all seeded rows have published_at.
ALTER TABLE news_posts
    ADD CONSTRAINT chk_news_posts_published_at
    CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL);
