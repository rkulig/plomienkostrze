package com.plomienkostrze.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.plomienkostrze.news.MatchDataUnavailableException;
import com.plomienkostrze.news.NewsGenerationService;
import com.plomienkostrze.news.NewsGenerationService.ProposalDraft;

/**
 * Admin-only generation of a news-post proposal from the last played match
 * (roadmap S-03, US-01). Persists nothing: the returned draft lives in the
 * admin's browser until published via the existing POST /api/news-posts.
 *
 * Error contract for the SPA: 424 = match data unavailable (scraper),
 * 502 = generation failed (model/transport) — distinguishable by status alone.
 */
@RestController
@RequestMapping("/api/news-posts")
public class NewsGenerationController {

	private static final Logger log = LoggerFactory.getLogger(NewsGenerationController.class);

	private final NewsGenerationService generationService;

	public NewsGenerationController(NewsGenerationService generationService) {
		this.generationService = generationService;
	}

	public record ProposalResponse(String title, String content) {
	}

	@PostMapping("/generate")
	public ProposalResponse generate() {
		ProposalDraft draft;
		try {
			draft = generationService.generateFromLastMatch();
		} catch (MatchDataUnavailableException e) {
			log.warn("match data unavailable", e);
			throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "match data unavailable");
		} catch (RuntimeException e) {
			log.error("news generation failed", e);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "news generation failed");
		}
		if (draft == null || draft.title() == null || draft.content() == null) {
			log.error("news generation returned an incomplete draft: {}", draft);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "news generation failed");
		}
		return new ProposalResponse(draft.title(), draft.content());
	}
}
