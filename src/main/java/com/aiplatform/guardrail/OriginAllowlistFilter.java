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
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * OriginAllowlistFilter — Block requests from unknown browser origins
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Replaces the X-Api-Key header approach. The API key should NEVER be sent
 * to the browser. Instead, the backend validates the HTTP Origin header and
 * only allows requests from configured frontend URLs.
 *
 * How it works:
 *   - Origin header present + in allowlist  → allow
 *   - Origin header present + NOT in list   → 403 Forbidden (forged/unknown origin)
 *   - No Origin header at all               → allow (Postman, curl, backend-to-backend)
 *     The AWS Security Group handles IP-level blocking for direct API abuse.
 *
 * Exempt paths (always allowed):
 *   OPTIONS — browser CORS preflight (no Origin check needed; Spring handles it)
 *   /actuator/** — health probes from load balancer
 *   /api/health  — explicit health endpoint
 *
 * Configuration (application.yml):
 *   app.cors.allowed-origins: http://localhost:5173,https://your-app.vercel.app
 *   app.guardrail.origin-check.enabled: true
 *
 * MERN/Next.js analogy:
 *   Equivalent of a Next.js middleware.ts or Express middleware that checks:
 *     if (req.headers.origin && !ALLOWED_ORIGINS.includes(req.headers.origin)) {
 *       return res.status(403).json({ error: 'Origin not allowed' })
 *     }
 *
 * Why not just CORS?
 *   CORS headers instruct the BROWSER to block cross-origin responses, but
 *   they don't prevent the server from processing the request. This filter
 *   rejects at the server level before any business logic runs.
 *
 * AWS Security Group (additional layer):
 *   - Allow port 443 inbound only from Vercel IP ranges or Cloudflare ranges
 *   - This blocks non-browser direct API calls at the network layer
 *   - See README for specific Vercel/Cloudflare CIDR ranges
 *
 * NOTE: Not active by default. CORS in GuardrailConfig (addCorsMappings) is
 * sufficient for browser-based origin validation. Activate this filter by
 * adding @Component if server-level pre-processing rejection is required.
 * ══════════════════════════════════════════════════════════════════════════
 */
// @Component  — inactive; CORS in GuardrailConfig is the active origin guard
public class OriginAllowlistFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OriginAllowlistFilter.class);

    private final Set<String> allowedOrigins;
    private final boolean enabled;

    public OriginAllowlistFilter(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}") String rawOrigins,
            @Value("${app.guardrail.origin-check.enabled:true}") boolean enabled) {
        // Split by comma and trim whitespace — handles YAML multi-line folded strings
        this.allowedOrigins = Arrays.stream(rawOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        this.enabled = enabled;
        log.info("OriginAllowlistFilter initialised — enabled={}, allowedOrigins={}", enabled, this.allowedOrigins);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Kill switch — disable for local dev if needed
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        // Always let OPTIONS (CORS preflight) and health/actuator through
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("OPTIONS".equals(method) || path.startsWith("/actuator") || path.equals("/api/health")) {
            chain.doFilter(request, response);
            return;
        }

        String origin = request.getHeader("Origin");

        // No Origin header — tool/backend-to-backend call; allow but log.
        // AWS Security Group is the network-level guard against direct abuse.
        if (origin == null) {
            chain.doFilter(request, response);
            return;
        }

        // Origin present but not in allowlist → reject
        if (!allowedOrigins.contains(origin.trim())) {
            log.warn("Blocked request from forbidden Origin: {} → {}", origin, path);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Origin not allowed\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
