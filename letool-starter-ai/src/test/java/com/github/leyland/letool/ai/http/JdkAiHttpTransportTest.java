package com.github.leyland.letool.ai.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JdkAiHttpTransport} 的真实本地 HTTP 行为测试。
 */
class JdkAiHttpTransportTest {

    /**
     * 验证默认传输层会发送 JSON POST，并返回状态码和响应体。
     */
    @Test
    void postShouldSendJsonRequestAndReturnResponse() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.start(false)) {
            JdkAiHttpTransport transport = new JdkAiHttpTransport();

            AiHttpResponse response = transport.post(AiHttpRequest.postJson(
                    server.baseUrl() + "/chat/completions",
                    Map.of("Authorization", "Bearer sk-test"),
                    "{\"hello\":\"world\"}",
                    500,
                    3000));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("ok");
            assertThat(server.requestBody).isEqualTo("{\"hello\":\"world\"}");
            assertThat(server.authorization).isEqualTo("Bearer sk-test");
        }
    }

    /**
     * 验证默认传输层会按行回调 SSE 响应内容。
     */
    @Test
    void postStreamShouldCallbackResponseLines() throws Exception {
        try (LocalHttpServer server = LocalHttpServer.start(true)) {
            JdkAiHttpTransport transport = new JdkAiHttpTransport();
            List<String> lines = new ArrayList<>();

            transport.postStream(AiHttpRequest.postJson(
                    server.baseUrl() + "/chat/completions",
                    Map.of("Accept", "text/event-stream"),
                    "{\"stream\":true}",
                    500,
                    3000), lines::add);

            assertThat(lines).containsExactly(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"A\"}}]}",
                    "data: [DONE]");
        }
    }

    /**
     * 测试用本地 HTTP 服务，分别模拟普通 JSON 响应和 SSE 响应。
     */
    static class LocalHttpServer implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;
        private volatile String requestBody;
        private volatile String authorization;

        private LocalHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static LocalHttpServer start(boolean streaming) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            LocalHttpServer holder = new LocalHttpServer(server, executor);
            server.createContext("/chat/completions", exchange -> holder.handle(exchange, streaming));
            server.setExecutor(executor);
            server.start();
            return holder;
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        private void handle(HttpExchange exchange, boolean streaming) throws IOException {
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String body = streaming
                    ? "data: {\"choices\":[{\"delta\":{\"content\":\"A\"}}]}\n\n"
                    + "data: [DONE]\n\n"
                    : "{\"ok\":true}";
            exchange.getResponseHeaders().add("Content-Type",
                    streaming ? "text/event-stream; charset=utf-8" : "application/json; charset=utf-8");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
