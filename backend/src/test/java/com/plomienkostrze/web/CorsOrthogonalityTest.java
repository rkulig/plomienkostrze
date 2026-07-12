package com.plomienkostrze.web;

import static com.plomienkostrze.web.MockPrincipals.userJwt;
import static org.springframework.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static org.springframework.http.HttpHeaders.ORIGIN;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.plomienkostrze.league.LeagueService;
import com.plomienkostrze.news.NewsGenerationService;
import com.plomienkostrze.news.NewsPostRepository;
import com.plomienkostrze.security.SecurityConfig;

/**
 * Risk #4 — CORS and authorization are orthogonal. A legal {@code Origin} passes
 * preflight (answered before the authorization chain), yet the protected write
 * still demands a valid admin token: the CORS allow-origin header grants
 * cross-origin permission to the <i>browser</i>, never access to the endpoint.
 *
 * <p>Every case pairs the CORS surface with a protected-path status assertion so
 * neither can drift alone — a "CORS fix" that relaxes the authz matcher, or an
 * authz loosening that a permissive header would mask, breaks a case here. The
 * legal Origin ({@code http://localhost:4200}) comes from {@code app.cors.allowed-origins}
 * in the test properties, mirroring {@link CorsConfig}.
 */
@WebMvcTest
@Import({ SecurityConfig.class, CorsConfig.class })
class CorsOrthogonalityTest {

	private static final String LEGAL_ORIGIN = "http://localhost:4200";
	private static final String VALID_BODY = "{\"title\":\"t\",\"content\":\"c\"}";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private NewsPostRepository repository;

	@MockitoBean
	private NewsGenerationService generationService;

	// LeagueTableController is component-scanned by @WebMvcTest; mock its collaborator (S-05).
	@MockitoBean
	private LeagueService leagueService;

	@Test
	void preflightSucceedsForLegalOrigin() throws Exception {
		mvc.perform(options("/api/news-posts")
				.header(ORIGIN, LEGAL_ORIGIN)
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isOk())
				// The actual allowed Origin is echoed, not merely a header present.
				.andExpect(header().string(ACCESS_CONTROL_ALLOW_ORIGIN, LEGAL_ORIGIN));
	}

	@Test
	void protectedWriteStillRejectsTokenlessCallWithLegalOrigin() throws Exception {
		// A legal Origin header does not grant access — no token still means 401.
		mvc.perform(post("/api/news-posts")
				.header(ORIGIN, LEGAL_ORIGIN)
				.contentType(APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void protectedWriteStillRejectsNonAdminWithLegalOrigin() throws Exception {
		// Signed in but not admin — the Origin header does not lift the role gate.
		mvc.perform(post("/api/news-posts").with(userJwt())
				.header(ORIGIN, LEGAL_ORIGIN)
				.contentType(APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isForbidden());
	}
}
