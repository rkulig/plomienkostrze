package com.plomienkostrze.news;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sole integration point with 90minut.pl (roadmap S-03): fetches the team's
 * season page and parses the most recent played match. The page is static
 * server-rendered HTML in ISO-8859-2 (jsoup honours the charset header).
 *
 * Each match row holds five cells: date | competition | hosts | score | guests.
 * The score cell carries class {@code mecze2}; our own team is marked with
 * {@code <b><u>}, the opponent is a plain link. Unplayed matches show "-" as
 * the score, penalty shoot-outs append a second "k. x-y" line.
 */
@Component
public class NinetyMinutClient {

	private static final int TIMEOUT_MILLIS = 10_000;
	private static final Pattern SCORE = Pattern.compile("(\\d+)-(\\d+)");
	private static final Pattern PENALTIES = Pattern.compile("k\\.\\s*(\\d+-\\d+)");
	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final String baseUrl;
	private final int teamId;
	private final int seasonId;

	public NinetyMinutClient(@Value("${app.ninetyminut.base-url}") String baseUrl,
			@Value("${app.ninetyminut.team-id}") int teamId,
			@Value("${app.ninetyminut.season-id}") int seasonId) {
		this.baseUrl = baseUrl;
		this.teamId = teamId;
		this.seasonId = seasonId;
	}

	public enum Outcome {
		WIN, DRAW, LOSS
	}

	/**
	 * Goals and venue are from Płomień's perspective; {@code note} is e.g.
	 * "po karnych 4-3" or null. {@code kickoff} at midnight means the page
	 * carried no kickoff time (a real 00:00 kickoff does not happen).
	 */
	public record MatchResult(String opponent, boolean home, int goalsFor, int goalsAgainst,
			String competition, LocalDateTime kickoff, Outcome outcome, String note) {
	}

	/**
	 * Returns the most recent played match of the configured season: the last
	 * row (rows are chronological) with a real score, skipping unplayed ("-")
	 * and future-dated rows. Never returns null.
	 */
	public MatchResult fetchLastMatch() {
		Document page;
		try {
			page = Jsoup.connect(baseUrl + "/mecze_druzyna.php?id=" + teamId + "&id_sezon=" + seasonId)
					.timeout(TIMEOUT_MILLIS)
					.get();
		} catch (IOException e) {
			throw new MatchDataUnavailableException("failed to fetch 90minut team page", e);
		}
		MatchResult last = null;
		for (Element scoreCell : page.select("td.mecze2")) {
			MatchResult match = parseRow(scoreCell);
			if (match != null) {
				last = match;
			}
		}
		if (last == null) {
			throw new MatchDataUnavailableException(
					"no played match found for team " + teamId + " in season " + seasonId);
		}
		return last;
	}

	/** Returns null for rows that are unplayed, in the future, or structurally off. */
	private MatchResult parseRow(Element scoreCell) {
		Element hostsCell = scoreCell.previousElementSibling();
		Element guestsCell = scoreCell.nextElementSibling();
		Element competitionCell = hostsCell == null ? null : hostsCell.previousElementSibling();
		Element dateCell = competitionCell == null ? null : competitionCell.previousElementSibling();
		if (guestsCell == null || dateCell == null) {
			return null;
		}

		String scoreText = scoreCell.text();
		Matcher score = SCORE.matcher(scoreText);
		if (!score.find()) {
			return null;
		}
		int hostGoals = Integer.parseInt(score.group(1));
		int guestGoals = Integer.parseInt(score.group(2));
		Matcher penalties = PENALTIES.matcher(scoreText);
		String note = penalties.find() ? "po karnych " + penalties.group(1) : null;

		LocalDateTime kickoff;
		String dateText = dateCell.text().strip();
		try {
			kickoff = LocalDateTime.parse(dateText, DATE_TIME);
		} catch (DateTimeParseException e) {
			try {
				kickoff = LocalDate.parse(dateText.substring(0, Math.min(10, dateText.length()))).atStartOfDay();
			} catch (DateTimeParseException | IndexOutOfBoundsException e2) {
				return null;
			}
		}
		if (kickoff.toLocalDate().isAfter(LocalDate.now())) {
			return null;
		}

		boolean home = hostsCell.selectFirst("b > u") != null;
		String opponent = (home ? guestsCell : hostsCell).text().strip();
		int goalsFor = home ? hostGoals : guestGoals;
		int goalsAgainst = home ? guestGoals : hostGoals;
		Outcome outcome = goalsFor > goalsAgainst ? Outcome.WIN
				: goalsFor < goalsAgainst ? Outcome.LOSS
				: Outcome.DRAW;
		return new MatchResult(opponent, home, goalsFor, goalsAgainst,
				competitionCell.text().strip(), kickoff, outcome, note);
	}
}
