package com.plomienkostrze.testmessage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TestMessageRepository extends JpaRepository<TestMessage, Long> {

	List<TestMessage> findTop10ByOrderByIdDesc();
}
