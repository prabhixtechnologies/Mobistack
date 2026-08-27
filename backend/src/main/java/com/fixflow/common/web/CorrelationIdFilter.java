package com.fixflow.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request an id that appears in the logs, in the response header,
 * and in any error body.
 *
 * <p>Without it, a shopkeeper reporting "it failed at about four o'clock" leaves
 * nothing to search for. With it, the message they were shown identifies the
 * exact request across every log line it produced.
 */
@Component
@Order(1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String ATTRIBUTE = "mobistack.requestId";

    private static final int MAX_SUPPLIED_LENGTH = 64;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String id = supplied(request.getHeader(HEADER));
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        MDC.put(MDC_KEY, id);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Honours a caller-supplied id so a trace can span the browser and the API,
     * but bounds and sanitises it — this value reaches log files and headers.
     */
    private static String supplied(String header) {
        if (header == null || header.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String trimmed = header.trim();
        if (trimmed.length() > MAX_SUPPLIED_LENGTH) {
            trimmed = trimmed.substring(0, MAX_SUPPLIED_LENGTH);
        }
        return trimmed.replaceAll("[^A-Za-z0-9._:-]", "");
    }

    /** The id for the request being handled, for error bodies and audit trails. */
    public static String current(HttpServletRequest request) {
        Object id = request == null ? null : request.getAttribute(ATTRIBUTE);
        return id instanceof String value ? value : null;
    }
}
