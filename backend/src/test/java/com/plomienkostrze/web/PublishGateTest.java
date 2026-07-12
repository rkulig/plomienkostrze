package com.plomienkostrze.web;

import static com.plomienkostrze.web.MockPrincipals.adminJwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.plomienkostrze.news.NewsGenerationService;
import com.plomienkostrze.news.NewsGenerationService.ProposalDraft;
import com.plomienkostrze.news.NewsPost;
import com.plomienkostrze.news.NewsPostRepository;
import com.plomienkostrze.news.NewsPostStatus;
import com.plomienkostrze.security.SecurityConfig;

/**
 * Risk #2 (reframed) — the publish gate. Two invariants keep unpublished content
 * out of public view:
 *
 * <ol>
 * <li><b>Public read is PUBLISHED-only.</b> The list asks the repository for
 * {@code findByStatus(PUBLISHED, …)} and detail 404s anything the filter rejects
 * — the gate is controller-side, so the oracle is <i>which status the controller
 * asks for</i>, not the response body.</li>
 * <li><b>Generation persists nothing.</b> {@code POST /generate} returns an
 * in-memory draft and never touches the repository; the only path that writes is
 * {@code POST /api/news-posts}, which publishes.</li>
 * </ol>
 *
 * <p>The 200-no-save (generate) vs 201-publishes (create) distinction is pinned
 * as two contrasting cases so a future reader — or a regression that adds a
 * {@code save} to the generate path — can't conflate them.
 */
@WebMvcTest
@Import({ SecurityConfig.class, CorsConfig.class })
class PublishGateTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private NewsPostRepository repository;

	@MockitoBean
	private NewsGenerationService generationService;

	// LeagueTableController is component-scanned by @WebMvcTest; mock its collaborator (S-05).
	@MockitoBean
	private com.plomienkostrze.league.LeagueService leagueService;

	// FixturesController is component-scanned by @WebMvcTest; mock its collaborator (S-06).
	@MockitoBean
	private com.plomienkostrze.league.FixturesService fixturesService;

	private static final String VALID_BODY = "{\"title\":\"t\",\"content\":\"c\"}";

	@Test
	void listAsksRepositoryForPublishedOnly() throws Exception {
		given(repository.findByStatus(any(), any())).willReturn(Page.<NewsPost>empty());

		mvc.perform(get("/api/news-posts")).andExpect(status().isOk());

		// The oracle: the controller requests PUBLISHED, never a status-agnostic fetch.
		ArgumentCaptor<NewsPostStatus> statusArg = ArgumentCaptor.forClass(NewsPostStatus.class);
		verify(repository).findByStatus(statusArg.capture(), any(Pageable.class));
		assertThat(statusArg.getValue()).isEqualTo(NewsPostStatus.PUBLISHED);
	}

	@Test
	void detailReturnsPublishedPost() throws Exception {
		given(repository.findById(anyLong())).willReturn(Optional.of(NewsPost.published("t", "c")));

		mvc.perform(get("/api/news-posts/1")).andExpect(status().isOk());
	}

	/**
	 * The not-found branch pins the {@code .filter(status == PUBLISHED)} guard's
	 * presence. The "non-published post exists → 404" branch is unreachable by
	 * construction today (the enum has only PUBLISHED) and is asserted at the DB
	 * level in rollout Phase 2.
	 */
	@Test
	void detail404sWhenPostAbsent() throws Exception {
		given(repository.findById(anyLong())).willReturn(Optional.empty());

		mvc.perform(get("/api/news-posts/1")).andExpect(status().isNotFound());
	}

	@Test
	void generateReturnsDraftAndPersistsNothing() throws Exception {
		given(generationService.generateFromLastMatch()).willReturn(new ProposalDraft("draft title", "draft content"));

		mvc.perform(post("/api/news-posts/generate").with(adminJwt()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("draft title"))
				.andExpect(jsonPath("$.content").value("draft content"));

		// The no-save oracle: generation makes nothing public — it never touches the repo.
		verifyNoInteractions(repository);
	}

	@Test
	void createIsTheOnlyPathThatPersists() throws Exception {
		given(repository.save(any())).willReturn(NewsPost.published("t", "c"));

		mvc.perform(post("/api/news-posts").with(adminJwt())
				.contentType(APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isCreated());

		verify(repository).save(any());
	}
}
