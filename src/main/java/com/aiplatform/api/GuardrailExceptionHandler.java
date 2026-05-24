package com.aiplatform.api;

import com.aiplatform.guardrail.PromptInjectionException;
import com.aiplatform.guardrail.RateLimitExceededException;
import com.aiplatform.guardrail.UnsafeOutputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * GuardrailExceptionHandler — Map Phase 6 guardrail exceptions to HTTP responses
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Spring @RestControllerAdvice intercepts exceptions thrown by any controller
 * and converts them into well-formed JSON error responses.
 *
 * Exception → HTTP mapping:
 *   PromptInjectionException  → 400 Bad Request
 *   UnsafeOutputException     → 422 Unprocessable Entity
 *   RateLimitExceededException → 429 Too Many Requests
 *
 * MERN/Next.js analogy:
 *   Equivalent of a global error handler in Express:
 *     app.use((err, req, res, next) => {
 *       if (err instanceof PromptInjectionError) return res.status(400).json(...)
 *       if (err instanceof UnsafOutputError)     return res.status(422).json(...)
 *       if (err instanceof RateLimitError)       return res.status(429).json(...)
 *     })
 *
 *   Or in Next.js App Router, the error.ts file that maps errors to responses.
 *
 * Error response shape: { "error": "<message>" }
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   "Guardrail failures should produce informative error responses for
 *    developers while avoiding leaking internal details to end users."
 * ══════════════════════════════════════════════════════════════════════════
 */
@RestControllerAdvice
public class GuardrailExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GuardrailExceptionHandler.class);

    @ExceptionHandler(PromptInjectionException.class)
    public ResponseEntity<Map<String, String>> handleInjection(PromptInjectionException ex) {
        log.warn("Guardrail: prompt injection blocked — {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Request contains potentially unsafe content"));
    }

    @ExceptionHandler(UnsafeOutputException.class)
    public ResponseEntity<Map<String, String>> handleUnsafeOutput(UnsafeOutputException ex) {
        log.warn("Guardrail: unsafe output suppressed — {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("error", "Generated advice was suppressed due to safety concerns"));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimit(RateLimitExceededException ex) {
        log.warn("Guardrail: rate limit exceeded — {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Too many requests — please wait before retrying"));
    }
}
