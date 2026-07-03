package com.github.leyland.letool.net.http;

import com.github.leyland.letool.net.exception.NetException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link NetHttpTemplate} 的本地 HTTP 集成测试。
 *
 * <p>使用 JDK 内置 {@link HttpServer} 验证真实 HTTP 请求、请求头/请求体传输和错误状态处理。</p>
 */
class NetHttpTemplateIntegrationTest {

    /**
     * 验证模板会向真实 HTTP 服务发送方法、Header 和 Body。
     */
    @Test
    void shouldSendRequestToLocalHttpServer() throws IOException {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            NetHttpTemplate template = new NetHttpTemplate(1000, 1000);

            String response = template.request()
                    .method("POST")
                    .url(server.url("/echo"))
                    .header("X-Letool-Test", "yes")
                    .bodyBytes("payload".getBytes(StandardCharsets.UTF_8))
                    .execute();

            assertThat(response).isEqualTo("{\"ok\":true}");
            assertThat(server.lastMethod.get()).isEqualTo("POST");
            assertThat(server.lastHeader.get()).isEqualTo("yes");
            assertThat(server.lastBody.get()).isEqualTo("payload");
        }
    }

    /**
     * HTTP 4xx/5xx 应视为请求失败并抛出 NetException，避免调用方误把错误体当成功结果。
     */
    @Test
    void shouldThrowNetExceptionForHttpErrorStatus() throws IOException {
        try (LocalHttpServer server = LocalHttpServer.start()) {
            NetHttpTemplate template = new NetHttpTemplate(1000, 1000);

            assertThatThrownBy(() -> template.get(server.url("/error")))
                    .isInstanceOf(NetException.class)
                    .hasMessageContaining("HTTP GET")
                    .hasMessageContaining("failed with status 500");
        }
    }

    /**
     * 测试用本地 HTTP 服务。
     */
    static class LocalHttpServer implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicReference<String> lastMethod = new AtomicReference<>();
        private final AtomicReference<String> lastHeader = new AtomicReference<>();
        private final AtomicReference<String> lastBody = new AtomicReference<>();

        private LocalHttpServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static LocalHttpServer start() throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            LocalHttpServer holder = new LocalHttpServer(server, executor);
            server.setExecutor(executor);
            server.createContext("/echo", holder::handleEcho);
            server.createContext("/error", holder::handleError);
            server.start();
            return holder;
        }

        String url(String path) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + path;
        }

        private void handleEcho(HttpExchange exchange) throws IOException {
            lastMethod.set(exchange.getRequestMethod());
            lastHeader.set(exchange.getRequestHeaders().getFirst("X-Letool-Test"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"ok\":true}");
        }

        private void handleError(HttpExchange exchange) throws IOException {
            respond(exchange, 500, "{\"error\":\"boom\"}");
        }

        private void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
