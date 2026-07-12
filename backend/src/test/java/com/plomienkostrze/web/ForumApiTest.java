package com.plomienkostrze.web;

import static com.plomienkostrze.web.MockPrincipals.userJwt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.plomienkostrze.forum.ForumPost;
import com.plomienkostrze.forum.ForumService;
import com.plomienkostrze.forum.ForumService.ThreadWithPosts;
import com.plomienkostrze.forum.ForumThread;
import com.plomienkostrze.forum.ThreadNotFoundException;
import com.plomienkostrze.league.FixturesService;
import com.plomienkostrze.league.LeagueService;
import com.plomienkostrze.news.NewsGenerationService;
import com.plomienkostrze.news.NewsPostRepository;
import com.plomienkostrze.security.SecurityConfig;

/**
 * Behavior slice for the forum web layer (roadmap S-07), driven by a signed-in
 * fan ({@link MockPrincipals#userJwt()}). Pins the controller contract: create
 * returns 201 with the opener, reply returns 201 and delegates to the service,
 * a missing thread yields 404, blank input yields 400, and responses expose only
 * the safe {@code authorDisplayName} — never the author's UID or email.
 *
 * <p>The multi-row bump (last_activity_at / post_count) lives in the
 * {@code @Transactional} service and is proven end-to-end in the manual Postgres
 * verification; here the service is mocked, so the assertion is that the
 * controller delegates the reply with the right thread id and body.
 */
@WebMvcTest
@Import({ SecurityConfig.class, CorsConfig.class })
class ForumApiTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ForumService forumService;

	// Collaborators of the other component-scanned controllers — present so the
	// @WebMvcTest context loads.
	@MockitoBean
	private NewsPostRepository repository;

	@MockitoBean
	private NewsGenerationService generationService;

	@MockitoBean
	private LeagueService leagueService;

	@MockitoBean
	private FixturesService fixturesService;

	@Test
	void createThreadReturns201WithOpener() throws Exception {
		ForumThread thread = ForumThread.openedBy("Zapowiedź meczu", "uid-1", "Jan");
		ForumPost opener = ForumPost.in(10L, "uid-1", "Jan", "Kto idzie na mecz?");
		given(forumService.openThread(any(), any(), any(), any(), any())).willReturn(thread);
		given(forumService.getThread(any(), anyInt(), anyInt()))
				.willReturn(new ThreadWithPosts(thread, new PageImpl<>(List.of(opener))));

		mvc.perform(post("/api/forum/threads").with(userJwt())
				.contentType(APPLICATION_JSON)
				.content("{\"title\":\"Zapowiedź meczu\",\"body\":\"Kto idzie na mecz?\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Zapowiedź meczu"))
				.andExpect(jsonPath("$.authorDisplayName").value("Jan"))
				.andExpect(jsonPath("$.postCount").value(1))
				.andExpect(jsonPath("$.posts[0].body").value("Kto idzie na mecz?"))
				.andExpect(jsonPath("$.posts[0].authorDisplayName").value("Jan"));
	}

	@Test
	void replyReturns201AndDelegatesToService() throws Exception {
		given(forumService.reply(any(), any(), any(), any(), any()))
				.willReturn(ForumPost.in(5L, "uid-1", "Jan", "Jasne, będę!"));

		mvc.perform(post("/api/forum/threads/7/posts").with(userJwt())
				.contentType(APPLICATION_JSON)
				.content("{\"body\":\"Jasne, będę!\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.body").value("Jasne, będę!"))
				.andExpect(jsonPath("$.authorDisplayName").value("Jan"));

		// The controller delegates to the transactional service, which owns the
		// last_activity_at / post_count bump.
		verify(forumService).reply(eq(7L), eq("Jasne, będę!"), any(), any(), any());
	}

	@Test
	void getThreadMissingReturns404() throws Exception {
		given(forumService.getThread(any(), anyInt(), anyInt())).willThrow(new ThreadNotFoundException(99L));

		mvc.perform(get("/api/forum/threads/99").with(userJwt()))
				.andExpect(status().isNotFound());
	}

	@Test
	void replyToMissingThreadReturns404() throws Exception {
		given(forumService.reply(any(), any(), any(), any(), any())).willThrow(new ThreadNotFoundException(99L));

		mvc.perform(post("/api/forum/threads/99/posts").with(userJwt())
				.contentType(APPLICATION_JSON)
				.content("{\"body\":\"hej\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void blankTitleReturns400() throws Exception {
		mvc.perform(post("/api/forum/threads").with(userJwt())
				.contentType(APPLICATION_JSON)
				.content("{\"title\":\"\",\"body\":\"treść\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void blankBodyReturns400() throws Exception {
		mvc.perform(post("/api/forum/threads/1/posts").with(userJwt())
				.contentType(APPLICATION_JSON)
				.content("{\"body\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void responsesOmitAuthorUidAndEmail() throws Exception {
		ForumThread thread = ForumThread.openedBy("Wątek", "uid-secret", "Jan");
		ForumPost opener = ForumPost.in(1L, "uid-secret", "Jan", "treść");
		given(forumService.getThread(any(), anyInt(), anyInt()))
				.willReturn(new ThreadWithPosts(thread, new PageImpl<>(List.of(opener))));

		mvc.perform(get("/api/forum/threads/1").with(userJwt()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authorDisplayName").value("Jan"))
				.andExpect(jsonPath("$.authorUid").doesNotExist())
				.andExpect(jsonPath("$.authorEmail").doesNotExist())
				.andExpect(jsonPath("$.posts[0].authorUid").doesNotExist())
				.andExpect(jsonPath("$.posts[0].authorEmail").doesNotExist());
	}
}
