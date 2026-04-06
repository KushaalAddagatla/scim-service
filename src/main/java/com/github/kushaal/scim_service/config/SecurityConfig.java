package com.github.kushaal.scim_service.config;

import com.github.kushaal.scim_service.dto.response.ScimError;
import com.github.kushaal.scim_service.model.ScimConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Secures all SCIM endpoints with OAuth 2.0 Bearer JWT (HMAC-SHA256).
 *
 * <p>Discovery endpoints are permit-all because Okta fetches ServiceProviderConfig,
 * Schemas, and ResourceTypes before presenting credentials — they are intentionally
 * public per RFC 7644 §4.
 *
 * <p>All other {@code /scim/v2/**} endpoints require a valid JWT that passes:
 * <ul>
 *   <li>Signature verification (HMAC-SHA256, key from Secrets Manager)</li>
 *   <li>Expiry check ({@code exp} claim, via {@link JwtTimestampValidator})</li>
 *   <li>Issuer check ({@code iss} must equal {@code scim.jwt.issuer})</li>
 *   <li>Scope check ({@code scope} must contain {@code scim:provision})</li>
 * </ul>
 *
 * <p>Both 401 (unauthenticated) and 403 (insufficient scope) responses return a
 * SCIM-shaped error body ({@code application/scim+json}) instead of Spring Security's
 * default HTML page — Okta and other IdPs expect machine-readable JSON.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Main security filter chain.
     *
     * <p>{@code JwtDecoder} is injected here rather than auto-configured so we
     * control exactly which validators run (timestamp + issuer). The bean is
     * provided by {@link AwsSecretsManagerConfig} in live environments and by
     * {@code TestSecurityConfig} in the test context.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtDecoder jwtDecoder,
                                           ObjectMapper objectMapper,
                                           RateLimitFilter rateLimitFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // RFC 7644 §4 — discovery endpoints are public
                        .requestMatchers(HttpMethod.GET,
                                "/scim/v2/ServiceProviderConfig",
                                "/scim/v2/Schemas",
                                "/scim/v2/ResourceTypes").permitAll()
                        // Swagger UI and OpenAPI spec are public — no auth needed to read docs
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        // Certification action endpoint — auth comes from the signed JWT in the
                        // token query param, not a Bearer header. Must be listed before the SCIM
                        // catch-all below, otherwise Spring Security applies the wrong rule first.
                        .requestMatchers("/certifications/action").permitAll()
                        // All other SCIM operations require a valid token with the provisioning scope.
                        // Spring Security maps the JWT 'scope' claim → SCOPE_<value> authorities,
                        // so 'scim:provision' in the token becomes SCOPE_scim:provision here.
                        .requestMatchers("/scim/v2/**").hasAuthority("SCOPE_scim:provision")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        // Override entry points so auth failures return application/scim+json,
                        // not the Spring Security default HTML error page.
                        .authenticationEntryPoint(scimAuthenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(scimAccessDeniedHandler(objectMapper))
                );
        return http.build();
    }

    @Bean
    public RateLimitFilter rateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${scim.rate-limit.capacity:100}") long capacity,
            @Value("${scim.rate-limit.refill-per-minute:100}") long refillPerMinute) {
        return new RateLimitFilter(objectMapper, capacity, refillPerMinute);
    }

    /**
     * JWT decoder wired with the HMAC-SHA256 signing key and two validators:
     * timestamp (rejects expired tokens) and issuer (rejects tokens from unexpected sources).
     *
     * <p>Scope is NOT validated here — it is enforced by {@code hasAuthority} in the
     * filter chain above, which is the idiomatic Spring Security approach and keeps
     * authorization rules visible in one place.
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKeySpec signingKey,
                                 @Value("${scim.jwt.issuer:scim-service}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(issuer)
        ));

        return decoder;
    }

    // ── Error response helpers ────────────────────────────────────────────────

    // 401 — no token, expired token, bad signature, wrong issuer
    private AuthenticationEntryPoint scimAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(ScimConstants.SCIM_CONTENT_TYPE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ScimError.builder().status(401).detail(ex.getMessage()).build()
            ));
        };
    }

    // 403 — valid token but missing scim:provision scope
    private AccessDeniedHandler scimAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, ex) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(ScimConstants.SCIM_CONTENT_TYPE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ScimError.builder().status(403).detail(ex.getMessage()).build()
            ));
        };
    }
}
