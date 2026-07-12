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
 * A forum discussion thread (roadmap S-07). Mirrors migration
 * V7__create_forum_threads.sql; every {@code @Column} length/nullable must match
 * the migration so ddl-auto=validate passes in production.
 *
 * <p>Immutable-by-convention (getters only, no setters): the only mutation path
 * is {@link #registerReply(Instant)}, called by ForumService inside the reply
 * transaction to keep {@code last_activity_at} and {@code post_count}
 * denormalized on the thread (avoids an N+1 count when rendering the list).
 */
@Entity
@Table(name = "forum_threads")
public class ForumThread {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(name = "author_uid", nullable = false, length = 128)
	private String authorUid;

	@Column(name = "author_display_name", nullable = false, length = 100)
	private String authorDisplayName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "last_activity_at", nullable = false)
	private Instant lastActivityAt;

	@Column(name = "post_count", nullable = false)
	private int postCount;

	protected ForumThread() {
		// JPA
	}

	/**
	 * Opens a new thread. The opening post counts as the first post, so
	 * {@code post_count} starts at 1 and {@code last_activity_at} equals
	 * {@code created_at} (both stamped in {@link #onCreate()}).
	 */
	public static ForumThread openedBy(String title, String authorUid, String displayName) {
		ForumThread thread = new ForumThread();
		thread.title = title;
		thread.authorUid = authorUid;
		thread.authorDisplayName = displayName;
		thread.postCount = 1;
		return thread;
	}

	/**
	 * Records a reply landing at {@code at}: bumps {@code last_activity_at} so the
	 * thread bubbles to the top of the list and increments {@code post_count}.
	 * Package-visible — only ForumService calls it, inside the reply transaction.
	 */
	void registerReply(Instant at) {
		this.lastActivityAt = at;
		this.postCount++;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
		if (lastActivityAt == null) {
			lastActivityAt = createdAt;
		}
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getAuthorUid() {
		return authorUid;
	}

	public String getAuthorDisplayName() {
		return authorDisplayName;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getLastActivityAt() {
		return lastActivityAt;
	}

	public int getPostCount() {
		return postCount;
	}
}
