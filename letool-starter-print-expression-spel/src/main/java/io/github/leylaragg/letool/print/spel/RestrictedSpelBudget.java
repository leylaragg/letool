package io.github.leylaragg.letool.print.spel;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * 管理一次受限 SpEL 求值的累计步骤和合作式软截止时间。
 *
 * <p>预算不是抢占式线程中断器。AST 白名单保证单个操作有界，属性访问和求值边界再通过 checkpoint 尽早终止异常调用。
 * 每个实例由单次求值独占，不跨线程共享。</p>
 *
 * @author leyland
 */
final class RestrictedSpelBudget {

    /** 默认单次求值允许的累计检查点数量。 */
    private static final long DEFAULT_MAX_STEPS = 256;

    /** 默认单次求值允许的软截止时长，单位为纳秒。 */
    private static final long DEFAULT_MAX_NANOS = 250_000_000L;

    /** 单次求值允许的最大检查点数量。 */
    private final long maxSteps;

    /** 单次求值允许的最大纳秒时长。 */
    private final long maxNanos;

    /** 提供单调纳秒时间的函数。 */
    private final LongSupplier nanoTime;

    /** 单次求值开始时的单调时间。 */
    private final long startedAtNanos;

    /** 已成功通过的累计检查点数量。 */
    private long steps;

    /**
     * 创建生产环境使用的默认预算。
     *
     * @return 使用固定安全上限和系统单调时钟的新预算
     */
    static RestrictedSpelBudget standard() {
        return new RestrictedSpelBudget(
                DEFAULT_MAX_STEPS, DEFAULT_MAX_NANOS, System::nanoTime);
    }

    /**
     * 创建指定上限和单调时钟的预算。
     *
     * <p>该构造器保持包级可见，便于使用确定性时钟验证资源治理，不作为宿主放宽安全上限的公开配置入口。</p>
     *
     * @param maxSteps 最大检查点数量
     * @param maxNanos 最大纳秒时长
     * @param nanoTime 单调纳秒时间提供方
     * @throws IllegalArgumentException 任一容量上限不是正数时抛出
     * @throws NullPointerException 单调时间提供方为空时抛出
     */
    RestrictedSpelBudget(
            long maxSteps, long maxNanos, LongSupplier nanoTime) {
        if (maxSteps <= 0 || maxNanos <= 0) {
            throw new IllegalArgumentException("求值预算上限必须为正数");
        }
        this.maxSteps = maxSteps;
        this.maxNanos = maxNanos;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime 不能为空");
        this.startedAtNanos = nanoTime.getAsLong();
    }

    /**
     * 检查累计步骤和软截止时间，并在通过后记录一次步骤。
     *
     * <p>所有上限都在修改状态前判断，超限失败不会污染计数，也不会发生长整型加法溢出。</p>
     *
     * @throws IllegalArgumentException 步骤或时间超过安全上限时抛出
     */
    void checkpoint() {
        long elapsedNanos = nanoTime.getAsLong() - startedAtNanos;
        if (steps >= maxSteps || elapsedNanos > maxNanos) {
            throw new IllegalArgumentException("条件表达式求值超过安全限制");
        }
        steps++;
    }
}
