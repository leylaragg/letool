package io.github.leylaragg.letool.tool.http;

/**
 * HTTP 静态便利入口。
 *
 * <p>静态方法使用不可变默认配置和共享 JDK HTTP 客户端，适合简单调用。需要独立超时、响应上限、代理、
 * TLS 或认证配置时，应创建或注入 {@link HttpTemplate}，避免修改进程级全局状态。</p>
 */
public final class HttpUtil {

    /** 不可变静态默认模板。 */
    private static final HttpTemplate DEFAULT_TEMPLATE = new HttpTemplate();

    /** 工具类不允许实例化。 */
    private HttpUtil() {
    }

    /**
     * 创建尚未设置 URL 的请求构建器。
     *
     * @return 绑定静态默认模板的请求构建器
     */
    public static HttpRequest create() {
        return DEFAULT_TEMPLATE.create();
    }

    /**
     * 创建带请求地址的请求构建器。
     *
     * @param url HTTP 或 HTTPS 地址
     * @return 绑定静态默认模板的请求构建器
     */
    public static HttpRequest create(String url) {
        return DEFAULT_TEMPLATE.create(url);
    }

    /**
     * 使用指定不可变配置创建独立 HTTP 模板。
     *
     * @param config HTTP 基础配置
     * @return 独立 HTTP 模板
     */
    public static HttpTemplate template(HttpConfig config) {
        return new HttpTemplate(config);
    }

    /**
     * 发送 GET 请求并返回文本响应体。
     *
     * @param url HTTP 或 HTTPS 地址
     * @return 文本响应体
     */
    public static String get(String url) {
        return create(url).get().execute().getBody();
    }

    /**
     * 发送 JSON POST 请求并返回文本响应体。
     *
     * @param url HTTP 或 HTTPS 地址
     * @param body JSON 请求体
     * @return 文本响应体
     */
    public static String post(String url, String body) {
        return create(url).post().contentType("application/json").body(body).execute().getBody();
    }

    /**
     * 发送 JSON PUT 请求并返回文本响应体。
     *
     * @param url HTTP 或 HTTPS 地址
     * @param body JSON 请求体
     * @return 文本响应体
     */
    public static String put(String url, String body) {
        return create(url).put().contentType("application/json").body(body).execute().getBody();
    }

    /**
     * 发送 DELETE 请求并返回文本响应体。
     *
     * @param url HTTP 或 HTTPS 地址
     * @return 文本响应体
     */
    public static String delete(String url) {
        return create(url).delete().execute().getBody();
    }

    /**
     * 获取静态便利入口使用的默认模板。
     *
     * @return 不可变默认模板
     */
    static HttpTemplate defaultTemplate() {
        return DEFAULT_TEMPLATE;
    }
}
