package com.plomienkostrze.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.plomienkostrze.league.FixtureRow;
import com.plomienkostrze.league.FixturesService;
import com.plomienkostrze.league.LeagueDataUnavailableException;

/**
 * Public, unauthenticated read of Płomień's season fixtures (roadmap S-06),
 * scraped from 90minut.pl and served from a short server-side cache. Structural
 * clone of {@link LeagueTableController} (record DTOs via static {@code from});
 * the endpoint is allow-listed in SecurityConfig.
 *
 * <p>A scrape failure maps to 502 (BAD_GATEWAY) so the SPA can render an error state.
 */
@RestController
@RequestMapping("/api/fixtures")
public class FixturesController {

	private static final Logger log = LoggerFactory.getLogger(FixturesController.class);

	private final FixturesService fixturesService;

	public FixturesController(FixturesService fixturesService) {
		this.fixturesService = fixturesService;
	}

	public record RowResponse(String round, String opponent, boolean home, boolean played,
			Integer goalsFor, Integer goalsAgainst) {

		static RowResponse from(FixtureRow row) {
			return new RowResponse(row.round(), row.opponent(), row.home(), row.played(),
					row.goalsFor(), row.goalsAgainst());
		}
	}

	public record FixturesResponse(List<RowResponse> rows) {
	}

	@GetMapping
	public FixturesResponse fixtures() {
		try {
			List<RowResponse> rows = fixturesService.getFixtures().stream()
					.map(RowResponse::from)
					.toList();
			return new FixturesResponse(rows);
		} catch (LeagueDataUnavailableException e) {
			log.warn("fixtures unavailable", e);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "fixtures unavailable");
		}
	}
}
