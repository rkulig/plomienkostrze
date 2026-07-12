package com.plomienkostrze.forum;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the forum's read queries and the two multi-write transactions (roadmap
 * S-07). Thread creation and reply are {@code @Transactional} because each spans
 * more than one row: opening a thread saves the thread plus its opening post;
 * replying saves the post and bumps the parent thread's denormalized
 * {@code last_activity_at} / {@code post_count}.
 *
 * <p>The safe author display label (name → email local-part → "Kibic") is
 * computed here at write time and snapshotted onto every row, so the raw email
 * never persists and never ships to other fans.
 */
@Service
public class ForumService {

	private static final int DISPLAY_NAME_MAX_LENGTH = 100;
	private static final String FALLBACK_LABEL = "Kibic";

	private final ForumThreadRepository threadRepository;
	private final ForumPostRepository postRepository;

	public ForumService(ForumThreadRepository threadRepository, ForumPostRepository postRepository) {
		this.threadRepository = threadRepository;
		this.postRepository = postRepository;
	}

	/** A thread header paired with a page of its posts (oldest first). */
	public record ThreadWithPosts(ForumThread thread, Page<ForumPost> posts) {
	}

	/** Threads ordered by most-recent activity (newest first). */
	public Page<ForumThread> listThreads(int page, int size) {
		return threadRepository.findAllByOrderByLastActivityAtDesc(PageRequest.of(page, size));
	}

	/** A thread header plus a page of its posts; 404 (domain exception) if it doesn't exist. */
	public ThreadWithPosts getThread(Long threadId, int page, int size) {
		ForumThread thread = threadRepository.findById(threadId)
				.orElseThrow(() -> new ThreadNotFoundException(threadId));
		Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"));
		Page<ForumPost> posts = postRepository.findByThreadIdOrderByCreatedAtAsc(threadId, pageable);
		return new ThreadWithPosts(thread, posts);
	}

	/**
	 * Opens a thread with its first post, atomically. {@code post_count} starts at
	 * 1 and {@code last_activity_at} equals {@code created_at} (the opener counts).
	 */
	@Transactional
	public ForumThread openThread(String title, String body, String authorUid, String rawName, String rawEmail) {
		String label = displayLabel(rawName, rawEmail);
		ForumThread thread = threadRepository.save(ForumThread.openedBy(title, authorUid, label));
		postRepository.save(ForumPost.in(thread.getId(), authorUid, label, body));
		return thread;
	}

	/**
	 * Appends a reply and bumps the parent thread's activity, atomically: insert
	 * the post, set {@code last_activity_at = now}, increment {@code post_count}.
	 * 404 (domain exception) if the thread doesn't exist.
	 */
	@Transactional
	public ForumPost reply(Long threadId, String body, String authorUid, String rawName, String rawEmail) {
		ForumThread thread = threadRepository.findById(threadId)
				.orElseThrow(() -> new ThreadNotFoundException(threadId));
		String label = displayLabel(rawName, rawEmail);
		ForumPost post = postRepository.save(ForumPost.in(threadId, authorUid, label, body));
		thread.registerReply(post.getCreatedAt());
		threadRepository.save(thread);
		return post;
	}

	/**
	 * The safe author label: the Google display name if present, else the email's
	 * local-part (before {@code @}), else "Kibic". Truncated to the column length.
	 * The raw email is never persisted — only this derived label leaves the write.
	 */
	private static String displayLabel(String rawName, String rawEmail) {
		String label = null;
		if (rawName != null && !rawName.isBlank()) {
			label = rawName.strip();
		} else if (rawEmail != null && !rawEmail.isBlank()) {
			String localPart = rawEmail.strip().split("@", 2)[0].strip();
			if (!localPart.isEmpty()) {
				label = localPart;
			}
		}
		if (label == null) {
			label = FALLBACK_LABEL;
		}
		return label.length() > DISPLAY_NAME_MAX_LENGTH ? label.substring(0, DISPLAY_NAME_MAX_LENGTH) : label;
	}
}
