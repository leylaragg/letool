package io.github.leylaragg.letool.security.handler;

import io.github.leylaragg.letool.security.exception.SecurityErrorCode;
import io.github.leylaragg.letool.tool.model.R;
import io.github.leylaragg.letool.tool.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证失败处理器，当未登录用户访问受保护资源时返回 401 JSON 响应。
 *
 * <p>实现 Spring Security 的 {@link AuthenticationEntryPoint} 接口，
 * 响应使用 {@link SecurityErrorCode#UNAUTHENTICATED} 的稳定错误码。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class SecurityExceptionHandler implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

    /**
     * 处理未认证请求，返回 401 状态码和 JSON 错误信息。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param authException Spring Security 认证异常
     * @throws IOException 当响应写入失败时抛出
     */
    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        log.debug("Authentication failed for {}, exception type: {}",
                request.getRequestURI(),
                authException.getClass().getSimpleName());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SecurityErrorCode errorCode = SecurityErrorCode.UNAUTHENTICATED;
        R<Void> body = R.fail(
                errorCode.getCode(),
                errorCode.getDefaultMessage()
        );
        response.getWriter().write(JsonUtil.toJsonString(body));
    }
}
