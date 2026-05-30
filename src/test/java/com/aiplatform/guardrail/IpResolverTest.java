package com.aiplatform.guardrail;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpResolverTest {

    @Test
    void resolve_xForwardedFor_returnsFirstEntry() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50, 70.41.3.18, 150.172.238.178");
        assertEquals("203.0.113.50", IpResolver.resolve(request));
    }

    @Test
    void resolve_xRealIp_whenNoXff() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.22");
        assertEquals("198.51.100.22", IpResolver.resolve(request));
    }

    @Test
    void resolve_remoteAddr_whenNoHeaders() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        assertEquals("127.0.0.1", IpResolver.resolve(request));
    }

    @Test
    void resolve_blankXff_fallsToXRealIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getHeader("X-Real-IP")).thenReturn("10.0.0.5");
        assertEquals("10.0.0.5", IpResolver.resolve(request));
    }

    @Test
    void resolve_singleXff_noComma() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100");
        assertEquals("192.168.1.100", IpResolver.resolve(request));
    }
}
