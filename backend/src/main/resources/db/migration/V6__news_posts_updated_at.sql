-- S-04 (FR-007): track the last edit time of a post. Nullable — existing rows
-- get NULL (semantics: never edited). Additive and backward-compatible; the
-- V5 CHECK on published_at is untouched because edits don't change status or
-- published_at. Required by ddl-auto=validate once NewsPost carries updated_at.
ALTER TABLE news_posts
    ADD COLUMN updated_at TIMESTAMPTZ;
