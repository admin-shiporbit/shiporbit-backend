package com.shiporbit.backend.logging;

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
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * First filter of every request (before Spring Security): sets the trace id and opens the numbered flow.
 * <ul>
 *   <li>trace id: taken from request header trace_id (or X-Trace-Id / traceId) - set it in Postman to find
 *       your request in the log. Missing or invalid -> a new UUID is generated.</li>
 *   <li>Allowed format: 1-64 chars of letters, digits, '.', '_', ':', '-' (anything else is rejected so a
 *       header cannot inject fake log lines).</li>
 *   <li>The trace id is returned in response headers trace_id and X-Trace-Id, and in every error body.</li>
 *   <li>Steps: first "Request received", last "Response &lt;status&gt; in &lt;ms&gt;". Health checks log at DEBUG.</li>
 * </ul>
 * grep 'trace_id=&lt;id&gt;' logs/shiporbit-backend.log shows one request end to end.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraceFilter.class);

    public static final String HEADER = "trace_id";
    public static final String HEADER_ALT = "X-Trace-Id";
    private static final List<String> REQUEST_HEADERS = List.of(HEADER, HEADER_ALT, "traceId");
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = suppliedTraceId(request);
        boolean valid = supplied != null && VALID.matcher(supplied).matches();
        String traceId = valid ? supplied : UUID.randomUUID().toString();

        Flow.start(traceId);
        response.setHeader(HEADER, traceId);
        response.setHeader(HEADER_ALT, traceId);
        boolean quiet = request.getRequestURI().startsWith("/actuator/health");
        long start = System.nanoTime();
        try {
            if (supplied != null && !valid) {
                Flow.stepWarn(LOGGER, "Supplied trace_id rejected (allowed: 1-64 of A-Z a-z 0-9 . _ : -); generated {}", traceId);
            }
            Flow.step(LOGGER, quiet ? org.slf4j.event.Level.DEBUG : org.slf4j.event.Level.INFO,
                    "Request received: {} {}{} (trace_id {})", request.getMethod(), request.getRequestURI(),
                    request.getQueryString() == null ? "" : "?" + request.getQueryString(),
                    valid ? "from client" : "generated");
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000;
            int status = response.getStatus();
            org.slf4j.event.Level level = quiet ? org.slf4j.event.Level.DEBUG
                    : status >= 500 ? org.slf4j.event.Level.ERROR
                    : status >= 400 ? org.slf4j.event.Level.WARN : org.slf4j.event.Level.INFO;
            Flow.step(LOGGER, level, "Response {} for {} {} in {} ms", status, request.getMethod(), request.getRequestURI(), ms);
            Flow.end();
        }
    }

    private static String suppliedTraceId(HttpServletRequest request) {
        for (String name : REQUEST_HEADERS) {
            String v = request.getHeader(name);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
