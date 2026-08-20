package io.github.leylaragg.letool.print.api;

import io.github.leylaragg.letool.print.exception.PrintOutputException;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 流式打印输出的容量、摘要和调用方资源所有权测试。
 *
 * @author leyland
 */
class PrintOutputTest {

    /** 恰好写满允许容量时可以完成结果，框架只刷新而不关闭调用方流。 */
    @Test
    void shouldCompleteExactLimitWithoutClosingCallerStream() {
        TrackingOutputStream target = new TrackingOutputStream();
        PrintOutput output = new PrintOutput(target, 3);

        output.write(new byte[]{1, 2, 3});
        PrintResult result = output.complete(OutputFormat.PDF, Map.of("pageCount", "1"));
        output.close();

        assertThat(result.outputFormat()).isEqualTo(OutputFormat.PDF);
        assertThat(result.contentLength()).isEqualTo(3);
        assertThat(result.sha256())
                .isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        assertThat(result.metadata()).containsEntry("pageCount", "1");
        assertThat(output.completedWith(result)).isTrue();
        assertThat(target.flushed).isTrue();
        assertThat(target.closed).isFalse();
    }

    /** 批量内容越界时在调用底层流前失败，不向调用方留下本次写入的部分字节。 */
    @Test
    void shouldRejectOverflowBeforeWritingBatch() {
        ByteArrayOutputStream target = new ByteArrayOutputStream();
        PrintOutput output = new PrintOutput(target, 4);
        output.write(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> output.write(new byte[]{4, 5}))
                .isInstanceOf(PrintRenderingException.class)
                .hasMessageContaining("PRINT_007");
        assertThat(target.toByteArray()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> output.complete(OutputFormat.PDF, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("失败");
    }

    /** 调用方流写入失败时保留原始原因，但公开异常不回显底层消息。 */
    @Test
    void shouldKeepCallerFailureWithoutExposingMessage() {
        IOException cause = new IOException("secret-output-target");
        PrintOutput output = new PrintOutput(new FailingOutputStream(cause), 10);

        assertThatThrownBy(() -> output.write(1))
                .isInstanceOf(PrintOutputException.class)
                .hasMessageContaining("PRINT_013")
                .hasMessageNotContaining("secret-output-target")
                .hasCause(cause);
    }

    /** 完成阶段刷新失败也会冻结当前输出，并保留调用方异常。 */
    @Test
    void shouldKeepFlushFailureAndFreezeOutput() {
        IOException cause = new IOException("secret-flush-target");
        PrintOutput output = new PrintOutput(new FailingFlushOutputStream(cause), 10);
        output.write(1);

        assertThatThrownBy(() -> output.complete(OutputFormat.PDF, Map.of()))
                .isInstanceOf(PrintOutputException.class)
                .hasMessageNotContaining("secret-flush-target")
                .hasCause(cause);
        assertThatThrownBy(() -> output.write(2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("失败");
    }

    /** 完成结果会保护元数据副本，非法元数据也不会提前结束当前输出。 */
    @Test
    void shouldProtectAndValidateResultMetadata() {
        PrintOutput output = new PrintOutput(new ByteArrayOutputStream(), 10);
        output.write(1);
        assertThatThrownBy(() -> output.complete(OutputFormat.PDF, Map.of("", "invalid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("metadata");

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("pageCount", "1");
        PrintResult result = output.complete(OutputFormat.PDF, metadata);
        metadata.put("pageCount", "2");

        assertThat(result.metadata()).containsExactly(Map.entry("pageCount", "1"));
        assertThatThrownBy(() -> result.metadata().put("pageCount", "3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** 输出完成后不可继续写入或重复完成，避免摘要与调用方内容分叉。 */
    @Test
    void shouldFreezeCompletedOutput() {
        PrintOutput output = new PrintOutput(new ByteArrayOutputStream(), 10);
        output.write(1);
        output.complete(OutputFormat.PDF, Map.of());

        assertThatThrownBy(() -> output.write(2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("完成");
        assertThatThrownBy(() -> output.complete(OutputFormat.PDF, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("完成");
    }

    /** 记录框架是否错误关闭了调用方提供的输出流。 */
    private static final class TrackingOutputStream extends ByteArrayOutputStream {

        /** 是否执行过刷新。 */
        private boolean flushed;

        /** 是否执行过关闭。 */
        private boolean closed;

        /**
         * 记录完成阶段的刷新操作。
         *
         * @throws IOException 父类刷新失败时抛出
         */
        @Override
        public void flush() throws IOException {
            flushed = true;
            super.flush();
        }

        /**
         * 记录不应由打印框架触发的关闭操作。
         *
         * @throws IOException 父类关闭失败时抛出
         */
        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /** 使用固定 IO 异常模拟调用方输出目标故障。 */
    private static final class FailingOutputStream extends OutputStream {

        /** 每次写入时抛出的调用方异常。 */
        private final IOException failure;

        /**
         * @param failure 待抛出的固定异常
         */
        private FailingOutputStream(IOException failure) {
            this.failure = failure;
        }

        /**
         * @param value 待写入字节
         * @throws IOException 始终抛出构造时提供的故障
         */
        @Override
        public void write(int value) throws IOException {
            throw failure;
        }
    }

    /** 只在完成阶段拒绝刷新的调用方输出目标。 */
    private static final class FailingFlushOutputStream extends ByteArrayOutputStream {

        /** 刷新时抛出的调用方异常。 */
        private final IOException failure;

        /** @param failure 待抛出的固定异常 */
        private FailingFlushOutputStream(IOException failure) {
            this.failure = failure;
        }

        /**
         * @throws IOException 始终抛出构造时提供的故障
         */
        @Override
        public void flush() throws IOException {
            throw failure;
        }
    }
}
