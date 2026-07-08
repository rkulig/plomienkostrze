package com.plomienkostrze.news;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.plomienkostrze.news.NinetyMinutClient.MatchResult;

/**
 * Generates a news-post proposal from the last played match (roadmap S-03):
 * scrapes the result from 90minut.pl and asks the model for a title + content
 * draft. The proposal is never persisted — accepting it is a plain
 * POST /api/news-posts by the admin; rejecting it stores nothing.
 */
@Service
public class NewsGenerationService {

	// Weekday included: the model must not have to infer it from the date (it
	// guessed wrong in verification — a confabulation the guardrail forbids).
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy",
			Locale.of("pl"));
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

	private final NinetyMinutClient ninetyMinutClient;
	private final ChatClient chatClient;

	public NewsGenerationService(NinetyMinutClient ninetyMinutClient, ChatClient chatClient) {
		this.ninetyMinutClient = ninetyMinutClient;
		this.chatClient = chatClient;
	}

	public record ProposalDraft(String title, String content) {
	}

	/** Blocking call; scraper and model failures propagate to the controller. */
	public ProposalDraft generateFromLastMatch() {
		MatchResult match = ninetyMinutClient.fetchLastMatch();
		return chatClient.prompt()
				.user(userPromptFor(match))
				.call()
				.entity(ProposalDraft.class);
	}

	/**
	 * Plain concatenation of trusted, server-derived fields — deliberately no
	 * template renderer, so scraped strings are never interpreted as templates.
	 */
	private static String userPromptFor(MatchResult match) {
		String outcome = switch (match.outcome()) {
			case WIN -> "zwycięstwo Płomienia Kostrze";
			case DRAW -> "remis";
			case LOSS -> "porażka Płomienia Kostrze";
		};
		StringBuilder prompt = new StringBuilder()
				.append("Napisz wpis aktualności o ostatnim meczu Płomienia Kostrze. Fakty:\n")
				.append("- Rywal: ").append(match.opponent()).append('\n')
				.append("- Miejsce: ").append(match.home() ? "mecz u siebie" : "mecz wyjazdowy").append('\n')
				.append("- Rozgrywki: ").append(match.competition()).append('\n')
				.append("- Data: ").append(DATE_FORMAT.format(match.kickoff())).append('\n')
				.append("- Wynik: Płomień Kostrze ").append(match.goalsFor())
				.append(":").append(match.goalsAgainst()).append(" (").append(outcome).append(")\n");
		if (!match.kickoff().toLocalTime().equals(LocalTime.MIDNIGHT)) {
			prompt.append("- Godzina meczu: ")
					.append(match.kickoff().toLocalTime().format(TIME_FORMAT)).append('\n');
		}
		if (match.note() != null) {
			prompt.append("- Dodatkowo: ").append(match.note()).append('\n');
		}
		return prompt.toString();
	}
}
