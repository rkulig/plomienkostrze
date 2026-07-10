package com.plomienkostrze.news;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A club news post (roadmap S-01). Mirrors migration V2__create_news_posts.sql;
 * content length 10000 must match the migration so ddl-auto=validate passes.
 */
@Entity
@Table(name = "news_posts")
public class NewsPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(nullable = false, length = 10000)
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NewsPostStatus status;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at")
	private Instant updatedAt;

	protected NewsPost() {
		// JPA
	}

	/**
	 * Creates a post published in one step (roadmap S-02, FR-006). The factory
	 * guarantees the S-01 review invariant: a PUBLISHED post always carries
	 * published_at (a NULL would sort first under DESC and render an empty date).
	 */
	public static NewsPost published(String title, String content) {
		NewsPost post = new NewsPost();
		post.title = title;
		post.content = content;
		post.status = NewsPostStatus.PUBLISHED;
		post.publishedAt = Instant.now();
		return post;
	}

	/**
	 * Edits the post in place (roadmap S-04, FR-007): the only mutation path,
	 * kept intentional instead of exposing setters. Replaces title and content
	 * and stamps updated_at; deliberately leaves status and published_at alone
	 * so the post keeps its list position and the V5 CHECK stays satisfied.
	 */
	public void edit(String title, String content) {
		this.title = title;
		this.content = content;
		this.updatedAt = Instant.now();
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getContent() {
		return content;
	}

	public NewsPostStatus getStatus() {
		return status;
	}

	public Instant getPublishedAt() {
		return publishedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
