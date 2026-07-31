package com.github.leyland.letool.log.audit;

/**
 * 提供当前调用方审计上下文的扩展接口。
 *
 * <p>默认 Servlet 实现只读取标准 Principal、远端地址和 User-Agent。业务应用可以声明
 * 自定义 Bean，从 Spring Security、租户上下文或可信网关请求头中解析更完整的信息。</p>
 */
@FunctionalInterface
public interface AuditContextProvider {

    /**
     * 获取当前调用线程对应的审计上下文。
     *
     * @return 当前审计上下文；没有可用信息时返回 {@link AuditContext#empty()}
     */
    AuditContext getCurrentContext();

    /**
     * 创建始终返回空上下文的提供器。
     *
     * @return 无状态且可复用的空上下文提供器
     */
    static AuditContextProvider empty() {
        return AuditContext::empty;
    }
}
