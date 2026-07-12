package com.plomienkostrze.league;

/**
 * One parsed Płomień fixture from the league-page terminarz (roadmap S-06),
 * Płomień-centric and compact. Transient — scraped from 90minut.pl and served
 * from an in-memory cache, never persisted.
 *
 * <p>{@code round} is the round header label (e.g. "Kolejka 1 - 15-16 sierpnia",
 * or just "Kolejka 25" when the date range is absent). {@code home} is true when
 * Płomień is the host. {@code goalsFor}/{@code goalsAgainst} are Płomień-perspective
 * and null when {@code played} is false.
 */
public record FixtureRow(String round, String opponent, boolean home, boolean played,
		Integer goalsFor, Integer goalsAgainst) {
}
