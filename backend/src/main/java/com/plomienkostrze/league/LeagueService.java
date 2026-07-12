package com.plomienkostrze.league;

import java.time.Duration;
import java.time.Instant;
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
		List<StandingRow> rows = client.fetchStandings();
		cache = new Snapshot(rows, Instant.now());
		return rows;
	}

	private record Snapshot(List<StandingRow> rows, Instant fetchedAt) {
	}
}
