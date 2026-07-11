package com.plomienkostrze.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc principal post-processors shared by the Phase 1 security slices.
 *
 * <p>The admin allowlist ({@code app.admin.uids}) is empty in the test properties, so the
 * production converter mints nobody as admin — tests inject {@code ROLE_ADMIN} directly.
 * The authority string must be exactly {@code "ROLE_ADMIN"} to satisfy {@code hasRole("ADMIN")}
 * (which auto-prepends {@code ROLE_}). The {@code jwt()} post-processor injects the
 * Authentication and bypasses the JwtDecoder, so no live Firebase/JWKS fetch happens.
 */
final class MockPrincipals {

	private MockPrincipals() {
	}

	/** Authenticated caller elevated to admin (exact {@code ROLE_ADMIN} authority). */
	static RequestPostProcessor adminJwt() {
		return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
	}

	/** Authenticated caller with no roles — a signed-in fan, not an admin. */
	static RequestPostProcessor userJwt() {
		return jwt();
	}
}
