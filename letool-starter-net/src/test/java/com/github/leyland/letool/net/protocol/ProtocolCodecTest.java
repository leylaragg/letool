package com.github.leyland.letool.net.protocol;

import com.github.leyland.letool.net.exception.NetException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("协议编解码器测试")
class ProtocolCodecTest {

    @Nested
    @DisplayName("FixedLengthCodec - 定长协议编解码")
    class FixedLengthCodecTest {

        @Test
        @DisplayName("精确长度编解码")
        void exactLengthRoundtrip() {
            FixedLengthCodec codec = new FixedLengthCodec(10);
            byte[] encoded = codec.encode("hello");
            assertEquals(10, encoded.length);
            // bytes 5-9 are 0x00 padding
            assertEquals("hello", codec.decode(encoded));
        }

        @Test
        @DisplayName("短数据补零填充")
        void shortDataPadded() {
            FixedLengthCodec codec = new FixedLengthCodec(8);
            byte[] encoded = codec.encode("hi");
            assertEquals(8, encoded.length);
            assertEquals("hi", codec.decode(encoded));
        }

        @Test
        @DisplayName("长数据截断")
        void longDataTruncated() {
            FixedLengthCodec codec = new FixedLengthCodec(5);
            byte[] encoded = codec.encode("hello world");
            assertEquals(5, encoded.length);
            assertEquals("hello", codec.decode(encoded));
        }

        @Test
        @DisplayName("无效长度抛出 IllegalArgumentException")
        void invalidFrameLengthThrows() {
            assertThrows(IllegalArgumentException.class, () -> new FixedLengthCodec(0));
            assertThrows(IllegalArgumentException.class, () -> new FixedLengthCodec(-1));
        }

        @Test
        @DisplayName("null 消息抛出 NetException")
        void nullMessageThrows() {
            FixedLengthCodec codec = new FixedLengthCodec(10);
            assertThrows(NetException.class, () -> codec.encode(null));
        }

        @Test
        @DisplayName("null 字节抛出 NetException")
        void nullBytesThrows() {
            FixedLengthCodec codec = new FixedLengthCodec(10);
            assertThrows(NetException.class, () -> codec.decode(null));
        }

        @Test
        @DisplayName("协议名称包含 FixedLength")
        void protocolName() {
            FixedLengthCodec codec = new FixedLengthCodec(10);
            assertTrue(codec.getProtocolName().contains("FixedLength"));
        }
    }

    @Nested
    @DisplayName("DelimiterCodec - 分隔符协议编解码")
    class DelimiterCodecTest {

        @Test
        @DisplayName("使用 \\r\\n 分隔符编解码")
        void crlfDelimiterRoundtrip() {
            DelimiterCodec codec = new DelimiterCodec("\r\n", 1024, null);
            byte[] encoded = codec.encode("hello");
            // encode appends delimiter
            assertTrue(encoded.length >= 7); // "hello\r\n" = 7 bytes

            String decoded = (String) codec.decode("hello\r\nworld\r\n".getBytes());
            assertEquals("hello", decoded);
        }

        @Test
        @DisplayName("默认最大帧长")
        void defaultMaxFrameLength() {
            DelimiterCodec codec = new DelimiterCodec("\n");
            byte[] encoded = codec.encode("test");
            assertEquals("test\n", new String(encoded));
        }

        @Test
        @DisplayName("未找到分隔符抛出 NetException")
        void noDelimiterThrows() {
            DelimiterCodec codec = new DelimiterCodec("|", 1024, null);
            assertThrows(NetException.class, () -> codec.decode("no_delimiter_here".getBytes()));
        }

        @Test
        @DisplayName("null 消息抛出 NetException")
        void nullMessageThrows() {
            DelimiterCodec codec = new DelimiterCodec("\r\n");
            assertThrows(NetException.class, () -> codec.encode(null));
        }

