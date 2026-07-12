package com.plomienkostrze.security;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.DispatcherType;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Stateless resource server verifying Firebase ID tokens (roadmap S-02).
 * Public S-01 reads stay open (permitAll); the only write endpoint requires
 * ROLE_ADMIN, granted when the token's {@code sub} (Firebase UID) is on the
 * {@code app.admin.uids} allowlist — an empty allowlist means nobody is admin.
 *
 * The decoder is built from the JWKS URI (lazy key fetch), NOT issuer-uri:
 * issuer-uri does eager OIDC discovery at startup and would break the H2
 * context test offline. Issuer and audience are therefore validated explicitly.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final Set<String> adminUids;
	private final String projectId;
	private final String jwkSetUri;

	public SecurityConfig(@Value("${app.admin.uids}") String adminUids,
			@Value("${app.firebase.project-id}") String projectId,
			@Value("${app.firebase.jwk-set-uri}") String jwkSetUri) {
		this.adminUids = Set.copyOf(Arrays.stream(adminUids.split(","))
				.map(String::strip)
				.filter(uid -> !uid.isEmpty())
				.toList());
		this.projectId = projectId;
		this.jwkSetUri = jwkSetUri;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(withDefaults())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						// Container-internal ERROR dispatches (sendError → /error) must pass,
						// or denyAll rewrites every ResponseStatusException to 401/403.
						// Clients cannot forge a dispatcher type; direct GET /error stays denied.
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.GET, "/api/news-posts/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/league-table").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/fixtures").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/ping").permitAll()
						.requestMatchers("/actuator/health").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/me").authenticated()
						.requestMatchers(HttpMethod.POST, "/api/news-posts").hasRole("ADMIN")
						.requestMatchers(HttpMethod.POST, "/api/news-posts/generate").hasRole("ADMIN")
						.requestMatchers(HttpMethod.PUT, "/api/news-posts/*").hasRole("ADMIN")
						.requestMatchers(HttpMethod.DELETE, "/api/news-posts/*").hasRole("ADMIN")
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
		OAuth2TokenValidator<Jwt> validators = JwtValidators.createDefaultWithValidators(
				new JwtIssuerValidator("https://securetoken.google.com/" + projectId),
				new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
						aud -> aud != null && aud.contains(projectId)));
		decoder.setJwtValidator(validators);
		return decoder;
	}

	private JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(jwt -> adminUids.contains(jwt.getSubject())
				? List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_ADMIN"))
				: List.of());
		return converter;
	}
}
