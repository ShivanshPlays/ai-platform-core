package com.aiplatform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * GuardrailConfig — Register Phase 6 interceptors with Spring MVC
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Spring MVC does not scan HandlerInterceptors automatically.  They must be
 * registered via a WebMvcConfigurer.  This class does that for Phase 6.
 *
 * InputGuardrailFilter (OncePerRequestFilter) is auto-registered because it
 * is annotated with @Component — Spring Boot's auto-registration picks it up
 * without any explicit FilterRegistrationBean.
 *
 * MERN/Next.js analogy:
 *   In Express this is equivalent to calling app.use() for each middleware:
 *
 *     app.use('/api', apiKeyMiddleware)
 *     app.use('/api', rateLimitMiddleware)
 *
 *   In Next.js it is the middleware.ts file with a matcher config.
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   Centralising interceptor registration makes it easy to audit which
 *   guardrails are in place and in what order they run.
 * ══════════════════════════════════════════════════════════════════════════
 */
@Configuration
public class GuardrailConfig implements WebMvcConfigurer {

    // Allowed CORS origins — override in application.yml with app.cors.allowed-origins
    // MERN analogy: cors({ origin: process.env.ALLOWED_ORIGINS?.split(',') })
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String[] allowedOrigins;

    /**
     * Allow cross-origin requests from the React frontend (Vercel) and local dev (Vite).
     * SSE endpoints (/api/stream/**, /api/verbose-plan) need CORS to work in browsers.
     *
     * Best practices followed (ref: OPTIONS/CORS article):
     *   - allowedOrigins: explicit whitelist, never * — prevents rogue domain access
     *   - allowedMethods: only GET + POST — OPTIONS preflight is handled by Spring automatically
     *   - allowedHeaders: only headers the UI actually sends — no wildcard
     *   - allowCredentials: NOT set — avoids the forbidden * + credentials combination
     *   - maxAge: 3600s — reduces preflight roundtrips without over-caching policy changes
     *
     * MERN analogy:
     *   app.use(cors({ origin: [...], methods: ['GET','POST'], allowedHeaders: [...] }))
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "X-User-Id", "X-User-Tier")
                .exposedHeaders("Content-Type")
                .maxAge(3600);
    }
}