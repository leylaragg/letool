package com.github.leyland.letool.net.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP 简易熔断器 —— 基于滑动窗口统计失败率，实现 CLOSED / OPEN / HALF_OPEN 三态切换.
 *
 * <h3>状态转换</h3>
 * <pre>
 * CLOSED (正常)  ----失败率超阈值----&gt; OPEN (熔断)
 * OPEN (熔断)    ----恢复超时后------&gt; HALF_OPEN (试探)
 * HALF_OPEN      ----试探成功--------&gt; CLOSED
 * HALF_OPEN      ----试探失败--------&gt; OPEN
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * HttpCircuitBreaker cb = new HttpCircuitBreaker(0.5, 60, 30);
 * if (cb.allowRequest()) {
 *     try {
 *         doHttpCall();
 *         cb.recordSuccess();
 *     } catch (Exception e) {
 *         cb.recordFailure();
 *     }
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class HttpCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(HttpCircuitBreaker.class);

    // ======================== 状态枚举 ========================

    /**
     * 熔断器三态枚举.
     */
    public enum State {
        /** 关闭（正常通行） */
        CLOSED,
        /** 开启（快速失败） */
        OPEN,
        /** 半开（试探性放行） */
        HALF_OPEN
    }

    // ======================== 字段 ========================

    /** 当前状态 */
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);

    /** 失败率阈值（0.0 ~ 1.0） */
    private final double errorThreshold;

    /** 统计窗口大小（秒） */
    private final int windowSeconds;

    /** 熔断恢复等待时间（秒） */
    private final int recoverySeconds;

    /** 最近进入 OPEN 状态的时间戳 */
    private final AtomicLong lastOpenTime = new AtomicLong(0);

    /** 滑动窗口：最近窗口内的请求成功/失败记录 */
    private final ConcurrentLinkedDeque<EventRecord> window = new ConcurrentLinkedDeque<>();

    // ======================== 构造器 ========================

    /**
     * 构造熔断器.
     *
     * @param errorThreshold  失败率阈值（0.0 ~ 1.0），超过则触发熔断
     * @param windowSeconds   统计窗口秒数
     * @param recoverySeconds 熔断恢复等待秒数
     */
    public HttpCircuitBreaker(double errorThreshold, int windowSeconds, int recoverySeconds) {
        this.errorThreshold = Math.max(0.0, Math.min(1.0, errorThreshold));
        this.windowSeconds = Math.max(1, windowSeconds);
        this.recoverySeconds = Math.max(1, recoverySeconds);
        log.info("CircuitBreaker created: threshold={}, window={}s, recovery={}s",
                errorThreshold, windowSeconds, recoverySeconds);
    }

    // ======================== 核心方法 ========================

    /**
     * 判断当前是否允许请求通过.
     *
     * @return {@code true} 如果允许发送请求
     */
    public boolean allowRequest() {
        State currentState = state.get();
        long now = System.currentTimeMillis();

        switch (currentState) {
            case CLOSED:
                return true;

            case OPEN:
                // 检查是否可以进入 HALF_OPEN
                long openTime = lastOpenTime.get();
                if (now - openTime >= recoverySeconds * 1000L) {
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        log.info("Circuit breaker HALF_OPEN, probing...");
                        return true;
                    }
                    // CAS 失败，重新判断
                    return state.get() == State.HALF_OPEN;
                }
                return false;

            case HALF_OPEN:
                return true;

            default:
                return false;
        }
    }

    /**
     * 记录一次成功请求.
     */
    public void recordSuccess() {
        window.offerLast(new EventRecord(true, System.currentTimeMillis()));
        pruneWindow();

        if (state.get() == State.HALF_OPEN) {
            // 半开状态试探成功 → 恢复为 CLOSED
            if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                window.clear();
                log.info("Circuit breaker CLOSED (recovered)");
            }
        }
    }

    /**
     * 记录一次失败请求.
     */
    public void recordFailure() {
        window.offerLast(new EventRecord(false, System.currentTimeMillis()));
        pruneWindow();

        State currentState = state.get();
        if (currentState == State.CLOSED || currentState == State.HALF_OPEN) {
            double errorRate = calcErrorRate();
            if (errorRate >= errorThreshold) {
                if (state.compareAndSet(currentState, State.OPEN)) {
                    lastOpenTime.set(System.currentTimeMillis());
                    log.warn("Circuit breaker OPEN, error rate={:.2%}, threshold={:.2%}",
                            errorRate, errorThreshold);
                }
            }
        }
        // HALF_OPEN 试探失败 → 直接回 OPEN
        if (currentState == State.HALF_OPEN) {
            state.set(State.OPEN);
            lastOpenTime.set(System.currentTimeMillis());
            log.warn("Circuit breaker OPEN (half-open probe failed)");
        }
    }

    /**
     * 获取当前熔断器状态.
     *
     * @return 当前状态
     */
    public State getState() {
        return state.get();
    }

    /**
     * 强制重置为 CLOSED 状态.
     */
    public void reset() {
        state.set(State.CLOSED);
        window.clear();
        lastOpenTime.set(0);
        log.info("Circuit breaker reset to CLOSED");
    }

    // ======================== 内部方法 ========================

    /**
     * 裁剪滑动窗口 —— 移除超出时间窗口的旧记录.
     */
    private void pruneWindow() {
        long cutoff = System.currentTimeMillis() - (long) windowSeconds * 1000;
        EventRecord record;
        while ((record = window.peekFirst()) != null && record.timestamp < cutoff) {
            window.pollFirst();
        }
    }

    /**
     * 计算当前窗口内的失败率.
     *
     * @return 失败率（0.0 ~ 1.0），窗口为空返回 0
     */
    private double calcErrorRate() {
        int total = window.size();
        if (total == 0) {
            return 0.0;
        }
        int failures = 0;
        for (EventRecord record : window) {
            if (!record.success) {
                failures++;
            }
        }
        return (double) failures / total;
    }

    // ======================== 内部类：EventRecord ========================

    /**
     * 滑动窗口事件记录.
     */
    private static class EventRecord {
        final boolean success;
        final long timestamp;

        EventRecord(boolean success, long timestamp) {
            this.success = success;
            this.timestamp = timestamp;
        }
    }
}
