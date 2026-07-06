package com.github.leyland.letool.websocket.config;

import com.github.leyland.letool.tool.util.JsonUtil;
import com.github.leyland.letool.websocket.core.WsMessage;
import com.github.leyland.letool.websocket.core.WsSession;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Network-level smoke tests for the WebSocket starter endpoint.
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
     * Verifies the auto-configured endpoint accepts a real WebSocket handshake and sends the welcome frame.
     */
    @Test
    void shouldAcceptHandshakeOnConfiguredEndpoint() throws Exception {
        CountDownLatch welcomeReceived = new CountDownLatch(1);
        AtomicReference<WsMessage> welcomeMessage = new AtomicReference<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                        WsMessage wsMessage = JsonUtil.parseObject(message.getPayload(), WsMessage.class);
                        if (WsMessage.TYPE_NOTIFICATION.equals(wsMessage.getType())) {
                            welcomeMessage.set(wsMessage);
                            welcomeReceived.countDown();
                        }
                    }
                }, "ws://localhost:" + port + "/ws-test?token=smoke-token")
                .get(3, TimeUnit.SECONDS);

        try {
            assertThat(session.isOpen()).isTrue();
            assertThat(welcomeReceived.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(welcomeMessage.get().getPayload()).isEqualTo("连接成功");
        } finally {
            session.close();
        }
    }

    /**
     * Verifies endpoint messages are dispatched to registered {@link WsMessageHandler} beans.
     */
    @Test
    void shouldDispatchEndpointMessageToRegisteredHandler() throws Exception {
        CountDownLatch echoReceived = new CountDownLatch(1);
        AtomicReference<WsMessage> echoMessage = new AtomicReference<>();
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                        WsMessage wsMessage = JsonUtil.parseObject(message.getPayload(), WsMessage.class);
                        if ("echo-response".equals(wsMessage.getType())) {
                            echoMessage.set(wsMessage);
                            echoReceived.countDown();
                        }
                    }
                }, "ws://localhost:" + port + "/ws-test?token=smoke-token")
                .get(3, TimeUnit.SECONDS);

        try {
            WsMessage request = new WsMessage("echo-request", "hello-endpoint");
            session.sendMessage(new TextMessage(JsonUtil.toJsonString(request)));

            assertThat(echoReceived.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(echoMessage.get().getPayload()).isEqualTo("hello-endpoint");
        } finally {
            session.close();
        }
    }

    /**
     * Verifies the endpoint rejects handshakes that do not provide the configured auth token.
     */
    @Test
    void shouldRejectHandshakeWithoutToken() {
        StandardWebSocketClient client = new StandardWebSocketClient();

        assertThatThrownBy(() -> client.execute(new TextWebSocketHandler() {
                    }, "ws://localhost:" + port + "/ws-test")
                .get(3, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class);
    }

    /**
     * Minimal servlet application importing the WebSocket starter auto configuration.
     */
    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ImportAutoConfiguration(WebSocketAutoConfiguration.class)
    static class TestApplication {

        @Bean
        WsMessageHandler echoWsMessageHandler() {
            return new WsMessageHandler() {
                @Override
                public void handle(WsSession session, WsMessage message) {
                    session.sendMessage(WsMessage.builder()
                            .type("echo-response")
                            .payload(message.getPayload())
                            .build());
                }

                @Override
                public String getMessageType() {
                    return "echo-request";
                }
            };
        }
    }
}
