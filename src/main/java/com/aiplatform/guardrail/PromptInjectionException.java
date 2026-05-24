package com.aiplatform.guardrail;

/**
 * Thrown when the request body contains patterns that look like prompt injection.
 *
 * MERN analogy: a ZodError thrown by a custom .refine() rule, or throwing
 * from Express middleware when sanitisation fails.
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   "Input validation is the first line of defence. Reject anything that
 *    attempts to hijack the system prompt before it reaches the LLM."
 */
public class PromptInjectionException extends RuntimeException {
    public PromptInjectionException(String message) {
        super(message);
    }
}
