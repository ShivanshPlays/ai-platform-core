package com.aiplatform.guardrail;

/**
 * Thrown when OutputModerator detects potentially unsafe advice in a CoachAdvice.
 *
 * MERN analogy: throwing from a response interceptor after checking the AI output
 * against a safety policy before sending the response to the client.
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   "Output moderation is the last line of defence before the response reaches
 *    the user. Never skip it for health or medical domains."
 */
public class UnsafeOutputException extends RuntimeException {
    public UnsafeOutputException(String message) {
        super(message);
    }
}
