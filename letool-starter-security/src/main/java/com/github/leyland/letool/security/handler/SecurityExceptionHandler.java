package com.github.leyland.letool.security.handler;

import com.github.leyland.letool.tool.model.R;
import com.github.leyland.letool.tool.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SecurityExceptionHandler implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("Authentication failed for {}: {}", request.getRequestURI(), authException.getMessage());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        R<Void> body = R.fail("AUTH_001", "认证失败，请重新登录");
        response.getWriter().write(JsonUtil.toJsonString(body));
    }
}
