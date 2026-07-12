package com.plomienkostrze.league;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serves the league standings from a short in-memory TTL cache (roadmap S-05),
 * scraping via {@link NinetyMinutLeagueClient} on a miss. Keeps guest traffic
 * off 90minut and makes cached reads instant.
 *
 * <p>The cache is a performance optimization only: on a miss the service
 * scrapes; if the scrape throws, the exception propagates, the cache is NOT
 * updated and stale data is NOT served (the controller then returns 502).
 * Concurrent misses may double-scrape briefly — acceptable at this scale.
 *
 * <p>Pre-season rule: until the first match is played (every row shows 0 played),
 * there is no real ranking — 90minut still labels one team's rank cell, which would
 * imply a leader. In that case the rows are shuffled and all positions blanked, so
 * no team appears to lead. The shuffle happens once per scrape (the shuffled list is
 * what gets cached), so the order is stable within the cache window.
 */
@Service
public class LeagueService {

	private final NinetyMinutLeagueClient client;
	private final Duration ttl;

	private volatile Snapshot cache;

	public LeagueService(NinetyMinutLeagueClient client,
			@Value("${app.ninetyminut.league-cache-ttl}") Duration ttl) {
		this.client = client;
		this.ttl = ttl;
	}

	public List<StandingRow> getStandings() {
		Snapshot current = cache;
		if (current != null && Duration.between(current.fetchedAt(), Instant.now()).compareTo(ttl) < 0) {
			return current.rows();
		}
		List<StandingRow> rows = normalizePreseason(client.fetchStandings());
		cache = new Snapshot(rows, Instant.now());
		return rows;
	}

	/**
	 * Pre-season (no match played yet) → shuffle the rows and blank every position so
	 * no team appears to lead. Once any match is played, the source order and real
	 * positions are returned verbatim. The frontend renders a null position as "–".
	 */
	private List<StandingRow> normalizePreseason(List<StandingRow> rows) {
		if (rows.isEmpty() || rows.stream().anyMatch(r -> r.played() > 0)) {
			return rows;
		}
		List<StandingRow> shuffled = new ArrayList<>(rows);
		Collections.shuffle(shuffled);
		return shuffled.stream()
				.map(r -> new StandingRow(null, r.team(), r.played(), r.points()))
				.toList();
	}

	private record Snapshot(List<StandingRow> rows, Instant fetchedAt) {
	}
}
