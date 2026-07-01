package com.github.leyland.letool.mq.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Message MQ 消息模型测试")
class MessageTest {

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("无参构造应设置时间戳")
        void noArgConstructorShouldSetTimestamp() {
            Message msg = new Message();
            assertTrue(msg.getTimestamp() > 0);
        }

        @Test
        @DisplayName("(topic, body) 构造应初始化所有字段")
        void topicBodyConstructorShouldInitializeAllFields() {
            Message msg = new Message("order", "{\"id\":1}");

            assertNotNull(msg.getMessageId());
            assertEquals("order", msg.getTopic());
            assertEquals("*", msg.getTag());
            assertEquals("{\"id\":1}", msg.getBody());
            assertNotNull(msg.getHeaders());
            assertTrue(msg.getTimestamp() > 0);
        }

        @Test
        @DisplayName("(topic, body) 构造应生成唯一 messageId")
        void topicBodyConstructorShouldGenerateUniqueId() {
            Message m1 = new Message("t", "b");
            Message m2 = new Message("t", "b");
            assertNotEquals(m1.getMessageId(), m2.getMessageId());
        }

        @Test
        @DisplayName("(topic, tag, body) 构造应设置 tag")
        void topicTagBodyConstructorShouldSetTag() {
            Message msg = new Message("order", "create", "{}");

            assertEquals("order", msg.getTopic());
            assertEquals("create", msg.getTag());
            assertEquals("{}", msg.getBody());
        }
    }

    @Nested
    @DisplayName("of() 静态工厂测试")
    class OfFactoryTests {

        @Test
        @DisplayName("of(topic, payload) 应序列化对象为 JSON")
        void ofShouldSerializeObject() {
            Map<String, Object> map = new HashMap<>();
            map.put("id", 1);
            Message msg = Message.of("order", map);

            assertEquals("order", msg.getTopic());
            assertEquals("*", msg.getTag());
            assertTrue(msg.getBody().contains("\"id\":1"));
        }

        @Test
        @DisplayName("of(topic, tag, payload) 应设置 tag")
        void ofWithTagShouldSetTag() {
            Message msg = Message.of("order", "create", "data");

            assertEquals("order", msg.getTopic());
            assertEquals("create", msg.getTag());
        }

        @Test
        @DisplayName("of() 应生成 messageId")
        void ofShouldGenerateMessageId() {
            Message msg = Message.of("t", "payload");
            assertNotNull(msg.getMessageId());
        }
    }

    @Nested
    @DisplayName("builder() 测试")
    class BuilderTests {

        @Test
        @DisplayName("builder() 应返回 MessageBuilder 实例")
        void builderShouldReturnMessageBuilder() {
            assertNotNull(Message.builder());
            assertTrue(Message.builder() instanceof MessageBuilder);
        }
    }

    @Nested
    @DisplayName("Header 方法测试")
    class HeaderTests {

        @Test
        @DisplayName("addHeader 应添加键值对")
        void addHeaderShouldAddKeyValue() {
            Message msg = new Message("t", "{}");
            msg.addHeader("traceId", "abc");

            assertEquals("abc", msg.getHeader("traceId"));
        }

        @Test
        @DisplayName("addHeader 应返回自身")
        void addHeaderShouldReturnThis() {
            Message msg = new Message("t", "{}");
            assertSame(msg, msg.addHeader("k", "v"));
        }

        @Test
        @DisplayName("getHeader 不存在的键应返回 null")
        void getHeaderShouldReturnNullForMissingKey() {
            Message msg = new Message("t", "{}");
            assertNull(msg.getHeader("nonexistent"));
        }

        @Test
        @DisplayName("headers 为 null 时 addHeader 应创建新 Map")
        void addHeaderShouldCreateMapWhenNull() {
            Message msg = new Message();
            msg.setHeaders(null);
            msg.addHeader("k", "v");

            assertEquals("v", msg.getHeader("k"));
        }

        @Test
        @DisplayName("headers 为 null 时 getHeader 应返回 null")
        void getHeaderShouldReturnNullWhenHeadersIsNull() {
            Message msg = new Message();
            msg.setHeaders(null);
            assertNull(msg.getHeader("k"));
        }
    }

    @Nested
    @DisplayName("Getter/Setter 测试")
    class GetterSetterTests {

        @Test
        @DisplayName("setMessageId 应设置 ID")
        void setMessageIdShouldWork() {
            Message msg = new Message();
            msg.setMessageId("custom-id");
            assertEquals("custom-id", msg.getMessageId());
        }

        @Test
        @DisplayName("setTopic 应设置主题")
        void setTopicShouldWork() {
            Message msg = new Message();
            msg.setTopic("test-topic");
            assertEquals("test-topic", msg.getTopic());
        }

        @Test
        @DisplayName("setTag 应设置标签")
        void setTagShouldWork() {
            Message msg = new Message();
            msg.setTag("vip");
            assertEquals("vip", msg.getTag());
        }

        @Test
        @DisplayName("setBody 应设置消息体")
        void setBodyShouldWork() {
            Message msg = new Message();
            msg.setBody("hello");
            assertEquals("hello", msg.getBody());
        }

        @Test
        @DisplayName("setHeaders 应设置头部")
        void setHeadersShouldWork() {
            Message msg = new Message();
            Map<String, String> headers = new HashMap<>();
            headers.put("k", "v");
            msg.setHeaders(headers);
            assertEquals("v", msg.getHeader("k"));
        }

        @Test
        @DisplayName("setTimestamp 应设置时间戳")
        void setTimestampShouldWork() {
            Message msg = new Message();
            msg.setTimestamp(123456789L);
            assertEquals(123456789L, msg.getTimestamp());
        }
    }

    @Nested
    @DisplayName("toString() 测试")
    class ToStringTests {

        @Test
        @DisplayName("toString 应包含 messageId")
        void toStringShouldContainMessageId() {
            Message msg = new Message("order", "{}");
            assertTrue(msg.toString().contains(msg.getMessageId()));
        }

        @Test
        @DisplayName("toString 应包含 topic")
        void toStringShouldContainTopic() {
            Message msg = new Message("order", "{}");
            assertTrue(msg.toString().contains("order"));
        }

        @Test
        @DisplayName("toString 应包含 tag")
        void toStringShouldContainTag() {
            Message msg = new Message("order", "{}");
            assertTrue(msg.toString().contains("*"));
        }
    }
}
