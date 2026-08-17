package io.github.leylaragg.letool.monitor.alert;

import io.github.leylaragg.letool.monitor.config.MonitorProperties;
import io.github.leylaragg.letool.monitor.exception.MonitorException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 钉钉和企业微信 Webhook 通知渠道测试。
 *
 * <p>通过本地 HTTP server 验证 notifier 会发起真实 POST 请求，并正确处理服务端错误响应。</p>
 */
class WebhookNotifierTest {

    /**
     * 验证钉钉通知会发送 Markdown 格式的 Webhook 请求。
     */
    @Test
    void dingTalkNotifierShouldPostMarkdownPayload() throws Exception {
        try (WebhookServer server = WebhookServer.start("{\"errcode\":0,\"errmsg\":\"ok\"}")) {
            MonitorProperties properties = new MonitorProperties();
            properties.getAlert().getDingtalk().setWebhookUrl(server.url("/dingtalk?access_token=test"));

            new DingTalkNotifier(properties).send("CPU 告警", "CPU 使用率超过 90%");

            assertEquals("POST", server.method);
            assertEquals("/dingtalk?access_token=test", server.pathAndQuery);
            assertTrue(server.body.contains("\"msgtype\":\"markdown\""));
            assertTrue(server.body.contains("\"title\":\"CPU 告警\""));
            assertTrue(server.body.contains("CPU 使用率超过 90%"));
        }
    }

    /**
     * 验证配置钉钉 secret 时，请求 URL 会追加 timestamp 和 sign 参数。
     */
    @Test
    void dingTalkNotifierShouldAppendSignWhenSecretConfigured() throws Exception {
        try (WebhookServer server = WebhookServer.start("{\"errcode\":0,\"errmsg\":\"ok\"}")) {
            MonitorProperties properties = new MonitorProperties();
            properties.getAlert().getDingtalk().setWebhookUrl(server.url("/dingtalk?access_token=test"));
            properties.getAlert().getDingtalk().setSecret("SEC-test");

            new DingTalkNotifier(properties).send("内存告警", "堆内存过高");

            assertNotNull(server.pathAndQuery);
            assertTrue(server.pathAndQuery.startsWith("/dingtalk?access_token=test&timestamp="));
            assertTrue(server.pathAndQuery.contains("&sign="));
        }
    }

    /**
     * 验证企业微信通知会发送文本格式的 Webhook 请求。
     */
    @Test
    void wechatNotifierShouldPostTextPayload() throws Exception {
        try (WebhookServer server = WebhookServer.start("{\"errcode\":0,\"errmsg\":\"ok\"}")) {
            MonitorProperties properties = new MonitorProperties();
            properties.getAlert().getWechat().setWebhookUrl(server.url("/wechat?key=test"));

            new WechatNotifier(properties).send("订单告警", "订单失败率过高");

            assertEquals("POST", server.method);
            assertEquals("/wechat?key=test", server.pathAndQuery);
            assertTrue(server.body.contains("\"msgtype\":\"text\""));
            assertTrue(server.body.contains("订单失败率过高"));
        }
    }

    /**
     * 验证 Webhook 返回业务错误码时，通知渠道会抛出监控异常。
     */
    @Test
    void notifierShouldThrowWhenWebhookReturnsErrorCode() throws Exception {
        try (WebhookServer server = WebhookServer.start("{\"errcode\":310000,\"errmsg\":\"invalid webhook\"}")) {
            MonitorProperties properties = new MonitorProperties();
            properties.getAlert().getWechat().setWebhookUrl(server.url("/wechat?key=bad"));

            MonitorException ex = assertThrows(MonitorException.class,
                    () -> new WechatNotifier(properties).send("告警", "失败"));
            assertTrue(ex.getMessage().contains("invalid webhook"));
        }
    }

    /**
     * 验证非法 Webhook 地址也会遵循统一监控异常契约。
     */
    @Test
    void notifierShouldWrapInvalidWebhookUrl() {
        MonitorProperties properties = new MonitorProperties();
        properties.getAlert().getWechat().setWebhookUrl("not a valid uri");

        MonitorException exception = assertThrows(
                MonitorException.class,
                () -> new WechatNotifier(properties).send("告警", "失败"));

        assertEquals("MONITOR_WEBHOOK_DELIVERY_FAILED", exception.getCode());
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
    }

