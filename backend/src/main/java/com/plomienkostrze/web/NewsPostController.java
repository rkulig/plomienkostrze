package com.plomienkostrze.web;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
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

import com.plomienkostrze.news.NewsPost;
import com.plomienkostrze.news.NewsPostRepository;
import com.plomienkostrze.news.NewsPostStatus;

/**
 * Public, unauthenticated read API for news posts (roadmap S-01): a paginated
 * list of PUBLISHED posts (newest first) and a single-post detail. The list
 * carries server-computed excerpts, not full bodies — the same contract future
 * mobile clients consume.
 */
@RestController
@RequestMapping("/api/news-posts")
public class NewsPostController {

	private static final int MAX_PAGE_SIZE = 50;
	private static final int EXCERPT_MAX_LENGTH = 200;

	private final NewsPostRepository repository;

	public NewsPostController(NewsPostRepository repository) {
		this.repository = repository;
	}

	public record SummaryResponse(Long id, String title, Instant publishedAt, String excerpt) {

		static SummaryResponse from(NewsPost post) {
			return new SummaryResponse(post.getId(), post.getTitle(), post.getPublishedAt(),
					excerptOf(post.getContent()));
		}
	}

	public record ListResponse(List<SummaryResponse> items, long total) {
	}

	public record DetailResponse(Long id, String title, Instant publishedAt, String content) {

		static DetailResponse from(NewsPost post) {
			return new DetailResponse(post.getId(), post.getTitle(), post.getPublishedAt(), post.getContent());
		}
	}

	public record CreateRequest(
			@NotBlank @Size(max = 200) String title,
			@NotBlank @Size(max = 10000) String content) {
	}

	@GetMapping
	public ListResponse list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		if (page < 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"size must be between 1 and " + MAX_PAGE_SIZE);
		}
		Page<NewsPost> result = repository.findByStatus(NewsPostStatus.PUBLISHED,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt")));
		return new ListResponse(result.getContent().stream().map(SummaryResponse::from).toList(),
				result.getTotalElements());
	}

	/**
	 * One-step publish (roadmap S-02, FR-006): the post is created already
	 * PUBLISHED with published_at set. Requires ROLE_ADMIN (see SecurityConfig).
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public DetailResponse create(@Valid @RequestBody CreateRequest request) {
		NewsPost post = repository.save(NewsPost.published(request.title(), request.content()));
		return DetailResponse.from(post);
	}

	@GetMapping("/{id}")
	public DetailResponse get(@PathVariable Long id) {
		return repository.findById(id)
				.filter(post -> post.getStatus() == NewsPostStatus.PUBLISHED)
				.map(DetailResponse::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "news post not found"));
	}

	/** First paragraph (up to the first blank line), truncated to 200 chars with an ellipsis. */
	private static String excerptOf(String content) {
		String firstParagraph = content.strip().split("\\n\\s*\\n", 2)[0].strip();
		if (firstParagraph.length() <= EXCERPT_MAX_LENGTH) {
			return firstParagraph;
		}
		return firstParagraph.substring(0, EXCERPT_MAX_LENGTH).stripTrailing() + "…";
	}
}
