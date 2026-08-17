package io.github.leylaragg.letool.monitor.metrics;

import io.github.leylaragg.letool.monitor.exception.MonitorErrorCode;
import io.github.leylaragg.letool.monitor.exception.MonitorException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 基于 Micrometer 的业务指标便利门面。
 *
 * <p>该类型不维护私有指标存储，所有计数器和计时器都注册到应用提供的
 * {@link MeterRegistry}。应用可以通过 Spring Boot 支持的任意 Micrometer 注册表
 * 将指标导出到 Prometheus、OTLP 或其他监控后端。</p>
 *
 * <p>标签必须保持低基数，不应使用用户编号、订单编号、原始 URL 参数等无限增长值。</p>
 */
public final class MetricsCollector {

    /** 应用统一管理的 Micrometer 注册表。 */
    private final MeterRegistry meterRegistry;

    /**
     * 创建业务指标便利门面。
     *
     * @param meterRegistry 非空 Micrometer 注册表
     * @throws MonitorException 注册表为空时抛出
     */
    public MetricsCollector(MeterRegistry meterRegistry) {
        if (meterRegistry == null) {
            throw metricArgumentInvalid("meterRegistry 不能为空");
        }
        this.meterRegistry = meterRegistry;
    }

    /**
     * 获取或创建计数器。
     *
     * @param name 指标名称
     * @param tags 成对出现的低基数标签键和值
     * @return Micrometer 计数器
     * @throws MonitorException 指标参数或注册冲突不合法时抛出
     */
    public Counter counter(String name, String... tags) {
        String validatedName = validateName(name);
        String[] validatedTags = validateTags(tags);
        try {
            return meterRegistry.counter(validatedName, validatedTags);
        } catch (IllegalArgumentException exception) {
            throw MonitorException.causedBy(
                    MonitorErrorCode.METRIC_ARGUMENT_INVALID,
                    exception,
                    "计数器注册失败：" + validatedName);
        }
    }

    /**
     * 将指定计数器递增一。
     *
     * <p>该方法只表达递增副作用，不返回容易被误认为累计值的结果。
     * 如需读取 registry 当前视图，请显式调用 {@link #counterValue(String, String...)}。</p>
     *
     * @param name 指标名称
     * @param tags 成对出现的低基数标签键和值
     */
    public void increment(String name, String... tags) {
        Counter counter = counter(name, tags);
        counter.increment();
    }

    /**
     * 获取或创建计时器。
     *
     * @param name 指标名称
     * @param tags 成对出现的低基数标签键和值
     * @return Micrometer 计时器
     * @throws MonitorException 指标参数或注册冲突不合法时抛出
     */
    public Timer timer(String name, String... tags) {
        String validatedName = validateName(name);
        String[] validatedTags = validateTags(tags);
        try {
            return meterRegistry.timer(validatedName, validatedTags);
        } catch (IllegalArgumentException exception) {
            throw MonitorException.causedBy(
                    MonitorErrorCode.METRIC_ARGUMENT_INVALID,
                    exception,
                    "计时器注册失败：" + validatedName);
        }
    }

    /**
     * 记录一次已知耗时。
     *
     * @param name 指标名称
     * @param duration 非负耗时
     * @param tags 成对出现的低基数标签键和值
     * @throws MonitorException 耗时或其他指标参数不合法时抛出
     */
    public void recordTime(
            String name,
            Duration duration,
            String... tags) {
        if (duration == null) {
            throw metricArgumentInvalid("duration 不能为空");
        }
        if (duration.isNegative()) {
            throw metricArgumentInvalid("duration 不能为负数");
        }
        timer(name, tags).record(duration);
    }

    /**
     * 执行操作并记录实际耗时。
     *
     * @param name 指标名称
     * @param action 待计时操作
     * @param tags 成对出现的低基数标签键和值
     * @throws MonitorException 操作或指标参数不合法时抛出
     */
    public void record(
            String name,
            Runnable action,
            String... tags) {
        if (action == null) {
            throw metricArgumentInvalid("action 不能为空");
        }
        timer(name, tags).record(action);
    }

