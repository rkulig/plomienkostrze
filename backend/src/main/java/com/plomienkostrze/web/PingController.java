package com.plomienkostrze.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness endpoint so a fresh deploy has a non-404 target to verify.
 * Replaced/extended by real controllers as features land.
 */
@RestController
public class PingController {

	@GetMapping("/api/ping")
	public Map<String, String> ping() {
		return Map.of("status", "ok");
	}
}
