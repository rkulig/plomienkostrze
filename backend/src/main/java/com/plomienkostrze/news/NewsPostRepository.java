package com.plomienkostrze.news;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {

	Page<NewsPost> findByStatus(NewsPostStatus status, Pageable pageable);
}
