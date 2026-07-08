package com.github.leyland.letool.ratelimiter.circuit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 熔断器（Circuit Breaker）的默认实现。
 *
 * <p>基于滑动窗口统计失败率，实现 CLOSED → OPEN → HALF_OPEN 三态流转的熔断保护机制。</p>
 *
 * <h3>状态流转逻辑</h3>
 * <ol>
 *   <li><b>CLOSED（关闭）</b>：统计窗口内的失败率。当失败率超过 {@code failureThreshold}
 *       时，切换到 OPEN 状态，记录切换时间</li>
 *   <li><b>OPEN（开启）</b>：直接拒绝所有请求。经过 {@code recoveryTimeout} 秒后，
 *       自动切换到 HALF_OPEN 状态</li>
 *   <li><b>HALF_OPEN（半开）</b>：允许最多 {@code halfOpenMaxRequests} 次试探请求。
 *       如果全部成功，切换到 CLOSED；任何一次失败则切回 OPEN</li>
 * </ol>
 *
 * <h3>线程安全</h3>
 * <p>使用 {@link AtomicReference} 管理状态，{@code synchronized} 保护统计数据的更新。
 * 状态检查和流转使用 CAS 操作，保证无锁的高并发读取性能。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * DefaultCircuitBreaker cb = new DefaultCircuitBreaker("payment-service", 0.5, 60, 60, 3);
 *
 * if (!cb.isAllowed()) {
 *     return fallback();
 * }
 * try {
 *     Result r = remoteCall();
 *     cb.recordSuccess();
 *     return r;
 * } catch (Exception e) {
 *     cb.recordFailure();
 *     throw e;
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see CircuitBreaker
 * @see CircuitBreakerState
 */
public class DefaultCircuitBreaker implements CircuitBreaker {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(DefaultCircuitBreaker.class);

    // ======================== 配置属性 ========================

    /** 熔断器实例名称（唯一标识） */
    private final String name;

    /** 失败率阈值（0~1），超过此值触发熔断 */
    private final double failureThreshold;

    /** 统计窗口大小（秒） */
    private final long windowSize;

    /** 熔断恢复超时（秒） */
    private final long recoveryTimeout;

    /** 半开状态下允许的最大试探请求数 */
    private final int halfOpenMaxRequests;

    // ======================== 状态管理 ========================

    /** 当前熔断器状态（CLOSED / OPEN / HALF_OPEN），使用 CAS 保证线程安全 */
    private final AtomicReference<CircuitBreakerState> state =
            new AtomicReference<>(CircuitBreakerState.CLOSED);

    // ======================== 统计数据 ========================

    /**
     * 成功时间戳队列（用于滑动窗口统计）。
     *
     * <p>存储最近 windowSize 秒内的成功请求的时间戳（毫秒）。</p>
     */
    private final Deque<Long> successTimestamps = new ArrayDeque<>();

    /**
     * 失败时间戳队列（用于滑动窗口统计）。
     *
     * <p>存储最近 windowSize 秒内的失败请求的时间戳（毫秒）。</p>
     */
    private final Deque<Long> failureTimestamps = new ArrayDeque<>();

    /** 熔断开启的时间戳（毫秒），用于计算恢复超时 */
    private final AtomicLong openTimestamp = new AtomicLong(0);

    /** 半开状态下已完成的试探请求数 */
    private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);

    /** 半开状态下已发送的试探请求数 */
    private final AtomicInteger halfOpenRequestCount = new AtomicInteger(0);

    // ======================== 构造方法 ========================

    /**
     * 构造熔断器实例。
     *
     * @param name                 熔断器名称（唯一标识）
     * @param failureThreshold     失败率阈值（0~1）
     * @param windowSize           统计窗口大小（秒）
     * @param recoveryTimeout      恢复超时（秒）
     * @param halfOpenMaxRequests  半开状态最大试探请求数
     */
    public DefaultCircuitBreaker(String name, double failureThreshold, long windowSize,
                                  long recoveryTimeout, int halfOpenMaxRequests) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.windowSize = windowSize;
        this.recoveryTimeout = recoveryTimeout;
        this.halfOpenMaxRequests = halfOpenMaxRequests;
    }

    // ======================== CircuitBreaker 接口实现 ========================

    /**
     * 判断当前请求是否允许通过。
     *
     * <p>根据当前状态执行不同策略：</p>
     * <ul>
     *   <li><b>CLOSED</b>：直接允许</li>
     *   <li><b>OPEN</b>：检查是否已到恢复时间，若是则转入 HALF_OPEN 并允许</li>
     *   <li><b>HALF_OPEN</b>：检查试探请求数是否已达上限，未达上限则允许</li>
     * </ul>
     *
     * @return {@code true} 允许通过，{@code false} 被熔断拒绝
     */
    @Override
    public boolean isAllowed() {
        CircuitBreakerState currentState = state.get();

        switch (currentState) {
            case CLOSED:
                return true;

            case OPEN:
                return tryTransitionToHalfOpen();

            case HALF_OPEN:
                return halfOpenRequestCount.incrementAndGet() <= halfOpenMaxRequests;

            default:
                return false;
        }
    }

    /**
     * 记录一次成功的请求。
     *
     * <p>在 HALF_OPEN 状态下：累计成功次数，达到半开阈值后恢复到 CLOSED。
     * 在 CLOSED 状态下：记录成功时间戳用于失败率计算。</p>
     */
    @Override
    public void recordSuccess() {
        CircuitBreakerState currentState = state.get();

        if (currentState == CircuitBreakerState.HALF_OPEN) {
            synchronized (this) {
                int successCount = halfOpenSuccessCount.incrementAndGet();
                log.debug("CircuitBreaker [{}] HALF_OPEN success: {}/{}",
                        name, successCount, halfOpenMaxRequests);

                if (successCount >= halfOpenMaxRequests) {
                    transitionTo(CircuitBreakerState.CLOSED);
                    log.info("CircuitBreaker [{}] recovered → CLOSED", name);
                }
            }
        } else if (currentState == CircuitBreakerState.CLOSED) {
            synchronized (this) {
                long now = System.currentTimeMillis();
                successTimestamps.addLast(now);
                trimTimestamps(successTimestamps, now);
                trimTimestamps(failureTimestamps, now);

                // 检查是否需要触发熔断
                checkAndTrip(now);
            }
        }
        // OPEN 状态下不记录任何结果
    }

    /**
     * 记录一次失败的请求。
     *
     * <p>在 HALF_OPEN 状态下：任何一次失败立即切回 OPEN。
     * 在 CLOSED 状态下：记录失败时间戳，检查是否达到熔断阈值。</p>
     */
    @Override
    public void recordFailure() {
        CircuitBreakerState currentState = state.get();

        if (currentState == CircuitBreakerState.HALF_OPEN) {
            synchronized (this) {
                transitionTo(CircuitBreakerState.OPEN);
                log.warn("CircuitBreaker [{}] HALF_OPEN trial failed → OPEN", name);
            }
        } else if (currentState == CircuitBreakerState.CLOSED) {
            synchronized (this) {
                long now = System.currentTimeMillis();
                failureTimestamps.addLast(now);
                trimTimestamps(successTimestamps, now);
                trimTimestamps(failureTimestamps, now);

                // 检查是否需要触发熔断
                checkAndTrip(now);
            }
        }
        // OPEN 状态下不记录任何结果
    }

    /**
     * 获取熔断器的当前状态。
     *
     * @return 当前状态
     */
    @Override
    public CircuitBreakerState getState() {
        return state.get();
    }

    /**
     * 强制重置熔断器到 CLOSED 状态，清除所有统计数据。
     */
    @Override
    public void reset() {
        synchronized (this) {
            state.set(CircuitBreakerState.CLOSED);
            successTimestamps.clear();
            failureTimestamps.clear();
            openTimestamp.set(0);
            halfOpenSuccessCount.set(0);
            halfOpenRequestCount.set(0);
            log.info("CircuitBreaker [{}] manually reset → CLOSED", name);
        }
    }

    // ======================== 私有方法 ========================

    /**
     * 尝试从 OPEN 状态切换到 HALF_OPEN。
     *
     * <p>检查是否已超过 recoveryTimeout，若是则通过 CAS 切换状态。</p>
     *
     * @return {@code true} 切换成功（允许请求），{@code false} 仍在熔断中
     */
    private boolean tryTransitionToHalfOpen() {
        long openedAt = openTimestamp.get();
        long now = System.currentTimeMillis();

        if (now - openedAt >= recoveryTimeout * 1000L) {
            // 使用 CAS 保证只有一个线程能切换成功
            if (state.compareAndSet(CircuitBreakerState.OPEN, CircuitBreakerState.HALF_OPEN)) {
                halfOpenSuccessCount.set(0);
                halfOpenRequestCount.set(0);
                log.info("CircuitBreaker [{}] OPEN → HALF_OPEN (recovery timeout reached)", name);
                return true;
            }
            // CAS 失败说明被其他线程抢先切换了，重新检查状态
            return state.get() == CircuitBreakerState.HALF_OPEN
                    && halfOpenRequestCount.incrementAndGet() <= halfOpenMaxRequests;
        }
        return false;
    }

    /**
     * 检查失败率是否超过阈值，超过则触发熔断。
     *
     * @param now 当前时间戳（毫秒）
     */
    private void checkAndTrip(long now) {
        int failures = failureTimestamps.size();
        int successes = successTimestamps.size();
        int total = failures + successes;

        if (total == 0) {
            return;
        }

        double failureRate = (double) failures / total;
        // 至少需要一次失败才触发熔断，避免 failureThreshold=0 时纯成功请求误触发
        if (failures > 0 && failureRate >= failureThreshold) {
            transitionTo(CircuitBreakerState.OPEN);
            log.warn("CircuitBreaker [{}] CLOSED → OPEN: failureRate={}/{} (threshold={}), "
                            + "successes={}, failures={}",
                    name, String.format("%.2f", failureRate), total, failureThreshold, successes, failures);
        }
    }

    /**
     * 清理过期的统计数据（超出窗口范围的记录）。
     *
     * @param timestamps 时间戳队列
     * @param now        当前时间戳（毫秒）
     */
    private void trimTimestamps(Deque<Long> timestamps, long now) {
        long cutoff = now - windowSize * 1000L;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.pollFirst();
        }
    }

    /**
     * 切换熔断器状态并执行必要的初始化。
     *
     * @param newState 目标状态
     */
    private void transitionTo(CircuitBreakerState newState) {
        state.set(newState);
        if (newState == CircuitBreakerState.OPEN) {
            openTimestamp.set(System.currentTimeMillis());
        } else if (newState == CircuitBreakerState.CLOSED) {
            successTimestamps.clear();
            failureTimestamps.clear();
            halfOpenSuccessCount.set(0);
            halfOpenRequestCount.set(0);
        }
    }

    // ======================== Getter ========================

    /**
     * 获取熔断器实例名称。
     *
     * @return 熔断器名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取当前失败率。
     *
     * <p>仅基于当前窗口内的数据计算，窗口外的数据已被清理。</p>
     *
     * @return 失败率（0~1），无数据时返回 0
     */
    public double getFailureRate() {
        synchronized (this) {
            int failures = failureTimestamps.size();
            int successes = successTimestamps.size();
            int total = failures + successes;
            return total == 0 ? 0.0 : (double) failures / total;
        }
    }
}
