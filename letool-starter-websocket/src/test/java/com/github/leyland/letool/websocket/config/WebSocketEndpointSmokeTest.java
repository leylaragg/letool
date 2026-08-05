package com.github.leyland.letool.websocket.config;

import com.github.leyland.letool.tool.util.JsonUtil;
import com.github.leyland.letool.websocket.auth.WsAuthenticator;
import com.github.leyland.letool.websocket.core.WsMessage;
import com.github.leyland.letool.websocket.core.WsPrincipal;
import com.github.leyland.letool.websocket.core.WsSession;
import com.github.leyland.letool.websocket.core.WsTemplate;
import com.github.leyland.letool.websocket.handler.WsMessageHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocket 自动配置端点的真实网络冒烟测试。
 */
@SpringBootTest(
        classes = WebSocketEndpointSmokeTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "letool.websocket.path=/ws-test",
                "letool.websocket.heartbeat.enabled=false"
        })
class WebSocketEndpointSmokeTest {

    @LocalServerPort
    private int port;

    /**
     * 验证自定义鉴权、真实握手、消息路由和模板回包能够形成完整闭环。
     *
     * @throws Exception 连接或收发失败时抛出
     */
    @Test
    void shouldAuthenticateAndDispatchMessageThroughRealEndpoint() throws Exception {
        CountDownLatch responseReceived = new CountDownLatch(1);
        AtomicReference<WsMessage> responseMessage = new AtomicReference<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
                    /**
                     * 接收服务端回包。
                     *
                     * @param nativeSession 客户端原生会话
                     * @param textMessage 文本消息
                     */
                    @Override
                    protected void handleTextMessage(
                            WebSocketSession nativeSession,
                            TextMessage textMessage) {
                        WsMessage message = JsonUtil.parseObject(textMessage.getPayload(), WsMessage.class);
                        if ("echo-response".equals(message.getType())) {
                            responseMessage.set(message);
                            responseReceived.countDown();
                        }
                    }
                }, "ws://localhost:" + port + "/ws-test")
                .get(3, TimeUnit.SECONDS);

        try {
            session.sendMessage(new TextMessage(JsonUtil.toJsonString(
                    new WsMessage("echo-request", "hello-endpoint"))));

            assertThat(responseReceived.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(responseMessage.get().getPayload()).isEqualTo("hello-endpoint");
        } finally {
            session.close();
        }
    }

    /**
     * 冒烟测试使用的最小 Servlet 应用。
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ImportAutoConfiguration(WebSocketAutoConfiguration.class)
    static class TestApplication {

        /**
         * 提供业务自定义握手认证器，验证 starter 会正确退让默认实现。
         *
         * @return 固定测试主体的认证器
         */
        @Bean
        WsAuthenticator wsAuthenticator() {
            return request -> new WsPrincipal("smoke-user");
        }

        /**
         * 创建回显消息处理器。
         *
         * @param wsTemplate 消息发送模板
         * @return 回显消息处理器
         */
        @Bean
        WsMessageHandler echoWsMessageHandler(WsTemplate wsTemplate) {
            return new WsMessageHandler() {
                /**
                 * 将入站负载回送给当前会话。
                 *
                 * @param session 当前会话
                 * @param message 入站消息
                 */
                @Override
                public void handle(WsSession session, WsMessage message) {
                    wsTemplate.sendToSession(
                            session.getSessionId(),
                            new WsMessage("echo-response", message.getPayload()));
                }

                /**
                 * 获取处理的消息类型。
                 *
                 * @return 回显请求类型
                 */
                @Override
                public String getMessageType() {
                    return "echo-request";
                }
            };
        }
    }
}
