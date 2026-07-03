package com.github.leyland.letool.ai.http;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * AI 模块 HTTP 传输层接口。
 *
 * <p>该接口是工具包级扩展点：默认实现足够开箱即用，业务项目也可以替换为
 * OkHttp、Apache HttpClient、公司统一网关客户端或带观测能力的实现。</p>
 */
public interface AiHttpTransport {

    /**
     * 发送普通 JSON POST 请求。
     *
     * @param request HTTP 请求描述
     * @return HTTP 响应
     * @throws IOException 网络或传输异常
     */
    AiHttpResponse post(AiHttpRequest request) throws IOException;

    /**
     * 发送流式 JSON POST 请求，并按行回调响应内容。
     *
     * <p>当响应状态码不是 2xx 时，实现应返回错误响应体，不应回调流式内容。</p>
     *
     * @param request HTTP 请求描述
     * @param onLine  响应行回调
     * @return HTTP 响应
     * @throws IOException 网络或传输异常
     */
    AiHttpResponse postStream(AiHttpRequest request, Consumer<String> onLine) throws IOException;
}
