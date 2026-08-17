package io.github.leylaragg.letool.tool.util;

import io.github.leylaragg.letool.tool.id.IdGenerationException;

import java.lang.management.ManagementFactory;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.LongSupplier;

/**
 * ID 生成工具，提供 Snowflake、UUID 和 NanoId 三类便捷能力。
 *
 * <p>静态 Snowflake 入口优先读取 JVM 参数 {@code letool.id.worker-id} 和
 * {@code letool.id.datacenter-id}。未配置时会根据当前进程和网卡尽力推导节点号，
 * 但自动推导无法替代分布式节点分配；多实例生产环境必须为每个实例配置唯一组合，
 * 或直接创建显式节点号的 {@link Snowflake} 实例。</p>
 */
public final class IdUtil {

    /** NanoId 默认字符表。 */
    private static final char[] NANO_ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    /** NanoId 默认长度。 */
    private static final int NANO_DEFAULT_SIZE = 21;

    /** NanoId 允许的最大长度，避免错误参数造成不受控内存分配。 */
    private static final int NANO_MAX_SIZE = 1024;

    /** NanoId 使用的密码学安全随机源。 */
    private static final SecureRandom NANO_RANDOM = new SecureRandom();

    /** Snowflake Worker ID 的 JVM 参数名。 */
    private static final String WORKER_ID_PROPERTY = "letool.id.worker-id";

    /** Snowflake Datacenter ID 的 JVM 参数名。 */
    private static final String DATACENTER_ID_PROPERTY = "letool.id.datacenter-id";

    /**
     * 禁止创建工具类实例。
     */
    private IdUtil() {
    }

    /**
     * 生成默认 Snowflake 长整数 ID。
     *
     * @return 非负且在当前默认实例中单调递增的 ID
     * @throws IdGenerationException 当节点配置或系统时间不符合生成契约时抛出
     */
    public static long nextId() {
        return DefaultSnowflakeHolder.INSTANCE.nextId();
    }

    /**
     * 生成默认 Snowflake 字符串 ID。
     *
     * @return Snowflake 长整数的十进制字符串
     * @throws IdGenerationException 当节点配置或系统时间不符合生成契约时抛出
     */
    public static String nextIdStr() {
        return Long.toString(nextId());
    }

    /**
     * 生成不带连字符的 32 位 UUID。
     *
     * @return 32 位小写十六进制 UUID
     */
    public static String simpleUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成带连字符的标准 36 位 UUID。
     *
     * @return 标准 UUID 字符串
     */
    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成默认 21 位 NanoId。
     *
     * @return 使用数字和大小写字母组成的 NanoId
     */
    public static String nanoId() {
        return nanoId(NANO_DEFAULT_SIZE);
    }

    /**
     * 生成指定长度的 NanoId。
     *
     * @param size 字符数量，必须位于 1 到 1024 之间
     * @return 指定长度的 NanoId
     * @throws IdGenerationException 当长度不符合契约时抛出
     */
    public static String nanoId(int size) {
        if (size <= 0 || size > NANO_MAX_SIZE) {
            throw IdGenerationException.invalidArgument("size");
        }
        char[] result = new char[size];
        for (int index = 0; index < size; index++) {
            result[index] = NANO_ALPHABET[NANO_RANDOM.nextInt(NANO_ALPHABET.length)];
        }
        return new String(result);
    }

    /**
     * 延迟创建默认 Snowflake，避免错误节点配置影响 UUID 和 NanoId 入口。
     */
    private static final class DefaultSnowflakeHolder {

        /** 默认 Snowflake 单例。 */
        private static final Snowflake INSTANCE = new Snowflake();

        /**
         * 禁止创建延迟持有类实例。
         */
        private DefaultSnowflakeHolder() {
        }
    }

    /**
     * 线程安全的 64 位 Snowflake ID 生成器。
     *
     * <p>位布局为 1 位保留、41 位毫秒差值、5 位数据中心、5 位工作节点和
     * 12 位毫秒内序列。自定义纪元为北京时间 2024-01-01 00:00:00 对应的时间戳。</p>
     */
    public static final class Snowflake {

        /** 自定义纪元毫秒时间戳。 */
        private static final long EPOCH = 1_704_038_400_000L;

