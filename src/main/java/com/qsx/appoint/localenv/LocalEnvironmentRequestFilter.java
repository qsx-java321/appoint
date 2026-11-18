package com.qsx.appoint.localenv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qsx.appoint.common.ErrorCode;
import com.qsx.appoint.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class LocalEnvironmentRequestFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Appoint-Environment";

    private final LocalEnvironmentLifecycle lifecycle;
    private final ObjectMapper objectMapper;

    public LocalEnvironmentRequestFilter(LocalEnvironmentLifecycle lifecycle, ObjectMapper objectMapper) {
        this.lifecycle = lifecycle;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String profile = request.getHeader(HEADER);
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (profile == null || profile.equals(lifecycle.name()) || !path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        var error = ErrorCode.LOCAL_ENVIRONMENT_MISMATCH;
        response.setStatus(error.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Result.failure(error));
    }
}
