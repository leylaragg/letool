package com.github.leyland.letool.ratelimiter.circuit;

/**
 * 熔断器状态枚举。
 *
 * <p>定义熔断器的三种工作状态及其流转规则：</p>
 *
 * <h3>状态说明</h3>
 * <table>
 *   <tr><th>状态</th><th>含义</th><th>行为</th></tr>
 *   <tr><td>{@link #CLOSED}</td><td>电路闭合（正常）</td><td>请求正常通过，统计失败率</td></tr>
 *   <tr><td>{@link #OPEN}</td><td>电路断开（熔断）</td><td>拒绝所有请求，快速失败</td></tr>
 *   <tr><td>{@link #HALF_OPEN}</td><td>半开（试探）</td><td>允许少量请求，成功后恢复</td></tr>
 * </table>
 *
 * <h3>状态流转</h3>
 * <pre>{@code
 * CLOSED ──(失败率超阈值)──> OPEN ──(等待恢复超时)──> HALF_OPEN
 *   ^                                                   │
 *   └────────(试探成功)────────┘                        │
 *                                                        │
 *                        ┌───────────────────────────────┘
 *                        └──(试探失败)──> OPEN
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see CircuitBreaker
 * @see DefaultCircuitBreaker
 */
public enum CircuitBreakerState {

    /**
     * 关闭状态 —— 电路正常工作，请求正常通过。
     *
     * <p>在 CLOSED 状态下：</p>
     * <ul>
     *   <li>所有请求正常通过，不会被拦截</li>
     *   <li>持续统计成功/失败次数，计算失败率</li>
     *   <li>当失败率超过阈值时，自动切换到 {@link #OPEN} 状态</li>
     * </ul>
     */
    CLOSED,

    /**
     * 开启状态 —— 电路断开，快速失败。
     *
     * <p>在 OPEN 状态下：</p>
     * <ul>
     *   <li>拒绝所有请求，直接抛出异常</li>
     *   <li>等待 {@code recoveryTimeout} 秒后，自动切换到 {@link #HALF_OPEN}</li>
     *   <li>此状态下的请求不统计成功/失败</li>
     * </ul>
     */
    OPEN,

    /**
     * 半开状态 —— 试探恢复。
     *
     * <p>在 HALF_OPEN 状态下：</p>
     * <ul>
     *   <li>允许少量请求通过（最多 {@code halfOpenMaxRequests} 次）</li>
     *   <li>若试探请求全部成功，切换到 {@link #CLOSED}，恢复正常</li>
     *   <li>若任何一次试探失败，立即切换回 {@link #OPEN}，重新等待</li>
     * </ul>
     */
    HALF_OPEN
}
