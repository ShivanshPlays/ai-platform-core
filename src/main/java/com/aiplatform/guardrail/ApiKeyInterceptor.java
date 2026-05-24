package com.aiplatform.guardrail;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * ApiKeyInterceptor — Require X-Api-Key header on all /api/ requests
 * ══════════════════════════════════════════════════════════════════════════
 *
 * A Spring MVC HandlerInterceptor that validates an API key header before
 * any controller method runs.
 *
 * Configuration (application.yml):
 *   app.guardrail.enabled: true       — must be true for enforcement
 *   app.guardrail.api-key: <secret>   — set via GEMINI_API_KEY env var;
 *                                       defaults to "dev-key" if unset
 *
 * Exempt paths:
 *   GET /api/health — excluded from key check (monitoring probes)
 *   Actuator paths  — excluded (separate security config if needed)
 *
 * When NOT enforced:
 *   - app.guardrail.enabled = false (default in dev)
 *   - app.guardrail.api-key is blank (not configured)
 *   This allows the app to start and be tested without setting the env var.
 *
 * MERN/Next.js analogy:
 *   Equivalent of Express middleware or a Next.js middleware.ts that checks
 *   an API key before routing:
 *
 *     export function middleware(req: NextRequest) {
 *       if (req.headers.get('x-api-key') !== process.env.API_KEY) {
 *         return new Response(JSON.stringify({ error: 'Unauthorized' }), { status: 401 })
 *       }
 *     }
 *
 *   In a real app, replace this with Spring Security + Bearer token / OAuth2.
 *   HandlerInterceptor is sufficient for Phase 6's simple key-based auth.
 *
 * Why HandlerInterceptor and not a filter?
 *   Filters run before Spring MVC and can't easily access Spring beans.
 *   HandlerInterceptor.preHandle() runs after routing but before the
 *   controller method — ideal for auth checks on Controller-level paths.
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   "Authentication at the API gateway level ensures no request reaches
 *    an LLM without a verified identity, enabling per-user rate limiting
 *    and audit logging."
 *
 * NOTE: Not active by default. CORS in GuardrailConfig is the active auth
 * mechanism. Re-activate by adding @Component and registering in GuardrailConfig
 * when proper backend-issued API keys (not Gemini keys) are introduced.
 * ══════════════════════════════════════════════════════════════════════════
 */
// @Component  — inactive; register in GuardrailConfig when backend-issued API keys are added
public class ApiKeyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyInterceptor.class);

    // The expected API key value, injected from application.yml.
    // MERN analogy: process.env.API_KEY
    private final String expectedApiKey;
    private final boolean guardrailEnabled;

    public ApiKeyInterceptor(
            @Value("${app.guardrail.api-key:#{null}}") String expectedApiKey,
            @Value("${app.guardrail.enabled:false}") boolean guardrailEnabled) {
        this.expectedApiKey = expectedApiKey;
        this.guardrailEnabled = guardrailEnabled;
    }

    /**
     * Validate the X-Api-Key header before the controller method runs.
     *
     * @return true  → allow the request to proceed
     *         false → request has been rejected (response already written)
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // Skip enforcement when guardrails are off or key is not configured.
        if (!guardrailEnabled || expectedApiKey == null || expectedApiKey.isBlank()) {
            return true;
        }

        // Skip CORS preflight requests — browsers send OPTIONS without any API key.
        // The CORS handler (GuardrailConfig.addCorsMappings) will respond to these.
        // MERN analogy: if (req.method === 'OPTIONS') { return res.sendStatus(204); }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Health check is always exempt — monitoring probes don't carry an API key.
        if ("/api/health".equals(request.getRequestURI())) {
            return true;
        }

        String provided = request.getHeader("X-Api-Key");
        if (!expectedApiKey.equals(provided)) {
            log.warn("ApiKeyInterceptor: rejected request — uri={} key_provided={}",
                    request.getRequestURI(), provided == null ? "<none>" : "<wrong>");
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Missing or invalid X-Api-Key header\"}");
            return false;
        }

        return true;
    }
}
