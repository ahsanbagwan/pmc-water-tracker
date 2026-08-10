package org.punewatertracker.security;

import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    private static final long WINDOW_MILLIS = 60_000; // 1 minute

    private record Window(AtomicInteger count, long windowStart) {}

    private final ConcurrentHashMap<String, Window> attemptsByIp = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean isLoginAttempt = "POST".equalsIgnoreCase(request.getMethod())
                && LOGIN_PATH.equals(request.getRequestURI());

        if (!isLoginAttempt) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = request.getRemoteAddr();
        if (isRateLimited(clientIp)) {
            log.warn("Rate limit exceeded for login attempts from {}", clientIp);
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too many login attempts. Try again in a minute.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(String clientIp) {
        long now = System.currentTimeMillis();

        Window window = attemptsByIp.compute(clientIp, (ip, existing) -> {
            if (existing == null || now - existing.windowStart() > WINDOW_MILLIS) {
                // No window yet, or the previous one expired -- start a fresh one.
                return new Window(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });

        return window.count().get() > MAX_ATTEMPTS_PER_WINDOW;
    }
}
