package com.github.leyland.letool.monitor.alert;

import com.github.leyland.letool.monitor.config.MonitorProperties;
import com.github.leyland.letool.monitor.exception.MonitorException;
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
     * 测试用本地 Webhook 服务，记录最近一次请求。
     */
    static class WebhookServer implements AutoCloseable {

        private final HttpServer server;
        private final ExecutorService executor;
        private volatile String method;
        private volatile String pathAndQuery;
        private volatile String body;

        private WebhookServer(HttpServer server, ExecutorService executor) {
            this.server = server;
            this.executor = executor;
        }

        static WebhookServer start(String responseBody) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            WebhookServer holder = new WebhookServer(server, executor);
            server.createContext("/", exchange -> holder.handle(exchange, responseBody));
            server.setExecutor(executor);
            server.start();
            return holder;
        }

        String url(String pathAndQuery) {
            return "http://127.0.0.1:" + server.getAddress().getPort() + pathAndQuery;
        }

        private void handle(HttpExchange exchange, String responseBody) throws IOException {
            method = exchange.getRequestMethod();
            pathAndQuery = exchange.getRequestURI().toString();
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
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
