package com.aiplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 * Concise AI retry logging — replaces Spring AI's default verbose output.
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Problem:
 *   Spring AI's built-in RetryListener logs the full exception stack trace
 *   every time a transient error (503 rate-limit, 429 quota, network blip)
 *   triggers a retry.  This clutters logs with multi-hundred-line traces
 *   for situations that self-heal on the next attempt.
 *
 * Solution:
 *   Register a custom RetryListener bean.  Spring AI's SpringAiRetryAutoConfiguration
 *   collects ALL RetryListener beans from the context and registers them.
 *   We silence the built-in one via logging.level in application.yml and
 *   replace it with this single-line log.
 *
 * MERN/Next.js analogy:
 *   // axios-retry with custom onRetry hook instead of default console.error:
 *   axiosRetry(axios, {
 *     retries: 3,
 *     onRetry: (count, err) => logger.warn(`LLM retry ${count}: ${err.message}`)
 *   })
 *
 * Book ref: Chapter 27 — Evaluations Overview
 *   "Noisy logs hide real signal.  Keep retry noise at WARN with one line;
 *    reserve ERROR + stack trace for non-recoverable failures."
 * ═══════════════════════════════════════════════════════════════════════════
 */
@Configuration
public class RetryLoggingConfig {

    @Bean
    public RetryListener conciseAiRetryListener() {
        return new RetryListener() {

            private final Logger log = LoggerFactory.getLogger("com.nutritioncoach.ai.retry");

            /**
             * Called after each failed attempt (before the next retry or final failure).
             *
             * MERN analogy: the onRetry callback in axios-retry / p-retry.
             */
            @Override
            public <T, E extends Throwable> void onError(
                    RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {

                // Extract just the first line of the exception message — no stack trace.
                String msg = throwable.getMessage();
                String firstLine = (msg != null && msg.contains("\n"))
                        ? msg.substring(0, msg.indexOf('\n')).trim()
                        : (msg != null ? msg.trim() : throwable.getClass().getSimpleName());

                log.warn("AI call failed (attempt {}/{}): {}",
                        context.getRetryCount() + 1,
                        // retryCount is 0-based; max attempts come from the retry policy
                        "?",
                        firstLine);
            }
        };
    }
}
