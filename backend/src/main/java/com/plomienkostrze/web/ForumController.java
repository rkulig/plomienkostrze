package com.plomienkostrze.web;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.plomienkostrze.forum.ForumPost;
import com.plomienkostrze.forum.ForumService;
import com.plomienkostrze.forum.ForumService.ThreadWithPosts;
import com.plomienkostrze.forum.ForumThread;
import com.plomienkostrze.forum.ThreadNotFoundException;

/**
 * Login-gated forum API (roadmap S-07): the whole subtree is
 * {@code .authenticated()} (see SecurityConfig) — reads and writes alike are for
 * signed-in fans only, never anonymous callers. Author identity is read from the
 * Firebase JWT ({@code sub} = UID, {@code name}/{@code email} claims); responses
 * carry only the safe {@code authorDisplayName}, never the UID or email.
 */
@RestController
@RequestMapping("/api/forum")
public class ForumController {

	private static final int MAX_PAGE_SIZE = 50;

	private final ForumService forumService;

	public ForumController(ForumService forumService) {
		this.forumService = forumService;
	}

	public record ThreadSummaryResponse(Long id, String title, String authorDisplayName,
			Instant createdAt, Instant lastActivityAt, int postCount) {

		static ThreadSummaryResponse from(ForumThread thread) {
			return new ThreadSummaryResponse(thread.getId(), thread.getTitle(), thread.getAuthorDisplayName(),
					thread.getCreatedAt(), thread.getLastActivityAt(), thread.getPostCount());
		}
	}

	public record ThreadListResponse(List<ThreadSummaryResponse> items, long total) {
	}

	public record PostResponse(Long id, String authorDisplayName, String body, Instant createdAt) {

		static PostResponse from(ForumPost post) {
			return new PostResponse(post.getId(), post.getAuthorDisplayName(), post.getBody(), post.getCreatedAt());
		}
	}

	public record ThreadDetailResponse(Long id, String title, String authorDisplayName,
			Instant createdAt, Instant lastActivityAt, int postCount,
			List<PostResponse> posts, long totalPosts) {

		static ThreadDetailResponse from(ThreadWithPosts result) {
			ForumThread thread = result.thread();
			Page<ForumPost> posts = result.posts();
			return new ThreadDetailResponse(thread.getId(), thread.getTitle(), thread.getAuthorDisplayName(),
					thread.getCreatedAt(), thread.getLastActivityAt(), thread.getPostCount(),
					posts.getContent().stream().map(PostResponse::from).toList(), posts.getTotalElements());
		}
	}

	public record CreateThreadRequest(
			@NotBlank @Size(max = 200) String title,
			@NotBlank @Size(max = 10000) String body) {
	}

	public record CreatePostRequest(
			@NotBlank @Size(max = 10000) String body) {
	}

	@GetMapping("/threads")
	public ThreadListResponse listThreads(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		validatePaging(page, size);
		Page<ForumThread> result = forumService.listThreads(page, size);
		return new ThreadListResponse(result.getContent().stream().map(ThreadSummaryResponse::from).toList(),
				result.getTotalElements());
	}

	@PostMapping("/threads")
	@ResponseStatus(HttpStatus.CREATED)
	public ThreadDetailResponse createThread(@Valid @RequestBody CreateThreadRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		ForumThread thread = forumService.openThread(request.title(), request.body(), jwt.getSubject(),
				jwt.getClaimAsString("name"), jwt.getClaimAsString("email"));
		return ThreadDetailResponse.from(forumService.getThread(thread.getId(), 0, MAX_PAGE_SIZE));
	}

	@GetMapping("/threads/{id}")
	public ThreadDetailResponse getThread(@PathVariable Long id,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		validatePaging(page, size);
		return ThreadDetailResponse.from(forumService.getThread(id, page, size));
	}

	@PostMapping("/threads/{id}/posts")
	@ResponseStatus(HttpStatus.CREATED)
	public PostResponse reply(@PathVariable Long id, @Valid @RequestBody CreatePostRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		ForumPost post = forumService.reply(id, request.body(), jwt.getSubject(),
				jwt.getClaimAsString("name"), jwt.getClaimAsString("email"));
		return PostResponse.from(post);
	}

	private static void validatePaging(int page, int size) {
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"size must be between 1 and " + MAX_PAGE_SIZE);
		}
	}

	@ExceptionHandler(ThreadNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void handleThreadNotFound(ThreadNotFoundException ex) {
		// Maps the domain 404 to HTTP 404; body is the default error contract.
	}
}
