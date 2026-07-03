package com.github.leyland.letool.ai.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * 基于 JDK {@link HttpClient} 的 AI HTTP 传输层默认实现。
 *
 * <p>该实现不引入额外依赖，按连接超时时间复用 {@link HttpClient} 实例，
 * 从而利用 JDK 客户端自身的连接复用能力。</p>
 */
public class JdkAiHttpTransport implements AiHttpTransport {

    /**
     * 不同 provider 可能配置不同连接超时，因此按超时值缓存客户端。
     */
    private final ConcurrentMap<Integer, HttpClient> clients = new ConcurrentHashMap<>();

    @Override
    public AiHttpResponse post(AiHttpRequest request) throws IOException {
        HttpRequest httpRequest = buildRequest(request);
        try {
            HttpResponse<String> response = clientFor(request)
                    .send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new AiHttpResponse(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException("AI HTTP request interrupted");
            interrupted.initCause(e);
            throw interrupted;
        }
    }

    @Override
    public AiHttpResponse postStream(AiHttpRequest request, Consumer<String> onLine) throws IOException {
        HttpRequest httpRequest = buildRequest(request);
        try {
            HttpResponse<java.io.InputStream> response = clientFor(request)
                    .send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                try (java.io.InputStream body = response.body()) {
                    String errorBody = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                    return new AiHttpResponse(response.statusCode(), errorBody);
                }
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isEmpty()) {
                        onLine.accept(line);
                    }
                }
            }
            return new AiHttpResponse(response.statusCode(), "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException("AI HTTP stream interrupted");
            interrupted.initCause(e);
            throw interrupted;
        }
    }

    private HttpClient clientFor(AiHttpRequest request) {
        int connectTimeout = normalizeTimeout(request.connectTimeoutMillis(), 10000);
        return clients.computeIfAbsent(connectTimeout, timeout -> HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build());
    }

    private HttpRequest buildRequest(AiHttpRequest request) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(request.url()))
                .timeout(Duration.ofMillis(normalizeTimeout(request.readTimeoutMillis(), 60000)))
                .POST(HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8));

        for (Map.Entry<String, String> header : request.headers().entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        return builder.build();
    }

    private int normalizeTimeout(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
