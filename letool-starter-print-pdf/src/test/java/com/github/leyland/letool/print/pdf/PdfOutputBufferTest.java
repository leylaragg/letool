package com.github.leyland.letool.print.pdf;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDF 输出缓冲区在分配内存前执行容量治理。
 *
 * @author leyland
 */
class PdfOutputBufferTest {

    /** 恰好达到上限仍可读取防御性副本。 */
    @Test
    void shouldAllowExactLimitAndReturnCopy() throws Exception {
        PdfOutputBuffer buffer = new PdfOutputBuffer(5);

        buffer.write(new byte[]{1, 2, 3});
        buffer.write(new byte[]{4, 5});
        byte[] content = buffer.toByteArray();
        content[0] = 9;

        assertThat(buffer.size()).isEqualTo(5);
        assertThat(buffer.toByteArray()).containsExactly(1, 2, 3, 4, 5);
    }

    /** 越界写入不会改变缓冲区中已经接收的内容。 */
    @Test
    void shouldRejectWriteBeforeGrowingPastLimit() throws Exception {
        PdfOutputBuffer buffer = new PdfOutputBuffer(4);
        buffer.write(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> buffer.write(new byte[]{4, 5}))
                .isInstanceOf(PdfOutputBuffer.OutputLimitExceededException.class);
        assertThat(buffer.toByteArray()).containsExactly(1, 2, 3);
    }

    /** 最后一个分段只分配剩余容量，不因固定分段大小越过治理边界。 */
    @Test
    void shouldLimitAllocatedSegmentsToConfiguredCapacity() throws Exception {
        PdfOutputBuffer buffer = new PdfOutputBuffer(20_000);

        buffer.write(new byte[20_000]);

        assertThat(buffer.allocatedBytes()).isEqualTo(20_000);
    }
}