        /** 工作节点位数。 */
        private static final long WORKER_BITS = 5L;

        /** 数据中心位数。 */
        private static final long DATACENTER_BITS = 5L;

        /** 毫秒内序列位数。 */
        private static final long SEQUENCE_BITS = 12L;

        /** 最大工作节点编号。 */
        private static final long MAX_WORKER = (1L << WORKER_BITS) - 1;

        /** 最大数据中心编号。 */
        private static final long MAX_DATACENTER = (1L << DATACENTER_BITS) - 1;

        /** 最大毫秒内序列号。 */
        private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

        /** 最大时间差值。 */
        private static final long MAX_TIMESTAMP_DELTA = (1L << 41) - 1;

        /** 工作节点左移位数。 */
        private static final long WORKER_SHIFT = SEQUENCE_BITS;

        /** 数据中心左移位数。 */
        private static final long DATACENTER_SHIFT = SEQUENCE_BITS + WORKER_BITS;

        /** 时间差值左移位数。 */
        private static final long TIMESTAMP_SHIFT =
                SEQUENCE_BITS + WORKER_BITS + DATACENTER_BITS;

        /** 默认允许的时钟回拨毫秒数。 */
        private static final long DEFAULT_MAX_BACKWARD_MILLIS = 5L;

        /** 等待下一毫秒时每次挂起的纳秒数。 */
        private static final long WAIT_PARK_NANOS = 100_000L;

        /** 工作节点编号。 */
        private final long workerId;

        /** 数据中心编号。 */
        private final long datacenterId;

        /** 最大容忍时钟回拨毫秒数。 */
        private final long maxBackwardMillis;

        /** 可替换的毫秒时间源。 */
        private final LongSupplier timeSource;

        /** 当前毫秒内序列。 */
        private long sequence;

        /** 上一次参与生成的逻辑毫秒时间。 */
        private long lastTimestamp = -1L;

        /**
         * 使用 JVM 参数或尽力推导的节点号创建 Snowflake。
         *
         * @throws IdGenerationException 当 JVM 节点参数不完整或不合法时抛出
         */
        public Snowflake() {
            this(resolveDefaultNodeConfiguration());
        }

        /**
         * 使用显式节点号和默认 5 毫秒回拨容忍创建 Snowflake。
         *
         * @param workerId 工作节点编号，范围为 0 到 31
         * @param datacenterId 数据中心编号，范围为 0 到 31
         * @throws IdGenerationException 当节点编号越界时抛出
         */
        public Snowflake(long workerId, long datacenterId) {
            this(workerId, datacenterId, DEFAULT_MAX_BACKWARD_MILLIS, System::currentTimeMillis);
        }

        /**
         * 使用显式节点号和回拨容忍时间创建 Snowflake。
         *
         * @param workerId 工作节点编号，范围为 0 到 31
         * @param datacenterId 数据中心编号，范围为 0 到 31
         * @param maxBackwardDuration 最大容忍回拨时间，不得为负数
         * @throws IdGenerationException 当节点编号或回拨时间不合法时抛出
         */
        public Snowflake(
                long workerId,
                long datacenterId,
                Duration maxBackwardDuration) {
            this(
                    workerId,
                    datacenterId,
                    toBackwardMillis(maxBackwardDuration),
                    System::currentTimeMillis
            );
        }

        /**
         * 使用已解析节点配置创建默认 Snowflake。
         *
         * @param configuration 节点配置
         */
        private Snowflake(NodeConfiguration configuration) {
            this(
                    configuration.workerId(),
                    configuration.datacenterId(),
                    DEFAULT_MAX_BACKWARD_MILLIS,
                    System::currentTimeMillis
            );
        }

        /**
         * 使用可控时间源创建 Snowflake，供同包关键时间边界测试使用。
         *
         * @param workerId 工作节点编号
         * @param datacenterId 数据中心编号
         * @param maxBackwardMillis 最大容忍回拨毫秒数
         * @param timeSource 毫秒时间源
         */
        Snowflake(
                long workerId,
                long datacenterId,
                long maxBackwardMillis,
                LongSupplier timeSource) {
            validateNodeId(workerId, MAX_WORKER, "workerId");
            validateNodeId(datacenterId, MAX_DATACENTER, "datacenterId");
            if (maxBackwardMillis < 0) {
                throw IdGenerationException.invalidArgument("maxBackwardDuration");
            }
            if (timeSource == null) {
                throw IdGenerationException.invalidArgument("timeSource");
            }
            this.workerId = workerId;
            this.datacenterId = datacenterId;
            this.maxBackwardMillis = maxBackwardMillis;
            this.timeSource = timeSource;
        }

