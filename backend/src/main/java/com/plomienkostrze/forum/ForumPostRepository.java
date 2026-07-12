package com.plomienkostrze.forum;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {

	Page<ForumPost> findByThreadIdOrderByCreatedAtAsc(Long threadId, Pageable pageable);
}
