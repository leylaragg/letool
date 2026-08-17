package io.github.leylaragg.letool.net.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内置 TCP 报文分帧器契约测试。
 */
class FrameCodecTest {

    /**
     * 验证四字节长度字段编码后能够抵抗分段到达，并剥离长度头。
     */
    @Test
    void shouldDecodeFragmentedLengthFieldFrame() {
        EmbeddedChannel outbound = channel(LengthFieldFrameCodec.int32(), 64);
        assertThat(outbound.writeOutbound(buffer("hello"))).isTrue();
        ByteBuf lengthHeader = outbound.readOutbound();
        ByteBuf payload = outbound.readOutbound();
        ByteBuf encoded = Unpooled.wrappedBuffer(lengthHeader, payload);

        EmbeddedChannel inbound = channel(LengthFieldFrameCodec.int32(), 64);
        ByteBuf firstPart = encoded.readRetainedSlice(3);
        ByteBuf secondPart = encoded.readRetainedSlice(encoded.readableBytes());

        assertThat(inbound.writeInbound(firstPart)).isFalse();
        assertThat(inbound.writeInbound(secondPart)).isTrue();

        ByteBuf decoded = inbound.readInbound();
        assertThat(decoded.toString(StandardCharsets.UTF_8)).isEqualTo("hello");

        decoded.release();
        encoded.release();
        outbound.finishAndReleaseAll();
        inbound.finishAndReleaseAll();
    }

    /**
     * 验证分隔符分帧器能够从一次读取中拆出多个完整报文。
     */
    @Test
    void shouldSplitCoalescedDelimiterFrames() {
        EmbeddedChannel channel = channel(DelimiterFrameCodec.lineFeed(), 64);

        assertThat(channel.writeInbound(buffer("first\nsecond\n"))).isTrue();

        ByteBuf first = channel.readInbound();
        ByteBuf second = channel.readInbound();
        assertThat(first.toString(StandardCharsets.UTF_8)).isEqualTo("first");
        assertThat(second.toString(StandardCharsets.UTF_8)).isEqualTo("second");

        first.release();
        second.release();
        channel.finishAndReleaseAll();
    }

    /**
     * 验证定长分帧器会拒绝长度不匹配的出站报文。
     */
    @Test
    void shouldRejectInvalidFixedLengthOutboundFrame() {
        EmbeddedChannel channel = channel(new FixedLengthFrameCodec(4), 64);

        assertThatThrownBy(() -> channel.writeOutbound(buffer("abc")))
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("固定长度");

        channel.finishAndReleaseAll();
    }

    /**
     * 验证长度字段分帧器会在缓存无限增长前拒绝过大报文。
     */
    @Test
    void shouldRejectOversizedLengthFieldFrame() {
        EmbeddedChannel channel = channel(LengthFieldFrameCodec.int32(), 4);
        ByteBuf oversized = Unpooled.buffer();
        oversized.writeInt(5);
        oversized.writeCharSequence("12345", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> channel.writeInbound(oversized))
                .isInstanceOf(TooLongFrameException.class);

        channel.finishAndReleaseAll();
    }

    /**
     * 根据分帧器创建测试通道。
     *
     * @param frameCodec 分帧器
     * @param maxFrameLength 最大报文长度
     * @return 已安装入站和出站处理器的嵌入式通道
     */
    private EmbeddedChannel channel(FrameCodec frameCodec, int maxFrameLength) {
        EmbeddedChannel channel = new EmbeddedChannel();
        frameCodec.configure(channel.pipeline(), maxFrameLength);
        return channel;
    }

    /**
     * 创建 UTF-8 测试缓冲区。
     *
     * @param value 测试文本
     * @return 包含测试文本的缓冲区
     */
    private ByteBuf buffer(String value) {
        return Unpooled.copiedBuffer(value, StandardCharsets.UTF_8);
    }
}