        /**
         * 获取工作节点编号。
         *
         * @return 0 到 31 之间的节点编号
         */
        public long getWorkerId() {
            return workerId;
        }

        /**
         * 获取数据中心编号。
         *
         * @return 0 到 31 之间的数据中心编号
         */
        public long getDatacenterId() {
            return datacenterId;
        }

        /**
         * 获取最大容忍时钟回拨毫秒数。
         *
         * @return 非负毫秒数
         */
        public long getMaxBackwardMillis() {
            return maxBackwardMillis;
        }

        /**
         * 生成下一个 Snowflake ID。
         *
         * <p>容忍范围内的时钟回拨使用上一次逻辑时间继续递增；超过范围则快速失败。</p>
         *
         * @return 当前生成器内唯一且单调递增的长整数 ID
         * @throws IdGenerationException 当时钟回拨、时间范围或线程中断不符合契约时抛出
         */
        public synchronized long nextId() {
            long timestamp = currentTimestamp();
            validateTimestamp(timestamp);

            if (timestamp < lastTimestamp) {
                long backwardMillis = lastTimestamp - timestamp;
                if (backwardMillis > maxBackwardMillis) {
                    throw IdGenerationException.clockRollback(
                            new IllegalStateException("Clock rollback exceeds tolerance")
                    );
                }
                timestamp = lastTimestamp;
            }

            if (timestamp == lastTimestamp) {
                sequence = (sequence + 1) & MAX_SEQUENCE;
                if (sequence == 0) {
                    timestamp = waitUntilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }

            lastTimestamp = timestamp;
            return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                    | (datacenterId << DATACENTER_SHIFT)
                    | (workerId << WORKER_SHIFT)
                    | sequence;
        }

        /**
         * 等待物理时间越过上一逻辑毫秒，避免序列回绕后产生重复 ID。
         *
         * @param previousTimestamp 上一次逻辑毫秒时间
         * @return 已越过上一毫秒且处于可用范围的时间
         */
        private long waitUntilNextMillis(long previousTimestamp) {
            long startNanos = System.nanoTime();
            long waitLimitMillis = maxBackwardMillis == Long.MAX_VALUE
                    ? Long.MAX_VALUE
                    : Math.max(1L, maxBackwardMillis + 1L);
            long waitLimitNanos = TimeUnit.MILLISECONDS.toNanos(waitLimitMillis);

            long timestamp = currentTimestamp();
            while (timestamp <= previousTimestamp) {
                if (Thread.currentThread().isInterrupted()) {
                    Thread.currentThread().interrupt();
                    throw IdGenerationException.generationInterrupted(
                            new InterruptedException("Interrupted while waiting for Snowflake clock")
                    );
                }
                if (System.nanoTime() - startNanos >= waitLimitNanos) {
                    throw IdGenerationException.clockRollback(
                            new IllegalStateException("Clock did not advance before wait deadline")
                    );
                }
                LockSupport.parkNanos(WAIT_PARK_NANOS);
                timestamp = currentTimestamp();
            }
            validateTimestamp(timestamp);
            return timestamp;
        }

        /**
         * 从时间源读取当前毫秒时间。
         *
         * @return 当前毫秒时间
         */
        private long currentTimestamp() {
            return timeSource.getAsLong();
        }

        /**
         * 校验当前时间可以放入 41 位时间部分。
         *
         * @param timestamp 当前毫秒时间
         */
        private static void validateTimestamp(long timestamp) {
            if (timestamp < EPOCH || timestamp - EPOCH > MAX_TIMESTAMP_DELTA) {
                throw IdGenerationException.timestampOutOfRange(
                        new IllegalStateException("Timestamp is outside the Snowflake epoch range")
                );
            }
        }

        /**
         * 校验节点编号范围。
         *
         * @param nodeId 节点编号
         * @param maximum 最大允许值
         * @param configurationName 安全的配置名称
         */
        private static void validateNodeId(
                long nodeId,
                long maximum,
                String configurationName) {
            if (nodeId < 0 || nodeId > maximum) {
                throw IdGenerationException.invalidNodeConfiguration(configurationName);
            }
        }

        /**
         * 将回拨容忍时间转换为毫秒。
         *
         * @param duration 回拨容忍时间
         * @return 非负毫秒数
         */
        private static long toBackwardMillis(Duration duration) {
            if (duration == null || duration.isNegative()) {
                throw IdGenerationException.invalidArgument("maxBackwardDuration");
            }
            try {
                return duration.toMillis();
            } catch (ArithmeticException exception) {
                throw IdGenerationException.invalidArgument("maxBackwardDuration");
            }
        }

        /**
         * 解析默认节点配置。
         *
         * @return 显式 JVM 配置或尽力推导的节点组合
         */
        private static NodeConfiguration resolveDefaultNodeConfiguration() {
            String workerProperty;
            String datacenterProperty;
            try {
                workerProperty = System.getProperty(WORKER_ID_PROPERTY);
                datacenterProperty = System.getProperty(DATACENTER_ID_PROPERTY);
            } catch (SecurityException exception) {
                // 受限运行环境无法读取 JVM 参数时，继续使用尽力推导的节点组合。
                return new NodeConfiguration(deriveWorkerId(), deriveDatacenterId());
            }
            boolean workerConfigured = workerProperty != null && !workerProperty.isBlank();
            boolean datacenterConfigured =
                    datacenterProperty != null && !datacenterProperty.isBlank();

            if (workerConfigured != datacenterConfigured) {
                throw IdGenerationException.invalidNodeConfiguration(
                        "worker-id/datacenter-id"
                );
            }
            if (workerConfigured) {
                return new NodeConfiguration(
                        parseNodeProperty(WORKER_ID_PROPERTY, workerProperty, MAX_WORKER),
                        parseNodeProperty(
                                DATACENTER_ID_PROPERTY,
                                datacenterProperty,
                                MAX_DATACENTER
                        )
                );
            }
            return new NodeConfiguration(deriveWorkerId(), deriveDatacenterId());
        }

        /**
         * 解析并校验 JVM 节点参数。
         *
         * @param propertyName JVM 参数名称
         * @param propertyValue JVM 参数文本
         * @param maximum 最大允许值
         * @return 校验通过的节点编号
         */
        private static long parseNodeProperty(
                String propertyName,
                String propertyValue,
                long maximum) {
            try {
                long value = Long.parseLong(propertyValue);
                validateNodeId(value, maximum, propertyName);
                return value;
            } catch (NumberFormatException exception) {
                throw IdGenerationException.invalidNodeConfiguration(propertyName, exception);
            }
        }

        /**
         * 根据当前 JVM 进程号尽力推导工作节点编号。
         *
         * @return 0 到 31 之间的工作节点编号
         */
        private static long deriveWorkerId() {
            try {
                return Math.floorMod(ProcessHandle.current().pid(), MAX_WORKER + 1);
            } catch (RuntimeException exception) {
                return Math.floorMod(Thread.currentThread().getId(), MAX_WORKER + 1);
            }
        }

        /**
         * 根据可用网卡地址尽力推导数据中心编号。
         *
         * @return 0 到 31 之间的数据中心编号
         */
        private static long deriveDatacenterId() {
            long hash = 17L;
            try {
                hash = ManagementFactory.getRuntimeMXBean().getName().hashCode();
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces != null && interfaces.hasMoreElements()) {
                    byte[] hardwareAddress = interfaces.nextElement().getHardwareAddress();
                    if (hardwareAddress == null) {
                        continue;
                    }
                    for (byte value : hardwareAddress) {
                        hash = 31 * hash + (value & 0xff);
                    }
                }
            } catch (Exception ignored) {
                // 受限运行环境无法读取网卡时，继续使用 JVM 运行标识进行尽力推导。
            }
            return Math.floorMod(hash, MAX_DATACENTER + 1);
        }
    }

    /**
     * Snowflake 节点编号组合。
     *
     * @param workerId 工作节点编号
     * @param datacenterId 数据中心编号
     */
    private record NodeConfiguration(long workerId, long datacenterId) {
    }
}
