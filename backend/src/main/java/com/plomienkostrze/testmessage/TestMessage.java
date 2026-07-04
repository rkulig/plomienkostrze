package com.plomienkostrze.testmessage;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * E2E test-flow probe (deploy-plan Phase C): a row per text submitted from the
 * SPA's /test-flow view. Temporary — removed once real features land.
 */
@Entity
@Table(name = "test_messages")
public class TestMessage {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 1024)
	private String content;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TestMessage() {
		// JPA
	}

	public TestMessage(String content) {
		this.content = content;
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

	public String getContent() {
		return content;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