    /**
     * 验证成功状态中的非法 JSON 响应不会泄漏底层序列化异常。
     */
    @Test
    void notifierShouldWrapMalformedJsonResponse() throws Exception {
        try (WebhookServer server = WebhookServer.start("not-json")) {
            MonitorProperties properties = new MonitorProperties();
            properties.getAlert().getWechat()
                    .setWebhookUrl(server.url("/wechat?key=bad-json"));

            MonitorException exception = assertThrows(
                    MonitorException.class,
                    () -> new WechatNotifier(properties).send("告警", "失败"));

            assertEquals(
                    "MONITOR_WEBHOOK_DELIVERY_FAILED",
                    exception.getCode());
            assertNotNull(exception.getCause());
        }
    }

    /**
     * 验证非成功 HTTP 状态会转换为统一监控异常。
     */
    @Test
    void notifierShouldRejectNonSuccessHttpStatus() throws Exception {
        try (WebhookServer server = WebhookServer.start(
                503,
                "{\"message\":\"unavailable\"}")) {
            MonitorProperties properties = new MonitorProperties();
            properties.getAlert().getWechat()
                    .setWebhookUrl(server.url("/wechat?key=unavailable"));

            MonitorException exception = assertThrows(
                    MonitorException.class,
                    () -> new WechatNotifier(properties).send("告警", "失败"));

            assertEquals(
                    "MONITOR_WEBHOOK_DELIVERY_FAILED",
                    exception.getCode());
            assertTrue(exception.getMessage().contains("HTTP 503"));
        }
    }

    /**
     * 验证空成功响应不会被误判为投递成功。
     */
    @Test
    void notifierShouldRejectEmptyResponse() throws Exception {
        try (WebhookServer server = WebhookServer.start("")) {
            MonitorProperties properties = new MonitorProperties();
            properties.getAlert().getWechat()
                    .setWebhookUrl(server.url("/wechat?key=empty"));

            MonitorException exception = assertThrows(
                    MonitorException.class,
                    () -> new WechatNotifier(properties).send("告警", "失败"));

            assertEquals(
                    "MONITOR_WEBHOOK_DELIVERY_FAILED",
                    exception.getCode());
        }
    }

    /**
     * 测试用本地 Webhook 服务，记录最近一次请求。
     */
    static class WebhookServer implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;
        private volatile String method;
        private volatile String pathAndQuery;
        private volatile String body;

        /**
         * 创建测试 Webhook 服务包装器。
         *
         * @param server 本地 HTTP 服务
         * @param executor 请求处理线程池
         */
        private WebhookServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        /**
         * 启动返回成功状态的本地 Webhook 服务。
         *
         * @param responseBody HTTP 响应体
         * @return 已启动的测试服务
         * @throws IOException 本地端口绑定失败
         */
        static WebhookServer start(String responseBody) throws IOException {
            return start(200, responseBody);
        }

        /**
         * 启动返回指定状态和响应体的本地 Webhook 服务。
         *
         * @param statusCode HTTP 响应状态
         * @param responseBody HTTP 响应体
         * @return 已启动的测试服务
         * @throws IOException 本地端口绑定失败
         */
        static WebhookServer start(
                int statusCode,
                String responseBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            WebhookServer holder = new WebhookServer(server, executor);
            server.createContext(
                    "/",
                    exchange -> holder.handle(
                            exchange,
                            statusCode,
                            responseBody));
            server.setExecutor(executor);
            server.start();
            return holder;
        }

        /**
         * 构造当前测试服务上的完整 URL。
         *
         * @param pathAndQuery 请求路径和查询参数
         * @return 可访问的本地 URL
         */
        String url(String pathAndQuery) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + pathAndQuery;
        }

        /**
         * 记录请求并返回预设响应。
         *
         * @param exchange 当前 HTTP 交换
         * @param statusCode HTTP 响应状态
         * @param responseBody HTTP 响应体
         * @throws IOException 读取请求或写出响应失败
         */
        private void handle(
                HttpExchange exchange,
                int statusCode,
                String responseBody) throws IOException {
            method = exchange.getRequestMethod();
            pathAndQuery = exchange.getRequestURI().toString();
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        /**
         * 停止本地服务并释放请求线程池。
         */
        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
