package io.github.leylaragg.letool.print.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 渲染输出缓冲区在分配内存前执行容量治理。
 *
 * @author leyland
 */
class BoundedRenderOutputTest {

    /** 恰好达到上限时仍能读取与内部状态隔离的内容副本。 */
    @Test
    void shouldAllowExactLimitAndReturnCopy() throws Exception {
        BoundedRenderOutput output = new BoundedRenderOutput(5);

        output.write(new byte[]{1, 2, 3});
        output.write(new byte[]{4, 5});
        byte[] content = output.toByteArray();
        content[0] = 9;

        assertThat(output.size()).isEqualTo(5);
        assertThat(output.toByteArray()).containsExactly(1, 2, 3, 4, 5);
    }

    /** 一次批量写入越界时不留下已经复制的部分内容。 */
    @Test
    void shouldRejectBatchBeforeWritingPastLimit() throws Exception {
        BoundedRenderOutput output = new BoundedRenderOutput(4);
        output.write(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> output.write(new byte[]{4, 5}))
                .isInstanceOf(BoundedRenderOutput.OutputLimitExceededException.class);
        assertThat(output.toByteArray()).containsExactly(1, 2, 3);
    }

    /** 最后一个分段只分配剩余容量，不因固定分段大小越过上限。 */
    @Test
    void shouldLimitAllocatedSegmentsToConfiguredCapacity() throws Exception {
        BoundedRenderOutput output = new BoundedRenderOutput(20_000);

        output.write(new byte[20_000]);

        assertThat(output.allocatedBytes()).isEqualTo(20_000);
    }
}
