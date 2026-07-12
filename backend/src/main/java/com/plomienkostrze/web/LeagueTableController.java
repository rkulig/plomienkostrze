package com.plomienkostrze.web;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.plomienkostrze.league.LeagueDataUnavailableException;
import com.plomienkostrze.league.LeagueService;
import com.plomienkostrze.league.StandingRow;

/**
 * Public, unauthenticated read of the current league standings (roadmap S-05),
 * scraped from 90minut.pl and served from a short server-side cache. Mirrors the
 * S-01 public-read shape (record DTOs via static {@code from}); the endpoint is
 * allow-listed in SecurityConfig.
 *
 * <p>A scrape failure maps to 502 (BAD_GATEWAY) — the same pattern as
 * {@code NewsGenerationController} — so the SPA can render an error state.
 */
@RestController
@RequestMapping("/api/league-table")
public class LeagueTableController {

	private static final Logger log = LoggerFactory.getLogger(LeagueTableController.class);

	private final LeagueService leagueService;

	public LeagueTableController(LeagueService leagueService) {
		this.leagueService = leagueService;
	}

	public record RowResponse(Integer position, String team, int played, int points) {

		static RowResponse from(StandingRow row) {
			return new RowResponse(row.position(), row.team(), row.played(), row.points());
		}
	}

	public record TableResponse(List<RowResponse> rows) {
	}

	@GetMapping
	public TableResponse table() {
		try {
			List<RowResponse> rows = leagueService.getStandings().stream()
					.map(RowResponse::from)
					.toList();
			return new TableResponse(rows);
		} catch (LeagueDataUnavailableException e) {
			log.warn("league standings unavailable", e);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "league standings unavailable");
		}
	}
}
