package com.plomienkostrze.league;

/**
 * League standings could not be obtained from 90minut.pl — network failure or
 * an unexpected page structure. Mirrors {@code news.MatchDataUnavailableException};
 * the controller maps it to a 502 so the SPA can render an error state.
 */
public class LeagueDataUnavailableException extends RuntimeException {

	public LeagueDataUnavailableException(String message) {
		super(message);
	}

	public LeagueDataUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
