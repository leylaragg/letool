package com.github.leyland.letool.tool.function;

import io.github.resilience4j.core.IntervalFunction;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * 描述同步重试行为的不可变策略。
 *
 * <p>最大尝试次数包含第一次调用。策略只控制重试判定和两次调用之间的等待，
 * 不会为单次任务创建线程或强制超时；调用方应在具体客户端上配置请求超时。</p>
 *
 * @param <T> 任务返回值类型
 */
public final class RetryPolicy<T> {

    /** 默认最大尝试次数，包含第一次调用。 */
    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    /** 默认固定等待时间。 */
    public static final Duration DEFAULT_DELAY = Duration.ofMillis(100);

    private final int maxAttempts;
    private final Predicate<Throwable> retryOnException;
    private final Predicate<T> retryOnResult;
    private final IntervalFunction intervalFunction;

    /** Resilience4j 支持的三种等待模式。 */
    private enum BackoffMode {
        /** 每次使用相同等待时间。 */
        FIXED,
        /** 等待时间按倍数递增并受最大值限制。 */
        EXPONENTIAL,
        /** 指数递增后增加随机抖动并受最大值限制。 */
        EXPONENTIAL_RANDOM
    }

    /**
     * 根据已校验构建器创建不可变策略。
     *
     * @param builder 已完成参数校验的构建器
     */
    private RetryPolicy(Builder<T> builder) {
        this.maxAttempts = builder.maxAttempts;
        this.retryOnException = builder.retryOnException;
        this.retryOnResult = builder.retryOnResult;
        this.intervalFunction = switch (builder.backoffMode) {
            case FIXED -> attempt -> builder.initialDelay.toMillis();
            case EXPONENTIAL -> IntervalFunction.ofExponentialBackoff(
                    builder.initialDelay,
                    builder.multiplier,
                    builder.maxDelay
            );
            case EXPONENTIAL_RANDOM -> IntervalFunction.ofExponentialRandomBackoff(
                    builder.initialDelay,
                    builder.multiplier,
                    builder.randomizationFactor,
                    builder.maxDelay
            );
        };
    }

    /**
     * 创建使用生产安全默认值的策略构建器。
     *
     * @param <T> 任务返回值类型
     * @return 可变策略构建器
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * 获取包含首次调用在内的最大尝试次数。
     *
     * @return 大于等于一的尝试次数
     */
    int maxAttempts() {
        return maxAttempts;
    }

    /**
     * 获取异常重试条件。
     *
     * @return 非空异常判断器
     */
    Predicate<Throwable> retryOnException() {
        return retryOnException;
    }

    /**
     * 获取结果重试条件。
     *
     * @return 非空结果判断器
     */
    Predicate<T> retryOnResult() {
        return retryOnResult;
    }

    /**
     * 获取由成熟重试引擎执行的等待函数。
     *
     * @return 非空等待函数
     */
    IntervalFunction intervalFunction() {
        return intervalFunction;
    }

    /**
     * 不可变重试策略构建器。
     *
     * <p>构建器本身可变且不保证线程安全，每次 {@link #build()} 都会创建独立策略。</p>
     *
     * @param <T> 任务返回值类型
     */
    public static final class Builder<T> {

        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private BackoffMode backoffMode = BackoffMode.FIXED;
        private Duration initialDelay = DEFAULT_DELAY;
        private Duration maxDelay = DEFAULT_DELAY;
        private double multiplier = 2.0;
        private double randomizationFactor = 0.5;
        private Predicate<Throwable> retryOnException = throwable -> true;
        private Predicate<T> retryOnResult = result -> false;

        /** 使用默认值创建构建器。 */
        private Builder() {
        }

        /**
         * 配置包含首次调用在内的最大尝试次数。
         *
         * @param maxAttempts 最大尝试次数，必须大于等于一
         * @return 当前构建器
         */
        public Builder<T> maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * 配置每次重试前的固定等待时间。
         *
         * @param delay 非负等待时间
         * @return 当前构建器
         */
        public Builder<T> fixedDelay(Duration delay) {
            this.backoffMode = BackoffMode.FIXED;
            this.initialDelay = delay;
            this.maxDelay = delay;
            return this;
        }

