package com.github.kushaal.scim_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_KEY    = "correlationId";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        // Honour a correlation ID the client already attached (e.g. Okta, an API gateway,
        // or a test harness). If none is present, generate a fresh one.
        // This means the same ID threads through your logs AND the upstream caller's logs.
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // MDC is a thread-local map that SLF4J/Logback reads when formatting log lines.
        // Any log statement on this thread will now automatically include the correlation ID
        // if your logback pattern contains %X{correlationId}.
        // The service's writeAuditLog() also reads from here to persist it.
        MDC.put(CORRELATION_ID_KEY, correlationId);

        // Echo the correlation ID back in the response so callers can correlate
        // their request with your server logs.
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // CRITICAL: always remove from MDC in a finally block.
            // Spring uses a thread pool — if this thread is reused for the next request
            // without cleaning up, that request inherits this correlation ID,
            // silently poisoning the audit trail.
            MDC.remove(CORRELATION_ID_KEY);
        }
    }
}
