package com.github.leyland.letool.tool.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class IoUtilTest {

    @Test
    void copyReturnsActualBytesWithoutClosingCallerStreams() throws IOException {
        TrackingInputStream input = new TrackingInputStream("文件内容".getBytes(StandardCharsets.UTF_8));
        TrackingOutputStream output = new TrackingOutputStream();

        long copied = IoUtil.copy(input, output);

        assertEquals(output.size(), copied);
        assertEquals("文件内容", output.toString(StandardCharsets.UTF_8));
        assertFalse(input.closed);
        assertFalse(output.closed);
    }

    @Test
    void limitedReadAllowsBoundaryAndRejectsFirstExcessByte() throws IOException {
        assertArrayEquals(new byte[]{1, 2, 3},
                IoUtil.readBytes(new ByteArrayInputStream(new byte[]{1, 2, 3}), 3));

        assertThrows(IOException.class,
                () -> IoUtil.readBytes(new ByteArrayInputStream(new byte[]{1, 2, 3, 4}), 3));
    }

    @Test
    void limitedCopyDoesNotWriteTheFirstExcessByte() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThrows(IOException.class, () -> IoUtil.copy(
                new ByteArrayInputStream(new byte[]{1, 2, 3, 4}), output, 3));
        assertArrayEquals(new byte[]{1, 2, 3}, output.toByteArray());
    }

    @Test
    void limitedOperationsStopAfterConsumingTheFirstExcessByte() {
        ByteArrayInputStream copyInput = new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5});
        ByteArrayInputStream readInput = new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5});

        assertThrows(IOException.class,
                () -> IoUtil.copy(copyInput, new ByteArrayOutputStream(), 3));
        assertThrows(IOException.class, () -> IoUtil.readBytes(readInput, 3));

        assertEquals(1, copyInput.available());
        assertEquals(1, readInput.available());
    }

    @Test
    void readStringUsesRequestedCharset() throws IOException {
        byte[] content = "中文".getBytes(StandardCharsets.UTF_16LE);

        assertEquals("中文", IoUtil.readString(
                new ByteArrayInputStream(content), StandardCharsets.UTF_16LE, content.length));
    }

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

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
