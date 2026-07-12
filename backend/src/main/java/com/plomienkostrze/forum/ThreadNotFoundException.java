package com.plomienkostrze.forum;

/**
 * Thrown by {@link ForumService} when a thread id does not resolve. The web
 * layer maps it to HTTP 404 — the domain layer stays HTTP-agnostic.
 */
public class ThreadNotFoundException extends RuntimeException {

	public ThreadNotFoundException(Long threadId) {
		super("forum thread not found: " + threadId);
	}
}
