package com.plomienkostrze.news;

/**
 * Lifecycle status of a news post. S-01 knows only PUBLISHED; later slices
 * (S-02 drafts, S-03 generation proposals) add values — not columns.
 */
public enum NewsPostStatus {
	PUBLISHED
}
