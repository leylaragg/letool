package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.id.IdErrorCode;
import io.github.leylaragg.letool.tool.id.IdGenerationException;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ID 工具和 Snowflake 关键契约测试。
 */
class IdUtilTest {

    /** Snowflake 自定义纪元之后的一段稳定测试时间。 */
    private static final long TEST_TIME = 1_704_038_401_000L;

    /**
     * 验证静态便捷入口可以生成格式正确且不重复的常用 ID。
     */
    @Test
    void shouldGenerateConvenienceIdentifiers() {
        long first = IdUtil.nextId();
        long second = IdUtil.nextId();
        String uuid = IdUtil.uuid();
        String simpleUuid = IdUtil.simpleUUID();
        Set<String> nanoIds = new HashSet<>();
        for (int index = 0; index < 64; index++) {
            nanoIds.add(IdUtil.nanoId());
        }

        assertTrue(second > first);
        assertEquals(36, uuid.length());
        assertEquals(32, simpleUuid.length());
        assertFalse(simpleUuid.contains("-"));
        assertEquals(64, nanoIds.size());
    }

    /**
     * 验证 NanoId 拒绝无法形成有效标识的长度。
     */
    @Test
    void shouldRejectInvalidNanoIdLength() {
        IdGenerationException exception = assertThrows(
                IdGenerationException.class,
                () -> IdUtil.nanoId(0)
        );

        assertEquals(IdErrorCode.INVALID_ARGUMENT.getCode(), exception.getCode());
    }

    /**
     * 验证容忍范围内的时钟回拨使用逻辑时间并保持 ID 递增。
     */
    @Test
    void shouldUseLogicalTimeForTolerableClockRollback() {
        IdUtil.Snowflake snowflake = new IdUtil.Snowflake(
                1,
                2,
                5,
                new SequenceTimeSource(TEST_TIME, TEST_TIME - 2)
        );

        long first = snowflake.nextId();
        long second = snowflake.nextId();

        assertTrue(second > first);
    }

    /**
     * 验证超过容忍范围的时钟回拨稳定失败。
     */
    @Test
    void shouldRejectExcessiveClockRollback() {
        IdUtil.Snowflake snowflake = new IdUtil.Snowflake(
                1,
                2,
                5,
                new SequenceTimeSource(TEST_TIME, TEST_TIME - 6)
        );

        snowflake.nextId();
        IdGenerationException exception = assertThrows(
                IdGenerationException.class,
                snowflake::nextId
        );

        assertEquals(IdErrorCode.CLOCK_ROLLBACK.getCode(), exception.getCode());
    }

    /**
     * 验证 Snowflake 节点编号越界时使用稳定节点配置异常。
     */
    @Test
    void shouldRejectOutOfRangeNodeIdentifiers() {
        IdGenerationException worker = assertThrows(
                IdGenerationException.class,
                () -> new IdUtil.Snowflake(32, 0)
        );
        IdGenerationException datacenter = assertThrows(
                IdGenerationException.class,
                () -> new IdUtil.Snowflake(0, -1)
        );

        assertEquals(IdErrorCode.NODE_CONFIGURATION_FAILED.getCode(), worker.getCode());
        assertEquals(IdErrorCode.NODE_CONFIGURATION_FAILED.getCode(), datacenter.getCode());
    }

    /**
     * 验证默认构造器拒绝只配置一半的 JVM 节点参数。
     */
    @Test
    void shouldRequireCompleteJvmNodeConfiguration() {
        String workerProperty = "letool.id.worker-id";
        String datacenterProperty = "letool.id.datacenter-id";
        String previousWorker = System.getProperty(workerProperty);
        String previousDatacenter = System.getProperty(datacenterProperty);
        try {
            System.setProperty(workerProperty, "3");
            System.clearProperty(datacenterProperty);

            IdGenerationException exception = assertThrows(
                    IdGenerationException.class,
                    IdUtil.Snowflake::new
            );

            assertEquals(
                    IdErrorCode.NODE_CONFIGURATION_FAILED.getCode(),
                    exception.getCode()
            );
        } finally {
            restoreSystemProperty(workerProperty, previousWorker);
            restoreSystemProperty(datacenterProperty, previousDatacenter);
        }
    }

    /**
     * 验证自定义纪元之前的时间不会生成负数或不可解析的 ID。
     */
    @Test
    void shouldRejectTimestampBeforeSnowflakeEpoch() {
        IdUtil.Snowflake snowflake = new IdUtil.Snowflake(
                1,
                2,
                5,
                new SequenceTimeSource(1_704_038_399_999L)
        );

        IdGenerationException exception = assertThrows(
                IdGenerationException.class,
                snowflake::nextId
        );

        assertEquals(IdErrorCode.TIMESTAMP_OUT_OF_RANGE.getCode(), exception.getCode());
    }

    /**
     * 将 JVM 参数恢复为测试前状态。
     *
     * @param propertyName JVM 参数名称
     * @param previousValue 测试前参数值，允许为 {@code null}
     */
    private static void restoreSystemProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }

    /**
     * 按调用顺序返回预设毫秒时间的测试时间源。
     */
    private static final class SequenceTimeSource implements LongSupplier {

        /** 按调用顺序提供的毫秒时间。 */
        private final long[] timestamps;

        /** 当前读取位置。 */
        private int index;

        /**
         * 创建顺序时间源。
         *
         * @param timestamps 非空毫秒时间序列
         */
        private SequenceTimeSource(long... timestamps) {
            this.timestamps = timestamps;
        }

        /**
         * 获取当前时间；序列耗尽后持续返回最后一个时间。
         *
         * @return 当前毫秒时间
         */
        @Override
        public long getAsLong() {
            int current = Math.min(index, timestamps.length - 1);
            index++;
            return timestamps[current];
        }
    }
}
