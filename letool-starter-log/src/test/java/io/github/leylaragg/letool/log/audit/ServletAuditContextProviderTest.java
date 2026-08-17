package io.github.leylaragg.letool.log.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ServletAuditContextProvider} Servlet 审计上下文测试。
 */
class ServletAuditContextProviderTest {

    /**
     * 每个测试结束后清理 Spring 请求上下文。
     */
    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * Servlet 请求存在时应读取 Principal、远端地址和 User-Agent。
     */
    @Test
    void shouldResolveServletAuditContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setUserPrincipal(() -> "leyland");
        request.setRemoteAddr("10.10.0.8");
        request.addHeader("User-Agent", "JUnit-Agent");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AuditContext context = new ServletAuditContextProvider().getCurrentContext();

        assertThat(context.operator()).isEqualTo("leyland");
        assertThat(context.clientIp()).isEqualTo("10.10.0.8");
        assertThat(context.userAgent()).isEqualTo("JUnit-Agent");
    }

    /**
     * 非请求线程调用时应返回空上下文。
     */
    @Test
    void shouldReturnEmptyContextWithoutServletRequest() {
        AuditContext context = new ServletAuditContextProvider().getCurrentContext();

        assertThat(context).isEqualTo(AuditContext.empty());
    }
}
