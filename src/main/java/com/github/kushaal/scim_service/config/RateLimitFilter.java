package com.github.kushaal.scim_service.config;

import com.github.kushaal.scim_service.dto.response.ScimError;
import com.github.kushaal.scim_service.model.ScimConstants;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiter using Bucket4j token-bucket algorithm.
 *
 * <p>Each unique source IP gets its own bucket with a configurable capacity and
 * greedy refill rate. A greedy refill adds tokens at a constant rate as time
 * passes — so 100 tokens/60 seconds means roughly 1.67 tokens/second, not a
 * sudden burst of 100 at the top of every minute.
 *
 * <p>When a bucket is empty, this filter writes a 429 SCIM error body directly
 * to the response and halts the filter chain. It does NOT throw an exception,
 * because {@code @RestControllerAdvice} only covers exceptions that reach the
 * DispatcherServlet — exceptions thrown from a filter bypass the exception
 * handler entirely.
 *
 * <p>Capacity and refill rate are configurable so integration tests can use a
 * small limit (e.g., 3) without waiting a full minute.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final long capacity;
    private final long refillPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper, long capacity, long refillPerMinute) {
        this.objectMapper = objectMapper;
        this.capacity = capacity;
        this.refillPerMinute = refillPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType(ScimConstants.SCIM_CONTENT_TYPE);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ScimError.builder()
                            .status(429)
                            .scimType("tooMany")
                            .detail("Rate limit exceeded. Max " + capacity + " requests per minute per IP.")
                            .build()
            ));
        }
    }

    private Bucket createBucket() {
        // Bandwidth.classic with a greedy refill: tokens accumulate continuously
        // rather than resetting all at once on a fixed schedule. This prevents
        // thundering-herd bursts at minute boundaries.
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillPerMinute, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
