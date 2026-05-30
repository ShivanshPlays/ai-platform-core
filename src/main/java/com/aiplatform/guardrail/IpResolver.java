package com.aiplatform.guardrail;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP from an HttpServletRequest, handling
 * reverse proxies (nginx, Cloudflare, ALB) that set forwarding headers.
 *
 * Priority: X-Forwarded-For → X-Real-IP → request.getRemoteAddr()
 */
public final class IpResolver {

    private IpResolver() {}

    /**
     * Extracts the originating client IP address from the request.
     * When behind a proxy, the first entry in X-Forwarded-For is the real client.
     */
    public static String resolve(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 — take the first
            String clientIp = xff.split(",")[0].trim();
            if (!clientIp.isEmpty()) {
                return clientIp;
            }
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }

        return request.getRemoteAddr();
    }
}
