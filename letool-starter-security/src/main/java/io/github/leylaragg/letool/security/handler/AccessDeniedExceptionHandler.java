package io.github.leylaragg.letool.security.handler;

import io.github.leylaragg.letool.security.exception.SecurityErrorCode;
import io.github.leylaragg.letool.tool.model.R;
import io.github.leylaragg.letool.tool.util.JsonUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 权限拒绝处理器，当已登录用户访问无权限资源时返回 403 JSON 响应。
 *
 * <p>实现 Spring Security 的 {@link AccessDeniedHandler} 接口，
 * 响应使用 {@link SecurityErrorCode#ACCESS_DENIED} 的稳定错误码。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class AccessDeniedExceptionHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(AccessDeniedExceptionHandler.class);

    /**
     * 处理权限拒绝请求，返回 403 状态码和 JSON 错误信息。
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param accessDeniedException Spring Security 权限拒绝异常
     * @throws IOException 当响应写入失败时抛出
     */
    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        log.debug("Access denied for {}, exception type: {}",
                request.getRequestURI(),
                accessDeniedException.getClass().getSimpleName());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        SecurityErrorCode errorCode = SecurityErrorCode.ACCESS_DENIED;
        R<Void> body = R.fail(
                errorCode.getCode(),
                errorCode.getDefaultMessage()
        );
        response.getWriter().write(JsonUtil.toJsonString(body));
    }
}
