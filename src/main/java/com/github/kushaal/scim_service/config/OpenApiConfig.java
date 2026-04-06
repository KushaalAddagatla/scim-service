package com.github.kushaal.scim_service.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the global OpenAPI metadata and OAuth2 Bearer security scheme.
 *
 * <p>The {@code @OpenAPIDefinition} annotation adds the top-level info block
 * (title, description, version) and applies the {@code bearerAuth} scheme to
 * every endpoint by default — controllers can override with their own
 * {@code @SecurityRequirement} if needed.
 *
 * <p>The {@code @SecurityScheme} annotation tells Swagger UI to render a
 * "Authorize" button that prompts for a Bearer JWT, so testers can call
 * protected endpoints directly from the UI without a separate curl step.
 */
@OpenAPIDefinition(
        info = @Info(
                title = "SCIM Service API",
                version = "1.0.0",
                description = """
                        RFC 7643 / 7644 compliant identity provisioning service with an \
                        IGA access certification engine. \
                        Supports full User and Group CRUD, JSON Patch (RFC 6902), \
                        and the RFC 7644 §3.7 Bulk endpoint. \
                        All SCIM endpoints are secured with OAuth 2.0 Bearer JWT (HMAC-SHA256)."""
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "JWT signed with HMAC-SHA256. Payload must include scope=scim:provision."
)
@Configuration
public class OpenApiConfig {
}
