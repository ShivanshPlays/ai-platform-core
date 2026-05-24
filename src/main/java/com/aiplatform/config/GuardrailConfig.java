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
    // Origin-based request validation is handled by OriginAllowlistFilter (@Component).
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:3000}")
    private String[] allowedOrigins;

    /**
     * Allow cross-origin requests from the React frontend (Vercel) and local dev (Vite).
     * SSE endpoints (/api/stream/**, /api/verbose-plan) need CORS to work in browsers.
     *
     * MERN analogy:
     *   app.use(cors({ origin: ['http://localhost:5173', 'https://your-app.vercel.app'] }))
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Content-Type")
                .maxAge(3600);
    }
}