package com.plomienkostrze.forum;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForumThreadRepository extends JpaRepository<ForumThread, Long> {

	Page<ForumThread> findAllByOrderByLastActivityAtDesc(Pageable pageable);
}
