package com.plomienkostrze.forum;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A single message in a thread (roadmap S-07). The thread opener is just the
 * first post. Mirrors migration V8__create_forum_posts.sql; column lengths must
 * match so ddl-auto=validate passes.
 *
 * <p>The link to the parent thread is a plain {@code thread_id} FK column (a
 * {@code Long}), not a heavy {@code @ManyToOne} association — matching the news
 * codebase's avoidance of JPA associations and keeping the read queries explicit.
 */
@Entity
@Table(name = "forum_posts")
public class ForumPost {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "thread_id", nullable = false)
	private Long threadId;

	@Column(name = "author_uid", nullable = false, length = 128)
	private String authorUid;

	@Column(name = "author_display_name", nullable = false, length = 100)
	private String authorDisplayName;

	@Column(nullable = false, length = 10000)
	private String body;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ForumPost() {
		// JPA
	}

	/** Creates a post in the given thread (also used for the thread opener). */
	public static ForumPost in(Long threadId, String authorUid, String displayName, String body) {
		ForumPost post = new ForumPost();
		post.threadId = threadId;
		post.authorUid = authorUid;
		post.authorDisplayName = displayName;
		post.body = body;
		return post;
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

	public Long getThreadId() {
		return threadId;
	}

	public String getAuthorUid() {
		return authorUid;
	}

	public String getAuthorDisplayName() {
		return authorDisplayName;
	}

	public String getBody() {
		return body;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
