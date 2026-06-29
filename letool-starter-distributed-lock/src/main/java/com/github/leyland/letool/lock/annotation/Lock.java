package com.github.leyland.letool.lock.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 声明式分布式锁注解 —— 通过 AOP 自动为被标注的方法加锁。
 *
 * <p>将此注解标注在需要加分布式锁的方法上，框架会自动在方法执行前获取锁，
 * 在方法执行后（无论成功或异常）释放锁。获取锁失败时抛出 {@link com.github.leyland.letool.lock.exception.LockException}。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Lock(key = "order:#{#orderId}", waitTime = 5, leaseTime = 60, timeUnit = TimeUnit.SECONDS)
 * public void processOrder(Long orderId) {
 *     // 业务逻辑：同一 orderId 的请求会串行执行
 * }
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>{@code key} 支持 SpEL 表达式，可动态拼接方法参数</li>
 *   <li>该注解依赖 Spring AOP，仅对 Spring 管理的 Bean 的 public 方法生效</li>
 *   <li>底层委托给 {@link com.github.leyland.letool.lock.core.LockTemplate}</li>
 * </ul>
 *
 * @author leyland
 * @since 1.0.0
 * @see com.github.leyland.letool.lock.aspect.LockAspect
 * @see com.github.leyland.letool.lock.core.LockTemplate
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {

    /**
     * 锁的唯一标识 key。
     *
     * <p>支持 Spring Expression Language (SpEL) 表达式，可以通过方法参数动态生成 key。
     * 例如 {@code "order:#{#orderId}"} 会生成类似 {@code "order:123"} 的 key。</p>
     *
     * @return 锁的 key（支持 SpEL）
     */
    String key();

    /**
     * 等待获取锁的最长时间（默认 3 秒）。
     *
     * <p>超过此时间仍未获取到锁则抛出 {@link com.github.leyland.letool.lock.exception.LockException}。</p>
     *
     * @return 等待时间数值
     */
    long waitTime() default 3;

    /**
     * 持锁的租约时间（默认 30 秒）。
     *
     * <p>超过此时间锁将自动释放，防止因线程崩溃导致的死锁。</p>
     *
     * @return 持锁时间数值
     */
    long leaseTime() default 30;

    /**
     * {@link #waitTime()} 和 {@link #leaseTime()} 的时间单位，默认秒。
     *
     * @return 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
