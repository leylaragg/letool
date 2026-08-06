package com.github.leyland.letool.tool.enums;

/**
 * HTTP 请求方法枚举。
 *
 * <p>供 {@link com.github.leyland.letool.tool.http.HttpRequest} 声明请求语义，并用于限制默认自动重试范围。
 * 是否允许重试仍需结合具体业务的幂等约束判断。</p>
 */
public enum HttpMethod {

    /** 获取资源。 */
    GET,

    /** 提交或创建资源，默认不自动重试。 */
    POST,

    /** 创建或完整替换指定资源。 */
    PUT,

    /** 删除指定资源。 */
    DELETE,

    /** 部分更新资源，默认不自动重试。 */
    PATCH,

    /** 仅获取与 GET 相同的响应头信息。 */
    HEAD,

    /** 查询目标资源支持的通信选项。 */
    OPTIONS,

    /** 执行协议诊断回显，默认不自动重试。 */
    TRACE
}
