package com.github.leyland.letool.log.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;

/**
 * 基于当前 Servlet 请求解析审计上下文的默认实现。
 *
 * <p>客户端地址使用 Servlet 容器提供的远端地址，不直接信任可伪造的代理转发请求头。
 * 部署在可信网关后的应用可以替换 {@link AuditContextProvider}，按自身网络边界解析地址。</p>
 */
public class ServletAuditContextProvider implements AuditContextProvider {

    /**
     * 从当前 Servlet 请求读取 Principal、远端地址和 User-Agent。
     *
     * @return 当前请求的审计上下文；非请求线程返回空上下文
     */
    @Override
    public AuditContext getCurrentContext() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return AuditContext.empty();
        }

        HttpServletRequest request = servletAttributes.getRequest();
        Principal principal = request.getUserPrincipal();
        String operator = principal == null ? null : principal.getName();
        return new AuditContext(
                operator,
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }
}
