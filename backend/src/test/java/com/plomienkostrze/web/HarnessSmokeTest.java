package com.plomienkostrze.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.plomienkostrze.news.NewsGenerationService;
import com.plomienkostrze.news.NewsPostRepository;
import com.plomienkostrze.security.SecurityConfig;

/**
 * Phase 1 harness smoke test: proves the {@code @WebMvcTest} slice boots the real
 * {@link SecurityConfig} filter chain and that {@code spring-security-test} resolves.
 * The single assertion (anonymous write → 401) is folded into the Phase 2 role matrix;
 * this class exists only to fail fast if the harness itself regresses.
 */
@WebMvcTest
@Import({ SecurityConfig.class, CorsConfig.class })
class HarnessSmokeTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private NewsPostRepository repository;

	@MockitoBean
	private NewsGenerationService generationService;

	@Test
	void anonymousWriteIsUnauthorized() throws Exception {
		mvc.perform(post("/api/news-posts"))
				.andExpect(status().isUnauthorized());
	}
}
