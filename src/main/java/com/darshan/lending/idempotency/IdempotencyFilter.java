package com.darshan.lending.idempotency;

import com.darshan.lending.entity.IdempotencyRecord;
import com.darshan.lending.repository.IdempotencyRecordRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private final IdempotencyRecordRepository repo;

    private static final Set<String> PROTECTED_PATHS = Set.of(
            "/loans/",
            "/loan-requests",
            "/loan-offers/",
            "/accounts/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        if (!"POST".equals(request.getMethod())
                || !isProtectedPath(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getHeader("Idempotency-Key");

        if (key == null || key.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        // Validate UUID format
        if (!key.matches(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}" +
                        "-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            response.setStatus(422);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"message\":" +
                            "\"Idempotency-Key must be a valid UUID v4\"}");
            return;
        }

        String path = request.getRequestURI();

        var existing = repo.findByIdempotencyKeyAndRequestPath(key, path);

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            if (record.getExpiresAt().isBefore(LocalDateTime.now())) {
                repo.delete(record);
            } else {
                log.info("Idempotency hit: key={} path={}", key, path);
                response.setStatus(record.getResponseStatus());
                response.setContentType("application/json");
                response.setHeader("Idempotency-Key", key);
                response.setHeader("X-Idempotent-Replayed", "true");
                response.getWriter().write(record.getResponseBody());
                return;
            }
        }

        ContentCachingResponseWrapper wrappedResponse =
                new ContentCachingResponseWrapper(response);

        chain.doFilter(request, wrappedResponse);

        int status = wrappedResponse.getStatus();
        if (status >= 200 && status < 300) {
            String body = new String(wrappedResponse.getContentAsByteArray());
            repo.save(IdempotencyRecord.builder()
                    .idempotencyKey(key)
                    .requestPath(path)
                    .responseBody(body)
                    .responseStatus(status)
                    .build());
            log.info("Idempotency stored: key={} path={} status={}",
                    key, path, status);
        }

        wrappedResponse.copyBodyToResponse();
    }

    private boolean isProtectedPath(String uri) {
        return PROTECTED_PATHS.stream().anyMatch(uri::contains);
    }
}