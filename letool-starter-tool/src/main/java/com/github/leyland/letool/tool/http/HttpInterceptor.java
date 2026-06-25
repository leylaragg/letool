package com.github.leyland.letool.tool.http;

/**
 * HTTP 拦截器接口——在请求发送前、响应返回后、发生错误时提供切入能力.
 *
 * <h3>三个切面</h3>
 * <table>
 *   <tr><th>方法</th><th>调用时机</th><th>典型用途</th></tr>
 *   <tr><td>{@link #beforeRequest(HttpRequest)}</td><td>请求发送前</td><td>统一添加认证头、日志记录</td></tr>
 *   <tr><td>{@link #afterResponse(HttpRequest, HttpResponse)}</td><td>收到响应后</td><td>响应日志、指标采集、签名验证</td></tr>
 *   <tr><td>{@link #onError(HttpRequest, Exception)}</td><td>请求异常时</td><td>异常记录、告警通知</td></tr>
 * </table>
 *
 * <p>所有方法均为 default 方法，实现类只需覆写需要的切面.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 日志拦截器
 * HttpInterceptor logInterceptor = new HttpInterceptor() {
 *     public void beforeRequest(HttpRequest req) {
 *         log.info("Request: {} {}", req.getMethod(), req.getUrl());
 *     }
 *     public void afterResponse(HttpRequest req, HttpResponse resp) {
 *         log.info("Response: {} {} ({}ms)", req.getMethod(), resp.getStatusCode(), resp.getDurationMs());
 *     }
 * };
 *
 * HttpUtil.create("https://api.example.com/data")
 *     .interceptor(logInterceptor)
 *     .execute();
 * }</pre>
 */
public interface HttpInterceptor {

    /**
     * 请求发送前调用，可在此修改请求（如添加动态 Header）.
     *
     * @param request 即将发送的请求对象
     */
    default void beforeRequest(HttpRequest request) {}

    /**
     * 收到响应后调用.
     *
     * @param request  原始请求对象
     * @param response 收到的响应对象
     */
    default void afterResponse(HttpRequest request, HttpResponse response) {}

    /**
     * 请求过程中发生异常时调用（连接超时、SSL 错误、IO 异常等）.
     *
     * @param request 原始请求对象
     * @param e       捕获到的异常
     */
    default void onError(HttpRequest request, Exception e) {}
}
