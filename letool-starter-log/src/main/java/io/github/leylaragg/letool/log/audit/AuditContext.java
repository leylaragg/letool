package io.github.leylaragg.letool.log.audit;

/**
 * 当前审计操作的调用方上下文。
 *
 * <p>该对象只保存审计切面需要的通用信息，不绑定 Spring Security、租户框架或网关协议。
 * 业务应用可以通过 {@link AuditContextProvider} 接入自己的身份与网络上下文。</p>
 *
 * @param operator 当前操作人标识，无法确定时允许为 {@code null}
 * @param clientIp 客户端地址，无法确定时允许为 {@code null}
 * @param userAgent 客户端 User-Agent，无法确定时允许为 {@code null}
 */
public record AuditContext(
        String operator,
        String clientIp,
        String userAgent) {

    /**
     * 创建不包含调用方信息的空上下文。
     *
     * @return 所有字段均为空的审计上下文
     */
    public static AuditContext empty() {
        return new AuditContext(null, null, null);
    }
}
