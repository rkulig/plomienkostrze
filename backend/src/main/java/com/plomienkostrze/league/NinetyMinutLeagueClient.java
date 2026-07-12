package com.plomienkostrze.league;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Second integration point with 90minut.pl (roadmap S-05): fetches the
 * configured league page and parses the standings table. Parallel to the
 * match-scraping {@code news.NinetyMinutClient}, but for the league page.
 *
 * <p>The page is static server-rendered HTML in ISO-8859-2 (jsoup honours the
 * charset header — no explicit encoding here). It carries ~60 tables; the
 * standings one is identified by a header row holding the cells {@code Nazwa}
 * and {@code Pkt.} Data rows within it carry a team link
 * {@code a.main[href*="skarb.php?id_klub="]}; from each we read only the compact
 * columns: {@code td[0]}=position (may be blank pre-season), {@code td[1]}=team,
 * {@code td[2]}=matches played, {@code td[3]}=points.
 *
 * <p>{@link #fetchPage()} is a deliberate reusable seam: S-06 (fixtures) will
 * scrape the same page.
 */
@Component
public class NinetyMinutLeagueClient {

	private static final int TIMEOUT_MILLIS = 10_000;

	private final String leagueUrl;

	public NinetyMinutLeagueClient(@Value("${app.ninetyminut.league-url}") String leagueUrl) {
		this.leagueUrl = leagueUrl;
	}

	/** Fetches and parses the current standings; never returns null (may be empty). */
	public List<StandingRow> fetchStandings() {
		Document page = fetchPage();
		Element standings = findStandingsTable(page);
		if (standings == null) {
			throw new LeagueDataUnavailableException("standings table not found on " + leagueUrl);
		}
		List<StandingRow> rows = new ArrayList<>();
		for (Element tr : standings.select("tr")) {
			if (tr.selectFirst("a.main[href*='skarb.php?id_klub=']") == null) {
				continue;
			}
			StandingRow row = parseRow(tr);
			if (row != null) {
				rows.add(row);
			}
		}
		return rows;
	}

	/**
	 * Reusable page-fetch seam (S-06): {@code Jsoup.connect(...).timeout(...).get()}
	 * with {@code IOException} wrapped as {@link LeagueDataUnavailableException}.
	 */
	private Document fetchPage() {
		try {
			return Jsoup.connect(leagueUrl).timeout(TIMEOUT_MILLIS).get();
		} catch (IOException e) {
			throw new LeagueDataUnavailableException("failed to fetch 90minut league page", e);
		}
	}

	/**
	 * The standings table is the one whose header row holds cells {@code Nazwa}
	 * and {@code Pkt.} — distinguishing it from the ~60 other tables on the page.
	 */
	private Element findStandingsTable(Document page) {
		for (Element table : page.select("table")) {
			boolean hasName = false;
			boolean hasPoints = false;
			for (Element cell : table.select("th, td")) {
				String text = cell.text().strip();
				if (text.equals("Nazwa")) {
					hasName = true;
				} else if (text.equals("Pkt.")) {
					hasPoints = true;
				}
			}
			if (hasName && hasPoints) {
				return table;
			}
		}
		return null;
	}

	/** Returns null for structurally short rows (mirror of NinetyMinutClient.parseRow). */
	private StandingRow parseRow(Element tr) {
		Elements cells = tr.select("td");
		if (cells.size() < 4) {
			return null;
		}
		Element teamLink = tr.selectFirst("a.main[href*='skarb.php?id_klub=']");
		if (teamLink == null) {
			return null;
		}
		String team = teamLink.text().strip();
		if (team.isEmpty()) {
			return null;
		}
		Integer position = parsePosition(cells.get(0).text());
		Integer played = parseInt(cells.get(2).text());
		Integer points = parseInt(cells.get(3).text());
		if (played == null || points == null) {
			return null;
		}
		return new StandingRow(position, team, played, points);
	}

	/** Position cell is e.g. "1." — strip the trailing dot; blank pre-season → null. */
	private Integer parsePosition(String text) {
		String cleaned = text.strip().replaceAll("\\.$", "").strip();
		return cleaned.isEmpty() ? null : parseInt(cleaned);
	}

	private Integer parseInt(String text) {
		try {
			return Integer.parseInt(text.strip());
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
