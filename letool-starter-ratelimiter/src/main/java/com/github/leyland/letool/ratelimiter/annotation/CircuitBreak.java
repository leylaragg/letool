package com.github.leyland.letool.ratelimiter.annotation;

import java.lang.annotation.*;

/**
 * 声明式熔断注解 —— 通过 AOP 自动为被标注的方法应用熔断保护。
 *
 * <p>将此注解标注在需要熔断保护的方法上（通常是调用外部服务的方法），
 * 框架会自动跟踪成功/失败次数，当失败率达到阈值时自动熔断。熔断后的请求
 * 将被快速拒绝，避免雪崩效应。</p>
 *
 * <h3>状态流转</h3>
 * <pre>{@code
 *                   失败率超阈值
 *   CLOSED (关闭) ───────────────→ OPEN (开启)
 *       ↑                              │
 *       │     试探成功                  │ 等待 recoveryTimeout
 *       └── HALF_OPEN (半开) ←─────────┘
 *               │
 *               └── 试探失败 → OPEN
 * }</pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 基础用法：保护支付服务调用
 * @CircuitBreak(name = "payment-service")
 * public PaymentResult pay(PaymentRequest req) { ... }
 *
 * // 带降级方法
 * @CircuitBreak(name = "inventory", failureThreshold = 0.3, fallbackMethod = "inventoryFallback")
 * public InventoryResult checkInventory(String sku) { ... }
 * public InventoryResult inventoryFallback(String sku) {
 *     return InventoryResult.defaultValue();
 * }
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>{@code name} 是熔断器实例的唯一标识，同名注解共享同一个熔断器</li>
 *   <li>该注解依赖 Spring AOP，仅对 Spring 管理的 Bean 的 public 方法生效</li>
 *   <li>回退方法必须与标注方法具有相同的参数签名</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 * @see com.github.leyland.letool.ratelimiter.aspect.RateLimitAspect
 * @see com.github.leyland.letool.ratelimiter.circuit.DefaultCircuitBreaker
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreak {

    /**
     * 熔断器实例名称，作为熔断器的唯一标识。
     *
     * <p>同名的 {@code @CircuitBreak} 注解将共享同一个熔断器实例，
     * 它们的成功/失败计数将合并统计。通常按服务名或接口名命名。</p>
     *
     * @return 熔断器实例名称
     */
    String name();

    /**
     * 失败率阈值（0~1），超过此值触发熔断，默认 {@code 0.5}。
     *
     * <p>例如设置为 0.5 表示：在统计窗口内，如果有超过 50% 的请求失败，
     * 则触发熔断。设置为 1.0 表示所有请求都失败才触发。</p>
     *
     * @return 失败率阈值
     */
    double failureThreshold() default 0.5;

    /**
     * 统计窗口大小（秒），默认 {@code 60}。
     *
     * <p>在此时间窗口内统计成功/失败次数，计算失败率。
     * 窗口外的数据将被丢弃。</p>
     *
     * @return 统计窗口大小（秒）
     */
    int windowSize() default 60;

    /**
     * 熔断恢复超时（秒），默认 {@code 60}。
     *
     * <p>熔断器切换到 OPEN 状态后，经过此时间自动进入 HALF_OPEN 状态，
     * 允许少量请求通过进行试探。</p>
     *
     * @return 恢复超时时间（秒）
     */
    int recoveryTimeout() default 60;

    /**
     * 熔断被触发时的回退方法名。
     *
     * <p>指定的方法必须与标注方法在同一个类中，且具有相同的参数列表和兼容的返回类型。
     * 留空则熔断时直接抛出 {@link com.github.leyland.letool.ratelimiter.exception.RateLimitException}。</p>
     *
     * @return 回退方法名（同一类中），留空表示不启用降级
     */
    String fallbackMethod() default "";
}
