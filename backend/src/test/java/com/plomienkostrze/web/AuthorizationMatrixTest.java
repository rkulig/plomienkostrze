package com.plomienkostrze.web;

import static com.plomienkostrze.web.MockPrincipals.adminJwt;
import static com.plomienkostrze.web.MockPrincipals.userJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.plomienkostrze.news.NewsGenerationService;
import com.plomienkostrze.news.NewsGenerationService.ProposalDraft;
import com.plomienkostrze.news.NewsPost;
import com.plomienkostrze.news.NewsPostRepository;
import com.plomienkostrze.security.SecurityConfig;

/**
 * Risk #1 (High × High) — the authorization boundary as data. A single
 * {@code @ParameterizedTest} sweeps every caller × endpoint combination so the
 * lockout is asserted structurally: adding an endpoint is one row, and it is
 * hard to omit the anon-401 / non-admin-403 rows that a naive admin-happy-path
 * test would skip.
 *
 * <p>The real {@link com.plomienkostrze.security.SecurityConfig} chain is
 * imported (not stubbed), so a future edit that downgrades an
 * {@code hasRole("ADMIN")} matcher to {@code authenticated}, or adds a write
 * endpoint without a rule, breaks a row here. The repository/service beans are
 * mocked with benign returns so an admin request reaches a business status
 * (201/200/204) rather than a 500 that could be mistaken for a pass.
 */
@WebMvcTest
@Import({ SecurityConfig.class, CorsConfig.class })
class AuthorizationMatrixTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private NewsPostRepository repository;

	@MockitoBean
	private NewsGenerationService generationService;

	@BeforeEach
	void stubBenignBusinessReturns() {
		NewsPost post = NewsPost.published("t", "c");
		given(repository.findByStatus(any(), any())).willReturn(Page.<NewsPost>empty());
		given(repository.findById(anyLong())).willReturn(Optional.of(post));
		given(repository.save(any())).willReturn(post);
		given(repository.existsById(anyLong())).willReturn(true);
		given(generationService.generateFromLastMatch()).willReturn(new ProposalDraft("t", "c"));
	}

	/** How the security chain classifies an endpoint — drives the expected status per caller. */
	private enum Access {
		PERMIT_ALL,
		AUTHENTICATED,
		ADMIN,
		DENY_ALL
	}

	/** The three principals the matrix exercises. */
	private enum Caller {
		ANONYMOUS,
		NON_ADMIN,
		ADMIN;

		/** Fresh post-processor per row; {@code null} means send no credentials. */
		RequestPostProcessor postProcessor() {
			return switch (this) {
				case ANONYMOUS -> null;
				case NON_ADMIN -> userJwt();
				case ADMIN -> adminJwt();
			};
		}
	}

	private record Endpoint(String label, Access access, Supplier<MockHttpServletRequestBuilder> request) {
	}

	private static final String VALID_BODY = "{\"title\":\"t\",\"content\":\"c\"}";

	private static List<Endpoint> endpoints() {
		return List.of(
				// Public reads — open to everyone.
				new Endpoint("GET /api/news-posts", Access.PERMIT_ALL,
						() -> get("/api/news-posts")),
				new Endpoint("GET /api/news-posts/1", Access.PERMIT_ALL,
						() -> get("/api/news-posts/1")),
				new Endpoint("GET /api/ping", Access.PERMIT_ALL,
						() -> get("/api/ping")),
				// Any signed-in user, admin or not.
				new Endpoint("GET /api/me", Access.AUTHENTICATED,
						() -> get("/api/me")),
				// Admin-gated mutating surface.
				new Endpoint("POST /api/news-posts", Access.ADMIN,
						() -> post("/api/news-posts").contentType(APPLICATION_JSON).content(VALID_BODY)),
				new Endpoint("POST /api/news-posts/generate", Access.ADMIN,
						() -> post("/api/news-posts/generate")),
				new Endpoint("PUT /api/news-posts/1", Access.ADMIN,
						() -> put("/api/news-posts/1").contentType(APPLICATION_JSON).content(VALID_BODY)),
				new Endpoint("DELETE /api/news-posts/1", Access.ADMIN,
						() -> delete("/api/news-posts/1")),
				// Unmatched path — pins the anyRequest().denyAll() default (denies everyone,
				// admin included), so a new endpoint added without a rule fails closed.
				new Endpoint("GET /api/unknown", Access.DENY_ALL,
						() -> get("/api/unknown")));
	}

	static Stream<Arguments> matrix() {
		return endpoints().stream().flatMap(endpoint ->
				Arrays.stream(Caller.values())
						.map(caller -> Arguments.of(caller + " → " + endpoint.label(), caller, endpoint)));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("matrix")
	void enforcesAuthorizationBoundary(String label, Caller caller, Endpoint endpoint) throws Exception {
		MockHttpServletRequestBuilder request = endpoint.request().get();
		RequestPostProcessor principal = caller.postProcessor();
		if (principal != null) {
			request = request.with(principal);
		}
		mvc.perform(request).andExpect(expected(endpoint.access(), caller));
	}

	private static ResultMatcher expected(Access access, Caller caller) {
		return switch (access) {
			// Open to all callers regardless of credentials.
			case PERMIT_ALL -> clearsSecurityGate();
			// 401 without a token; any signed-in user gets 200.
			case AUTHENTICATED -> caller == Caller.ANONYMOUS ? status().isUnauthorized() : status().isOk();
			// 401 anon, 403 signed-in-non-admin, admin clears the gate.
			case ADMIN -> switch (caller) {
				case ANONYMOUS -> status().isUnauthorized();
				case NON_ADMIN -> status().isForbidden();
				case ADMIN -> clearsSecurityGate();
			};
			// denyAll rejects everyone: 401 without credentials, 403 with any token
			// (admin included — the default is not an admin gate).
			case DENY_ALL -> caller == Caller.ANONYMOUS ? status().isUnauthorized() : status().isForbidden();
		};
	}

	/**
	 * The caller passed the security chain: any status except the two the chain
	 * itself emits. Asserting "not 401/403" (rather than an exact business code)
	 * keeps the matrix focused on the authorization boundary, not controller
	 * internals — the business codes are pinned in {@code PublishGateTest}.
	 */
	private static ResultMatcher clearsSecurityGate() {
		return result -> {
			int status = result.getResponse().getStatus();
			assertThat(status)
					.as("authorized caller must clear the security gate (not 401/403)")
					.isNotIn(HttpStatus.UNAUTHORIZED.value(), HttpStatus.FORBIDDEN.value());
		};
	}
}
