package com.aiplatform.guardrail;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * InputGuardrailFilter — Prompt injection check on every POST /api/ request
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Runs once per request (OncePerRequestFilter) and intercepts all POST
 * requests to /api/*.  It:
 *   1. Wraps the request in CachedBodyRequestWrapper so the body can be read
 *      twice (once here, once by the controller's @RequestBody deserialiser).
 *   2. Passes the raw JSON body to InputSanitiser for injection detection.
 *   3a. If unsafe: responds immediately with HTTP 400 + JSON error.
 *   3b. If safe: passes the wrapped request down the filter chain.
 *
 * Skips:
 *   • All non-POST requests (GET /api/health, etc.)
 *   • All non-/api/ paths (H2 console, actuator)
 *   • When app.guardrail.enabled = false (default in development)
 *
 * MERN/Next.js analogy:
 *   Equivalent of Express middleware running before route handlers:
 *
 *     app.use('/api', (req, res, next) => {
 *       try {
 *         sanitise(JSON.stringify(req.body))
 *         next()                    // ← chain.doFilter(wrapped, res)
 *       } catch (e) {
 *         res.status(400).json({ error: e.message })
 *       }
 *     })
 *
 *   Or a Next.js middleware.ts file that runs before API routes.
 *
 * Why OncePerRequestFilter vs HandlerInterceptor?
 *   HandlerInterceptor.preHandle() runs *after* the request body has been
 *   consumed by the dispatcher.  A servlet filter runs *before* Spring MVC,
 *   which is what we need to wrap the request body before Jackson reads it.
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   "Apply input sanitisation as early as possible in the request pipeline —
 *    ideally before the input reaches any application logic."
 * ══════════════════════════════════════════════════════════════════════════
 */
@Component
public class InputGuardrailFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InputGuardrailFilter.class);

    private final InputSanitiser sanitiser;
    private final boolean guardrailEnabled;

    public InputGuardrailFilter(InputSanitiser sanitiser,
                                @Value("${app.guardrail.enabled:false}") boolean guardrailEnabled) {
        this.sanitiser = sanitiser;
        this.guardrailEnabled = guardrailEnabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Skip if guardrails are disabled, or if this is not a POST /api/ request.
        // Non-POST requests (health check, H2 console) never carry a body.
        if (!guardrailEnabled
                || !"POST".equalsIgnoreCase(request.getMethod())
                || !request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // Wrap the request to cache the body so it can be re-read by Jackson.
        CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(request);
        String body = new String(wrapped.getCachedBody(), StandardCharsets.UTF_8);

        try {
            sanitiser.checkForInjection(body);
        } catch (PromptInjectionException e) {
            log.warn("InputGuardrailFilter: injection blocked — uri={} body_excerpt={}",
                    request.getRequestURI(), body.substring(0, Math.min(body.length(), 80)));
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\":\"Request contains potentially unsafe content\"}");
            return;
        }

        // Body is clean — continue with the wrapped request so Jackson
        // can still read the body from the cached byte array.
        chain.doFilter(wrapped, response);
    }
}
