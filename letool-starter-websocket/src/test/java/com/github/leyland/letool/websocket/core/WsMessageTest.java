package com.github.leyland.letool.websocket.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WsMessage 消息模型测试")
class WsMessageTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造应自动生成 messageId 和 timestamp")
        void defaultConstructorShouldAutoGenerateIdAndTimestamp() {
            WsMessage msg = new WsMessage();
            assertNotNull(msg.getMessageId());
            assertFalse(msg.getMessageId().isEmpty());
            assertTrue(msg.getTimestamp() > 0);
        }

        @Test
        @DisplayName("(type, payload) 构造应设置类型和负载")
        void typedConstructorShouldSetTypeAndPayload() {
            WsMessage msg = new WsMessage("chat", "hello");
            assertEquals("chat", msg.getType());
            assertEquals("hello", msg.getPayload());
            assertNotNull(msg.getMessageId());
        }

        @Test
        @DisplayName("两次构造生成的 messageId 应不同")
        void eachInstanceShouldHaveUniqueMessageId() {
            WsMessage m1 = new WsMessage();
            WsMessage m2 = new WsMessage();
            assertNotEquals(m1.getMessageId(), m2.getMessageId());
        }
    }

    @Nested
    @DisplayName("静态工厂方法测试")
    class StaticFactoryTests {

        @Test
        @DisplayName("of() 应序列化对象为 JSON 并设置类型")
        void ofShouldSerializePayload() {
            Map<String, Object> data = new HashMap<>();
            data.put("text", "hello");
            data.put("userId", "u1");

            WsMessage msg = WsMessage.of("chat", data);

            assertEquals("chat", msg.getType());
            assertNotNull(msg.getPayload());
            assertTrue(msg.getPayload().contains("hello"));
            assertTrue(msg.getPayload().contains("u1"));
        }

        @Test
        @DisplayName("text() 应创建 TYPE_TEXT 类型消息")
        void textShouldCreateTextMessage() {
            WsMessage msg = WsMessage.text("hello world");
            assertEquals(WsMessage.TYPE_TEXT, msg.getType());
            assertEquals("hello world", msg.getPayload());
        }

        @Test
        @DisplayName("pong() 应创建 TYPE_PONG 消息并携带时间戳")
        void pongShouldCreatePongMessage() {
            WsMessage msg = WsMessage.pong();
            assertEquals(WsMessage.TYPE_PONG, msg.getType());
            assertNotNull(msg.getPayload());
            assertDoesNotThrow(() -> Long.parseLong(msg.getPayload()));
        }

        @Test
        @DisplayName("error() 应创建 TYPE_ERROR 消息")
        void errorShouldCreateErrorMessage() {
            WsMessage msg = WsMessage.error("something went wrong");
            assertEquals(WsMessage.TYPE_ERROR, msg.getType());
            assertEquals("something went wrong", msg.getPayload());
        }
    }

    @Nested
    @DisplayName("Builder 模式测试")
    class BuilderTests {

        @Test
        @DisplayName("Builder 应正确设置所有字段")
        void builderShouldSetAllFields() {
            WsMessage msg = WsMessage.builder()
                    .type("notification")
                    .payload("{\"title\":\"alert\"}")
                    .senderId("user123")
                    .build();

            assertEquals("notification", msg.getType());
            assertEquals("{\"title\":\"alert\"}", msg.getPayload());
            assertEquals("user123", msg.getSenderId());
            assertNotNull(msg.getMessageId());
        }

        @Test
        @DisplayName("Builder payload(Object) 应序列化对象")
        void builderPayloadObjectShouldSerialize() {
            Map<String, String> data = new HashMap<>();
            data.put("key", "value");

            WsMessage msg = WsMessage.builder()
                    .type("event")
                    .payload((Object) data)
                    .build();

            assertEquals("event", msg.getType());
            assertTrue(msg.getPayload().contains("key"));
            assertTrue(msg.getPayload().contains("value"));
        }

        @Test
        @DisplayName("Builder 链式调用应返回同一 Builder 实例")
        void builderShouldSupportFluentChaining() {
            WsMessage.Builder builder = WsMessage.builder();
            assertSame(builder, builder.type("t"));
            assertSame(builder, builder.payload("p"));
            assertSame(builder, builder.senderId("s"));
        }
    }

    @Nested
    @DisplayName("payloadAs 反序列化测试")
    class PayloadAsTests {

        @Test
        @DisplayName("payloadAs 应将 JSON 负载反序列化为目标类型")
        void shouldDeserializePayload() {
            Map<String, Object> original = new HashMap<>();
            original.put("name", "test");
            original.put("value", 42);

            WsMessage msg = WsMessage.of("data", original);
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = msg.payloadAs(Map.class);

            assertEquals("test", parsed.get("name"));
            assertEquals(42, parsed.get("value"));
        }

        @Test
        @DisplayName("payloadAs 应支持 POJO 反序列化")
        void shouldDeserializeToPojo() {
            WsMessage msg = WsMessage.builder()
                    .type("user")
                    .payload("{\"name\":\"Alice\",\"value\":100}")
                    .build();

            TestPojo pojo = msg.payloadAs(TestPojo.class);
            assertEquals("Alice", pojo.getName());
            assertEquals(100, pojo.getValue());
        }
    }

    @Nested
    @DisplayName("消息类型常量测试")
    class TypeConstantTests {

        @Test
        @DisplayName("应定义正确的消息类型常量")
        void shouldDefineCorrectTypeConstants() {
            assertEquals("text", WsMessage.TYPE_TEXT);
            assertEquals("ping", WsMessage.TYPE_PING);
            assertEquals("pong", WsMessage.TYPE_PONG);
            assertEquals("notification", WsMessage.TYPE_NOTIFICATION);
            assertEquals("error", WsMessage.TYPE_ERROR);
        }
    }

    @Nested
    @DisplayName("Getter/Setter 测试")
    class GetterSetterTests {

        @Test
        @DisplayName("setMessageId/setTimestamp/setSenderId 应正确设置")
        void settersShouldWork() {
            WsMessage msg = new WsMessage();
            msg.setMessageId("custom-id");
            msg.setTimestamp(123456789L);
            msg.setSenderId("sender1");
            msg.setType("custom");

            assertEquals("custom-id", msg.getMessageId());
            assertEquals(123456789L, msg.getTimestamp());
            assertEquals("sender1", msg.getSenderId());
            assertEquals("custom", msg.getType());
        }
    }

    @Nested
    @DisplayName("toString 测试")
    class ToStringTests {

        @Test
        @DisplayName("toString 应包含关键字段")
        void toStringShouldContainKeyFields() {
            WsMessage msg = WsMessage.builder()
                    .type("chat")
                    .senderId("user1")
                    .build();
            String str = msg.toString();
            assertTrue(str.contains("chat"));
            assertTrue(str.contains("user1"));
            assertTrue(str.contains("WsMessage"));
        }
    }

    /** 测试用 POJO */
    public static class TestPojo {
        private String name;
        private int value;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
    }
}
