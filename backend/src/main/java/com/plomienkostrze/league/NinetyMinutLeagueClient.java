package com.plomienkostrze.league;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	/** Team-name text used to spot Płomień's row in a round block (no team links there). */
	private static final String TEAM_NAME = "Płomień Kostrze";
	/** A round header is a {@code <u>} whose text starts with e.g. "Kolejka 1". */
	private static final Pattern ROUND_HEADER = Pattern.compile("^\\s*Kolejka\\s+\\d+");
	/** A played score cell is "X-Y"; "-" or empty means the match is upcoming. */
	private static final Pattern SCORE = Pattern.compile("(\\d+)-(\\d+)");

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
	 * Fetches and parses Płomień's fixtures from the league-page terminarz (S-06),
	 * reusing {@link #fetchPage()} — the reuse the seam was built for.
	 *
	 * <p>The terminarz is a sequence of round blocks, each a {@code <u>} header
	 * ("Kolejka N - <date range>") followed by the round's {@code table.main[width=600]}
	 * of {@code host · score · guest} rows. We walk the document in order tracking the
	 * current round header; in each round table the one row whose text contains
	 * {@value #TEAM_NAME} is Płomień's match. Returns one {@link FixtureRow} per round
	 * that carries a Płomień match, in round (chronological) order; individual rows that
	 * don't parse are skipped. Throws {@link LeagueDataUnavailableException} if the walk
	 * yields zero rows — an empty terminarz is indistinguishable from a silent parse break.
	 */
	public List<FixtureRow> fetchFixtures() {
		Document page = fetchPage();
		List<FixtureRow> rows = new ArrayList<>();
		String currentRound = null;
		for (Element el : page.getAllElements()) {
			String tag = el.tagName();
			if (tag.equals("u")) {
				String text = el.text().strip();
				if (ROUND_HEADER.matcher(text).find()) {
					currentRound = text;
				}
			} else if (tag.equals("table") && isFixturesTable(el) && currentRound != null) {
				FixtureRow row = parseFixtureRow(el, currentRound);
				if (row != null) {
					rows.add(row);
				}
				currentRound = null;
			}
		}
		if (rows.isEmpty()) {
			throw new LeagueDataUnavailableException("no Płomień fixtures found on " + leagueUrl);
		}
		return rows;
	}

	/** A round's match table: {@code <table class="main" width="600">}. */
	private boolean isFixturesTable(Element table) {
		return table.hasClass("main") && "600".equals(table.attr("width"));
	}

	/**
	 * Płomień's row in a round table: {@code td[0]}=host, {@code td[1]}=score,
	 * {@code td[2]}=guest. Home iff Płomień is the host; opponent is the other cell.
	 * Score "-"/empty ⇒ upcoming; "X-Y" ⇒ host-guest normalized to Płomień's
	 * perspective. Returns null if no Płomień row is present or it is structurally short.
	 */
	private FixtureRow parseFixtureRow(Element table, String round) {
		for (Element tr : table.select("tr")) {
			if (!tr.text().contains(TEAM_NAME)) {
				continue;
			}
			Elements cells = tr.select("td");
			if (cells.size() < 3) {
				return null;
			}
			String host = cells.get(0).text().strip();
			String guest = cells.get(2).text().strip();
			boolean home = host.contains(TEAM_NAME);
			String opponent = home ? guest : host;
			Matcher score = SCORE.matcher(cells.get(1).text().strip());
			if (!score.find()) {
				return new FixtureRow(round, opponent, home, false, null, null);
			}
			int hostGoals = Integer.parseInt(score.group(1));
			int guestGoals = Integer.parseInt(score.group(2));
			int goalsFor = home ? hostGoals : guestGoals;
			int goalsAgainst = home ? guestGoals : hostGoals;
			return new FixtureRow(round, opponent, home, true, goalsFor, goalsAgainst);
		}
		return null;
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
