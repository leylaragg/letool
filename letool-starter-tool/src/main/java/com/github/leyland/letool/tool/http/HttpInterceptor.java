package com.github.leyland.letool.tool.http;

/**
 * HTTP 请求生命周期拦截器。
 *
 * <p>拦截器可用于补充认证请求头、记录请求指标或接收异常通知。所有回调均提供默认空实现，
 * 调用方只需要覆盖实际关心的生命周期方法。拦截器抛出的异常会被转换为统一的
 * {@link HttpException}，避免实现异常直接泄漏到业务层。</p>
 *
 * <p>同一个拦截器实例可能被多个请求线程同时调用，因此实现类不应保存请求级可变状态；
 * 如确有需要，应自行保证线程安全。</p>
 */
public interface HttpInterceptor {

    /**
     * 在请求快照创建前调用，可用于补充动态请求头等请求属性。
     *
     * <p>多个拦截器按照注册顺序执行。此时允许修改传入的请求构建对象，后续发送过程会基于
     * 拦截后的状态创建不可变快照。</p>
     *
     * @param request 即将发送的 HTTP 请求构建对象
     */
    default void beforeRequest(HttpRequest request) {
    }

    /**
     * 在收到并完整校验响应后调用。
     *
     * <p>多个拦截器按照注册顺序执行。本回调不会改变已经创建的响应对象。</p>
     *
     * @param request 已完成发送的 HTTP 请求构建对象
     * @param response 已完成读取的不可变 HTTP 响应
     */
    default void afterResponse(HttpRequest request, HttpResponse response) {
    }

    /**
     * 在请求执行或其他拦截器回调发生异常时调用。
     *
     * <p>该方法主要用于日志和指标采集，不应在实现中再次抛出异常，以免掩盖原始失败原因。</p>
     *
     * @param request 执行失败的 HTTP 请求构建对象
     * @param exception 请求执行期间捕获到的异常
     */
    default void onError(HttpRequest request, Exception exception) {
    }
}
