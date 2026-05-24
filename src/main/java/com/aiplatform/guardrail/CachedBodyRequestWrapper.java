package com.aiplatform.guardrail;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * ══════════════════════════════════════════════════════════════════════════
 * CachedBodyRequestWrapper — Allow the request body to be read more than once
 * ══════════════════════════════════════════════════════════════════════════
 *
 * Problem:
 *   An HTTP servlet request body (InputStream) can only be read once.
 *   Once InputGuardrailFilter reads it for injection checking, the
 *   downstream controller (Spring MVC / Jackson) would get an empty stream.
 *
 * Solution:
 *   This wrapper eagerly reads the full body into a byte array on construction,
 *   then re-serves it as a new ByteArrayInputStream every time getInputStream()
 *   or getReader() is called.
 *
 *   After the filter sanitises the body, it passes this wrapper to
 *   chain.doFilter() instead of the original request.  Jackson then reads
 *   from the cached bytes normally.
 *
 * MERN/Next.js analogy:
 *   In Express, middleware can call next() with a modified req.body because
 *   body-parser has already parsed the stream.  In raw servlet land, we must
 *   cache the stream manually to achieve the same re-readability.
 *   Node.js streams have similar "consumed" semantics; you'd collect the buffer:
 *     const body = []
 *     req.on('data', chunk => body.push(chunk))
 *     req.on('end', () => { req.rawBody = Buffer.concat(body) })
 *
 * Book ref: Chapter 9 — Middleware & Guardrails
 *   Infrastructure plumbing required to implement a request-body guardrail
 *   in the Java/Spring servlet model.
 * ══════════════════════════════════════════════════════════════════════════
 */
public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] cachedBody;

    /**
     * Construct the wrapper by eagerly consuming the request body stream.
     *
     * @param request the original HTTP request
     * @throws IOException if the stream cannot be read
     */
    public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // readAllBytes() drains the original InputStream into a byte array.
        this.cachedBody = request.getInputStream().readAllBytes();
    }

    /** Expose the cached bytes (used by the filter for content inspection). */
    public byte[] getCachedBody() {
        return cachedBody;
    }

    /**
     * Re-serve the cached body as a fresh ServletInputStream.
     * Called by Jackson/Spring MVC when deserialising @RequestBody.
     */
    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override public int read()               { return bais.read(); }
            @Override public boolean isFinished()     { return bais.available() == 0; }
            @Override public boolean isReady()        { return true; }
            @Override public void setReadListener(ReadListener rl) { /* no-op */ }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
