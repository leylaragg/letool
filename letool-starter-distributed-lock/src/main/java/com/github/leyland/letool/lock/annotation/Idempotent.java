package com.github.leyland.letool.lock.annotation;

import java.lang.annotation.*;

/**
 * 声明式幂等性注解 —— 通过 AOP 自动防止重复请求。
 *
 * <p>将此注解标注在需要幂等性保证的方法上，框架会基于 Redis 缓存请求状态：
 * 首次请求正常执行业务逻辑并缓存结果标记；在 TTL 时间内，相同 key 的重复请求
 * 将直接返回 {@code null} 而不执行方法体。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Idempotent(key = "pay:#{#orderId}", ttl = 3600)
 * public PaymentResult pay(Long orderId) {
 *     // 支付逻辑：同一 orderId 在一小时内只会执行一次
 *     return doPay(orderId);
 * }
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>{@code key} 支持 SpEL 表达式，可动态拼接方法参数</li>
 *   <li>该注解依赖 Spring AOP，仅对 Spring 管理的 Bean 的 public 方法生效</li>
 *   <li>重复请求返回 {@code null}，调用方需自行判空处理</li>
 *   <li>底层通过 Redis SET NX EX 命令实现标记的原子存储</li>
 * </ul>
 *
 * @author leyland
 * @since 1.0.0
 * @see com.github.leyland.letool.lock.aspect.IdempotentAspect
 * @see com.github.leyland.letool.lock.idempotent.IdempotentService
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 幂等性校验的唯一标识 key。
     *
     * <p>支持 Spring Expression Language (SpEL) 表达式，可以通过方法参数动态生成 key。
     * 例如 {@code "pay:#{#orderId}"} 会生成类似 {@code "pay:12345"} 的 key。</p>
     *
     * @return 幂等校验的 key（支持 SpEL）
     */
    String key();

    /**
     * 幂等标记的存活时间（秒），默认 3600 秒（1 小时）。
     *
     * <p>在此时间内，相同 key 的请求将被视为重复请求并直接返回 {@code null}。
     * 超过此时间后，标记自动过期，允许再次执行。</p>
     *
     * @return TTL 秒数
     */
    long ttl() default 3600;
}
