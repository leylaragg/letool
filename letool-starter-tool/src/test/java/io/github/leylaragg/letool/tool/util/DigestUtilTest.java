package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.model.DigestCopyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DigestUtil} 的摘要结果、流边界和资源所有权测试。
 */
class DigestUtilTest {

    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @TempDir
    Path temporaryDirectory;

    @Test
    void sha256MatchesStandardVectorForAllInputs() throws IOException {
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);
        Path path = Files.write(temporaryDirectory.resolve("content.bin"), content);

        assertEquals(ABC_SHA256, DigestUtil.sha256("abc"));
        assertEquals(ABC_SHA256, DigestUtil.sha256(content));
        assertEquals(ABC_SHA256, DigestUtil.sha256(new ByteArrayInputStream(content)));
        assertEquals(ABC_SHA256, DigestUtil.sha256(path));
    }

    @Test
    void copyAndSha256ConsumesInputOnlyOnce() throws IOException {
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        DigestCopyResult result = DigestUtil.copyAndSha256(
                new ByteArrayInputStream(content), output);

        assertEquals(3, result.bytesCopied());
        assertEquals(ABC_SHA256, result.sha256());
        assertArrayEquals(content, output.toByteArray());
    }

    /**
     * 验证缓冲区边界前后及大内容均保持摘要、复制结果和调用方流所有权。
     *
     * @throws IOException 测试流读写失败时抛出
     */
    @Test
    void streamOperationsPreserveBoundariesAndCallerOwnedStreams() throws IOException {
        int[] sizes = {
                0, 1,
                8191, 8192, 8193,
                16 * 1024 - 1, 16 * 1024, 16 * 1024 + 1,
                1024 * 1024
        };
        for (int size : sizes) {
            byte[] content = content(size);
            String expectedDigest = DigestUtil.sha256(content);
            TrackingInputStream digestInput = new TrackingInputStream(content);
            TrackingInputStream copyInput = new TrackingInputStream(content);
            TrackingOutputStream output = new TrackingOutputStream();

            assertEquals(expectedDigest, DigestUtil.sha256(digestInput));
            DigestCopyResult result = DigestUtil.copyAndSha256(copyInput, output);

            assertEquals(size, result.bytesCopied());
            assertEquals(expectedDigest, result.sha256());
            assertArrayEquals(content, output.toByteArray());
            assertFalse(digestInput.closed);
            assertFalse(copyInput.closed);
            assertFalse(output.closed);
        }
    }

    /**
     * 验证读取失败按原异常传播，并且不关闭调用方输入流。
     */
    @Test
    void streamDigestPropagatesReadFailureWithoutClosingInput() {
        IOException failure = new IOException("read failed");
        FailingInputStream input = new FailingInputStream(failure);

        IOException thrown = assertThrows(IOException.class, () -> DigestUtil.sha256(input));

        assertSame(failure, thrown);
        assertFalse(input.closed);
    }

    /**
     * 验证写入失败按原异常传播，并且不关闭调用方输入输出流。
     */
    @Test
    void copyAndSha256PropagatesWriteFailureWithoutClosingStreams() {
        TrackingInputStream input = new TrackingInputStream(new byte[]{1});
        IOException failure = new IOException("write failed");
        FailingOutputStream output = new FailingOutputStream(failure);

        IOException thrown = assertThrows(
                IOException.class, () -> DigestUtil.copyAndSha256(input, output));

        assertSame(failure, thrown);
        assertFalse(input.closed);
        assertFalse(output.closed);
    }

    @Test
    void constantTimeComparisonAcceptsCaseAndRejectsInvalidOrDifferentDigest() {
        assertTrue(DigestUtil.matchesSha256(ABC_SHA256.toUpperCase(), ABC_SHA256));
        assertFalse(DigestUtil.matchesSha256(ABC_SHA256, "00".repeat(32)));
        assertFalse(DigestUtil.matchesSha256(ABC_SHA256, "invalid"));
        assertFalse(DigestUtil.matchesSha256(null, ABC_SHA256));
    }

    /**
     * 创建内容稳定的指定长度字节数组。
     *
     * @param size 字节数
     * @return 测试内容
     */
    private static byte[] content(int size) {
        byte[] content = new byte[size];
        for (int index = 0; index < size; index++) {
            content[index] = (byte) (index * 31 + 7);
        }
        return content;
    }

    /** 跟踪调用方输入流是否被关闭。 */
    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private TrackingInputStream(byte[] content) {
            super(content);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /** 跟踪调用方输出流是否被关闭。 */
    private static final class TrackingOutputStream extends ByteArrayOutputStream {

        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    /** 固定抛出指定读取异常的输入流。 */
    private static final class FailingInputStream extends InputStream {

        private final IOException failure;
        private boolean closed;

        private FailingInputStream(IOException failure) {
            this.failure = failure;
        }

        @Override
        public int read() throws IOException {
            throw failure;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            throw failure;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** 固定抛出指定写入异常的输出流。 */
    private static final class FailingOutputStream extends OutputStream {

        private final IOException failure;
        private boolean closed;

        private FailingOutputStream(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void write(int value) throws IOException {
            throw failure;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            throw failure;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
