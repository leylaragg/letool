package com.github.leyland.letool.mq.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageBuilder 消息构建器测试")
class MessageBuilderTest {

    @Nested
    @DisplayName("基本构建测试")
    class BasicBuildTests {

        @Test
        @DisplayName("应构建有效的 Message")
        void shouldBuildValidMessage() {
            Message msg = new MessageBuilder()
                    .topic("order-topic")
                    .body("test data")
                    .build();

            assertEquals("order-topic", msg.getTopic());
            assertTrue(msg.getBody().contains("test data"));
        }

        @Test
        @DisplayName("默认 tag 应为 *")
        void defaultTagShouldBeStar() {
            Message msg = new MessageBuilder()
                    .topic("t")
                    .body("b")
                    .build();

            assertEquals("*", msg.getTag());
        }

        @Test
        @DisplayName("自定义 tag 应生效")
        void customTagShouldWork() {
            Message msg = new MessageBuilder()
                    .topic("t")
                    .tag("create")
                    .body("b")
                    .build();

            assertEquals("create", msg.getTag());
        }
    }

    @Nested
    @DisplayName("body() 序列化测试")
    class BodySerializationTests {

        @Test
        @DisplayName("body(Object) 应序列化为 JSON")
        void bodyObjectShouldSerializeToJson() {
            Message msg = new MessageBuilder()
                    .topic("order")
                    .body(new TestPayload("张三", 25))
                    .build();

            assertTrue(msg.getBody().contains("\"name\":\"张三\""));
            assertTrue(msg.getBody().contains("\"age\":25"));
        }

        @Test
        @DisplayName("bodyJson(String) 应直接使用传入的 JSON 字符串")
        void bodyJsonShouldUseRawString() {
            Message msg = new MessageBuilder()
                    .topic("t")
                    .bodyJson("{\"raw\":\"data\"}")
                    .build();

            assertEquals("{\"raw\":\"data\"}", msg.getBody());
        }
    }

    @Nested
    @DisplayName("Header 测试")
    class HeaderTests {

        @Test
        @DisplayName("header() 应添加头部键值对")
        void headerShouldAddKeyValue() {
            Message msg = new MessageBuilder()
                    .topic("t")
                    .body("b")
                    .header("traceId", "abc123")
                    .header("userId", "10086")
                    .build();

            assertEquals("abc123", msg.getHeader("traceId"));
            assertEquals("10086", msg.getHeader("userId"));
        }

        @Test
        @DisplayName("未设置 header 时应为空 Map")
        void noHeaderShouldBeEmptyMap() {
            Message msg = new MessageBuilder()
                    .topic("t")
                    .body("b")
                    .build();

            assertNotNull(msg.getHeaders());
            assertTrue(msg.getHeaders().isEmpty());
        }
    }

    @Nested
    @DisplayName("链式调用测试")
    class ChainingTests {

        @Test
        @DisplayName("所有方法应返回 this 以支持链式调用")
        void allMethodsShouldReturnThis() {
            MessageBuilder builder = new MessageBuilder();
            assertSame(builder, builder.topic("t"));
            assertSame(builder, builder.tag("vip"));
            assertSame(builder, builder.body("data"));
            assertSame(builder, builder.bodyJson("{}"));
            assertSame(builder, builder.header("k", "v"));
        }
    }

    @Nested
    @DisplayName("校验测试")
    class ValidationTests {

        @Test
        @DisplayName("topic 为 null 时应抛出异常")
        void nullTopicShouldThrow() {
            MessageBuilder builder = new MessageBuilder().body("data");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains("topic"));
        }

        @Test
        @DisplayName("topic 为空字符串时应抛出异常")
        void blankTopicShouldThrow() {
            MessageBuilder builder = new MessageBuilder().topic("   ").body("data");
            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        @DisplayName("body 为 null 时应抛出异常")
        void nullBodyShouldThrow() {
            MessageBuilder builder = new MessageBuilder().topic("t");
            assertThrows(IllegalArgumentException.class, builder::build);
        }

        @Test
        @DisplayName("body 为空字符串时应抛出异常")
        void blankBodyShouldThrow() {
            MessageBuilder builder = new MessageBuilder().topic("t").bodyJson("   ");
            assertThrows(IllegalArgumentException.class, builder::build);
        }
    }

    @Nested
    @DisplayName("Headers 不可变性测试")
    class HeadersImmutabilityTests {

        @Test
        @DisplayName("build 后修改 builder 内部 headers 不应影响 Message")
        void modifyingBuilderHeadersShouldNotAffectMessage() {
            MessageBuilder builder = new MessageBuilder()
                    .topic("t")
                    .body("b")
                    .header("key", "value");

            Message msg = builder.build();

            builder.header("hacked", "yes");

            assertNull(msg.getHeader("hacked"));
        }
    }

    @Nested
    @DisplayName("messageId 和时间戳测试")
    class MessageIdAndTimestampTests {

        @Test
        @DisplayName("build 未设置 messageId（使用无参构造，messageId 为 null）")
        void buildDoesNotSetMessageId() {
            Message msg = new MessageBuilder()
                    .topic("t")
                    .body("b")
                    .build();

            assertNull(msg.getMessageId());
        }

        @Test
        @DisplayName("build 应设置时间戳")
        void buildShouldSetTimestamp() {
            Message msg = new MessageBuilder()
                    .topic("t")
                    .body("b")
                    .build();

            assertTrue(msg.getTimestamp() > 0);
        }
    }

    static class TestPayload {
        private String name;
        private int age;

        TestPayload(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }
}