        @Test
        @DisplayName("协议名称包含 Delimiter")
        void protocolName() {
            DelimiterCodec codec = new DelimiterCodec("\r\n");
            assertTrue(codec.getProtocolName().contains("Delimiter"));
        }
    }

    @Nested
    @DisplayName("LengthFieldCodec - 变长协议编解码")
    class LengthFieldCodecTest {

        @Test
        @DisplayName("默认 4 字节长度前缀编解码")
        void defaultLengthFieldRoundtrip() {
            LengthFieldCodec codec = new LengthFieldCodec();
            byte[] encoded = codec.encode("hello world");
            String decoded = (String) codec.decode(encoded);
            assertEquals("hello world", decoded);
        }

        @Test
        @DisplayName("2 字节长度字段")
        void twoByteLengthField() {
            LengthFieldCodec codec = new LengthFieldCodec(0, 2);
            byte[] encoded = codec.encode("test");
            String decoded = (String) codec.decode(encoded);
            assertEquals("test", decoded);
        }

        @Test
        @DisplayName("空字符串编解码")
        void emptyStringRoundtrip() {
            LengthFieldCodec codec = new LengthFieldCodec();
            byte[] encoded = codec.encode("");
            String decoded = (String) codec.decode(encoded);
            assertEquals("", decoded);
        }

        @Test
        @DisplayName("数据不足时抛出 NetException")
        void incompleteFrameThrows() {
            LengthFieldCodec codec = new LengthFieldCodec();
            byte[] incomplete = {0x00, 0x00, 0x00, 0x10, 0x01}; // 声称长度 16 只有 1 字节
            assertThrows(NetException.class, () -> codec.decode(incomplete));
        }

        @Test
        @DisplayName("超过最大帧长抛出 NetException")
        void exceedsMaxFrameLengthThrows() {
            LengthFieldCodec codec = new LengthFieldCodec(0, 4, 0, 100);
            byte[] frame = codec.encode(new String(new char[200]));
            assertThrows(NetException.class, () -> codec.decode(frame));
        }

        @Test
        @DisplayName("null 消息抛出 NetException")
        void nullMessageThrows() {
            LengthFieldCodec codec = new LengthFieldCodec();
            assertThrows(NetException.class, () -> codec.encode(null));
        }

        @Test
        @DisplayName("协议名称包含 LengthField")
        void protocolName() {
            LengthFieldCodec codec = new LengthFieldCodec();
            assertTrue(codec.getProtocolName().contains("LengthField"));
        }
    }

    @Nested
    @DisplayName("JsonProtocolCodec - JSON 协议编解码")
    class JsonProtocolCodecTest {

        static class TestMessage {
            public String name;
            public int value;

            TestMessage() {}

            TestMessage(String name, int value) {
                this.name = name;
                this.value = value;
            }
        }

        @Test
        @DisplayName("JSON 编解码")
        void jsonRoundtrip() {
            JsonProtocolCodec<TestMessage> codec = new JsonProtocolCodec<>(TestMessage.class);
            TestMessage msg = new TestMessage("test", 42);
            byte[] encoded = codec.encode(msg);
            TestMessage decoded = codec.decode(encoded);
            assertEquals("test", decoded.name);
            assertEquals(42, decoded.value);
        }

        @Test
        @DisplayName("null 输入抛出 NetException")
        void nullInputThrows() {
            JsonProtocolCodec<TestMessage> codec = new JsonProtocolCodec<>(TestMessage.class);
            assertThrows(NetException.class, () -> codec.encode(null));
        }

        @Test
        @DisplayName("null 构造参数抛出 IllegalArgumentException")
        void nullConstructorArgThrows() {
            assertThrows(IllegalArgumentException.class, () -> new JsonProtocolCodec<>(null));
        }

        @Test
        @DisplayName("返回消息类型")
        void messageType() {
            JsonProtocolCodec<TestMessage> codec = new JsonProtocolCodec<>(TestMessage.class);
            assertEquals(TestMessage.class, codec.getMessageType());
        }
    }
}
