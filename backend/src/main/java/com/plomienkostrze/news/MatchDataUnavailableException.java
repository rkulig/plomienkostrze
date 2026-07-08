package com.plomienkostrze.news;

/**
 * Match data could not be obtained from 90minut.pl — network failure, an
 * unexpected page structure, or no played match in the configured season.
 */
public class MatchDataUnavailableException extends RuntimeException {

	public MatchDataUnavailableException(String message) {
		super(message);
	}

	public MatchDataUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}
}
