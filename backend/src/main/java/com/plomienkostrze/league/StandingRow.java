package com.plomienkostrze.league;

/**
 * One parsed line of the league standings (compact columns only). Transient —
 * scraped from 90minut.pl and served from an in-memory cache, never persisted.
 * {@code position} is nullable: pre-season the source shows a blank rank cell.
 */
public record StandingRow(Integer position, String team, int played, int points) {
}
