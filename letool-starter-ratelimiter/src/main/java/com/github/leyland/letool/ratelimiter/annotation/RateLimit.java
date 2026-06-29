package com.github.leyland.letool.ratelimiter.annotation;

import java.lang.annotation.*;

/**
 * 声明式限流注解 —— 通过 AOP 自动为被标注的方法应用限流策略。
 *
 * <p>将此注解标注在需要限流的方法上，框架会自动在方法执行前进行许可检查。
 * 许可不足时将执行以下回退策略之一：</p>
 * <ol>
 *   <li>若指定了 {@link #fallbackMethod()}，调用同类的回退方法</li>
 *   <li>否则抛出 {@link com.github.leyland.letool.ratelimiter.exception.RateLimitException}</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 简单限流：每秒最多 10 次请求，key 为固定字符串
 * @RateLimit(key = "send-sms", permitsPerSecond = 10)
 * public void sendSms(String phone) { ... }
 *
 * // 动态 key：使用 SpEL 表达式按手机号限流
 * @RateLimit(key = "'sms:' + #phone", permits = 1, permitsPerSecond = 1)
 * public void sendSms(String phone) { ... }
 *
 * // 带降级方法
 * @RateLimit(key = "'order:' + #orderId", fallbackMethod = "rateLimitFallback")
 * public String createOrder(String orderId) { ... }
 * public String rateLimitFallback(String orderId) {
 *     return "系统繁忙，请稍后再试";
 * }
 * }</pre>
 *
 * <h3>注意事项</h3>
 * <ul>
 *   <li>{@code key} 支持 SpEL 表达式，可动态拼接方法参数</li>
 *   <li>该注解依赖 Spring AOP，仅对 Spring 管理的 Bean 的 public 方法生效</li>
 *   <li>底层委托给 {@link com.github.leyland.letool.ratelimiter.core.RateLimitTemplate}</li>
 *   <li>回退方法必须与标注方法具有相同的参数签名</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 * @see com.github.leyland.letool.ratelimiter.aspect.RateLimitAspect
 * @see com.github.leyland.letool.ratelimiter.core.RateLimitTemplate
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流的唯一标识 key。
     *
     * <p>支持 Spring Expression Language (SpEL) 表达式，可以通过方法参数动态生成 key。
     * 例如 {@code "'sms:' + #phone"} 会生成类似 {@code "sms:13800138000"} 的 key。</p>
     *
     * <p>如果不使用 SpEL 动态特性，可以填写固定字符串作为全局限流 key。</p>
     *
     * @return 限流的 key（支持 SpEL）
     */
    String key();

    /**
     * 每次请求消耗的许可数，默认 {@code 1}。
     *
     * <p>大多数场景下每次请求消耗 1 个许可。对于批量操作或需要差异化限流的场景，
     * 可设置为大于 1 的值。</p>
     *
     * @return 请求许可数
     */
    int permits() default 1;

    /**
     * 每秒允许的最大请求数（稳态 QPS），默认 {@code 10}。
     *
     * <p>此参数仅在令牌桶算法下使用，设置令牌的补充速率。
     * 同时影响 {@code capacity} 和 {@code refillRate}。</p>
     *
     * @return 每秒最大许可数
     */
    int permitsPerSecond() default 10;

    /**
     * 限流算法类型，默认 {@code "token-bucket"}。
     *
     * <p>可选值：</p>
     * <ul>
     *   <li>{@code "token-bucket"} —— 令牌桶算法</li>
     *   <li>{@code "sliding-window"} —— 滑动窗口算法</li>
     * </ul>
     *
     * @return 算法类型标识
     */
    String algorithm() default "token-bucket";

    /**
     * 限流被拒绝时的回退方法名。
     *
     * <p>指定的方法必须与标注方法在同一个类中，且具有相同的参数列表和兼容的返回类型。
     * 留空则被拒绝时直接抛出 {@link com.github.leyland.letool.ratelimiter.exception.RateLimitException}。</p>
     *
     * <p>示例：</p>
     * <pre>{@code
     * @RateLimit(key = "...", fallbackMethod = "handleRateLimited")
     * public ResultDto process(Long id) { ... }
     *
     * // 回退方法：参数签名必须一致
     * public ResultDto handleRateLimited(Long id) {
     *     return ResultDto.error("请求过于频繁");
     * }
     * }</pre>
     *
     * @return 回退方法名（同一类中），留空表示不启用降级
     */
    String fallbackMethod() default "";
}