    /**
     * 执行有返回值的操作并记录实际耗时。
     *
     * @param name 指标名称
     * @param action 待计时操作
     * @param tags 成对出现的低基数标签键和值
     * @param <T> 操作返回值类型
     * @return 原操作返回值
     * @throws MonitorException 操作或指标参数不合法时抛出
     */
    public <T> T record(
            String name,
            Supplier<T> action,
            String... tags) {
        if (action == null) {
            throw metricArgumentInvalid("action 不能为空");
        }
        return timer(name, tags).record(action);
    }

    /**
     * 获取或创建完整身份对应的计数器，并查询当前值。
     *
     * <p>该方法遵循 Micrometer 的完整 meter 身份、公共标签和过滤器规则。
     * 指标不存在时会注册零值计数器，避免标签子集检索任意命中同名指标。</p>
     *
     * @param name 指标名称
     * @param tags 成对出现的低基数标签键和值
     * @return registry 当前可见值
     */
    public double counterValue(String name, String... tags) {
        return counter(name, tags).count();
    }

    /**
     * 获取或创建完整身份对应的计时器，并查询统计快照。
     *
     * <p>该方法遵循 Micrometer 的完整 meter 身份、公共标签和过滤器规则。
     * 指标不存在时会注册零值计时器，避免标签子集检索任意命中同名指标。</p>
     *
     * @param name 指标名称
     * @param tags 成对出现的低基数标签键和值
     * @return 不可变计时器快照
     */
    public TimerSnapshot timerSnapshot(String name, String... tags) {
        Timer timer = timer(name, tags);
        if (timer.count() == 0) {
            return TimerSnapshot.empty();
        }
        return new TimerSnapshot(
                timer.count(),
                nanosToDuration(timer.totalTime(TimeUnit.NANOSECONDS)),
                nanosToDuration(timer.mean(TimeUnit.NANOSECONDS)),
                nanosToDuration(timer.max(TimeUnit.NANOSECONDS)));
    }

    /**
     * 校验并规范化指标名称。
     *
     * @param name 原始指标名称
     * @return 去除首尾空白的指标名称
     */
    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw metricArgumentInvalid("指标名称不能为空");
        }
        return name.trim();
    }

    /**
     * 校验标签键值并创建防御性副本。
     *
     * @param tags 原始标签数组
     * @return 防御性复制后的标签数组
     */
    private static String[] validateTags(String[] tags) {
        if (tags == null) {
            throw metricArgumentInvalid("tags 不能为空");
        }
        if (tags.length % 2 != 0) {
            throw metricArgumentInvalid("标签必须以键值对形式出现");
        }
        String[] safeTags = tags.clone();
        for (int index = 0; index < safeTags.length; index++) {
            String value = safeTags[index];
            if (value == null || value.isBlank()) {
                String part = index % 2 == 0 ? "键" : "值";
                throw metricArgumentInvalid("标签" + part + "不能为空");
            }
            safeTags[index] = value.trim();
        }
        return safeTags;
    }

    /**
     * 将 Micrometer 的纳秒浮点值转换为 Duration。
     *
     * @param nanos 纳秒值
     * @return 非负 Duration
     */
    private static Duration nanosToDuration(double nanos) {
        if (!Double.isFinite(nanos) || nanos <= 0) {
            return Duration.ZERO;
        }
        if (nanos >= Long.MAX_VALUE) {
            return Duration.ofNanos(Long.MAX_VALUE);
        }
        return Duration.ofNanos(Math.round(nanos));
    }

    /**
     * 创建指标参数异常。
     *
     * @param reason 不合法原因
     * @return 结构化监控异常
     */
    private static MonitorException metricArgumentInvalid(String reason) {
        return MonitorException.of(
                MonitorErrorCode.METRIC_ARGUMENT_INVALID,
                reason);
    }

    /**
     * Micrometer 计时器的不可变统计快照。
     *
     * @param count registry 当前可见记录次数
     * @param totalTime registry 当前可见总耗时
     * @param mean 平均耗时
     * @param max 最大耗时
     */
    public record TimerSnapshot(
            long count,
            Duration totalTime,
            Duration mean,
            Duration max) {

        /** 复用的零值快照。 */
        private static final TimerSnapshot EMPTY = new TimerSnapshot(
                0,
                Duration.ZERO,
                Duration.ZERO,
                Duration.ZERO);

        /**
         * 获取零值快照。
         *
         * @return 不含任何记录的快照
         */
        public static TimerSnapshot empty() {
            return EMPTY;
        }
    }
}
