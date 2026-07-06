package com.plomienkostrze.web;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signed-in user's status (roadmap S-02). The admin allowlist lives only on
 * the backend, so the SPA asks here to drive its UI states ("Dodaj post"
 * button, /admin redirect). Requires a valid token; 401 without one comes
 * from the security chain.
 */
@RestController
public class MeController {

	public record MeResponse(boolean admin) {
	}

	@GetMapping("/api/me")
	public MeResponse me(Authentication authentication) {
		boolean admin = authentication.getAuthorities().stream()
				.anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
		return new MeResponse(admin);
	}
}
