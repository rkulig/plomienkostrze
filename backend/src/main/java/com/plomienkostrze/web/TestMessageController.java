package com.plomienkostrze.web;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.plomienkostrze.testmessage.TestMessage;
import com.plomienkostrze.testmessage.TestMessageRepository;

/**
 * E2E test-flow probe (deploy-plan Phase C): accepts a simple text from the
 * SPA and persists it, proving the full SPA -> API -> Cloud SQL data path.
 * Temporary — removed once real features land.
 */
@RestController
@RequestMapping("/api/test-messages")
public class TestMessageController {

	private static final int MAX_CONTENT_LENGTH = 1024;

	private final TestMessageRepository repository;

	public TestMessageController(TestMessageRepository repository) {
		this.repository = repository;
	}

	public record CreateRequest(String content) {
	}

	public record MessageResponse(Long id, String content, Instant createdAt) {

		static MessageResponse from(TestMessage message) {
			return new MessageResponse(message.getId(), message.getContent(), message.getCreatedAt());
		}
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MessageResponse create(@RequestBody CreateRequest request) {
		String content = request.content() == null ? "" : request.content().trim();
		if (content.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content must not be blank");
		}
		if (content.length() > MAX_CONTENT_LENGTH) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"content must be at most " + MAX_CONTENT_LENGTH + " characters");
		}
		return MessageResponse.from(repository.save(new TestMessage(content)));
	}

	@GetMapping
	public List<MessageResponse> latest() {
		return repository.findTop10ByOrderByIdDesc().stream().map(MessageResponse::from).toList();
	}
}