        /**
         * 配置带最大等待限制的指数退避。
         *
         * @param initialDelay 首次重试前的正等待时间
         * @param multiplier 每次递增倍数，必须为大于一的有限值
         * @param maxDelay 单次等待上限，不得小于初始等待
         * @return 当前构建器
         */
        public Builder<T> exponentialBackoff(
                Duration initialDelay,
                double multiplier,
                Duration maxDelay) {
            this.backoffMode = BackoffMode.EXPONENTIAL;
            this.initialDelay = initialDelay;
            this.multiplier = multiplier;
            this.maxDelay = maxDelay;
            return this;
        }

        /**
         * 配置带随机抖动和最大等待限制的指数退避。
         *
         * @param initialDelay 首次重试前的正等待时间
         * @param multiplier 每次递增倍数，必须为大于一的有限值
         * @param randomizationFactor 随机抖动比例，取值范围为零到一
         * @param maxDelay 单次等待上限，不得小于初始等待
         * @return 当前构建器
         */
        public Builder<T> exponentialRandomBackoff(
                Duration initialDelay,
                double multiplier,
                double randomizationFactor,
                Duration maxDelay) {
            this.backoffMode = BackoffMode.EXPONENTIAL_RANDOM;
            this.initialDelay = initialDelay;
            this.multiplier = multiplier;
            this.randomizationFactor = randomizationFactor;
            this.maxDelay = maxDelay;
            return this;
        }

        /**
         * 配置可以触发重试的异常条件。
         *
         * @param predicate 异常判断器，返回 {@code true} 时允许重试
         * @return 当前构建器
         */
        public Builder<T> retryOnException(Predicate<Throwable> predicate) {
            this.retryOnException = predicate;
            return this;
        }

        /**
         * 配置需要继续重试的任务结果条件。
         *
         * @param predicate 结果判断器，返回 {@code true} 时允许重试
         * @return 当前构建器
         */
        public Builder<T> retryOnResult(Predicate<T> predicate) {
            this.retryOnResult = predicate;
            return this;
        }

        /**
         * 创建不可变重试策略。
         *
         * @return 完成校验的不可变策略
         * @throws RetryOperationException 任一参数不符合策略契约时抛出
         */
        public RetryPolicy<T> build() {
            if (maxAttempts < 1) {
                throw RetryOperationException.invalidArgument("maxAttempts");
            }
            validateDelay(initialDelay, "initialDelay", backoffMode == BackoffMode.FIXED);
            if (backoffMode != BackoffMode.FIXED) {
                validateDelay(maxDelay, "maxDelay", false);
                if (!Double.isFinite(multiplier) || multiplier <= 1.0) {
                    throw RetryOperationException.invalidArgument("multiplier");
                }
                if (maxDelay.compareTo(initialDelay) < 0) {
                    throw RetryOperationException.invalidArgument("maxDelay");
                }
            }
            if (backoffMode == BackoffMode.EXPONENTIAL_RANDOM
                    && (!Double.isFinite(randomizationFactor)
                    || randomizationFactor < 0.0
                    || randomizationFactor > 1.0)) {
                throw RetryOperationException.invalidArgument("randomizationFactor");
            }
            if (retryOnException == null) {
                throw RetryOperationException.invalidArgument("retryOnException");
            }
            if (retryOnResult == null) {
                throw RetryOperationException.invalidArgument("retryOnResult");
            }
            return new RetryPolicy<>(this);
        }

        /**
         * 校验等待时间非空、非负且可以安全转换为毫秒。
         *
         * @param delay 待校验等待时间
         * @param parameterName 公开参数名称
         * @param allowZero 是否允许零等待
         */
        private static void validateDelay(
                Duration delay,
                String parameterName,
                boolean allowZero) {
            if (delay == null || delay.isNegative() || (!allowZero && delay.isZero())) {
                throw RetryOperationException.invalidArgument(parameterName);
            }
            try {
                long delayMillis = delay.toMillis();
                if (!allowZero && delayMillis < 1) {
                    throw RetryOperationException.invalidArgument(parameterName);
                }
            } catch (ArithmeticException exception) {
                throw RetryOperationException.invalidArgument(parameterName);
            }
        }
    }
}
