package io.github.leylaragg.letool.ratelimiter.annotation;

import io.github.leylaragg.letool.ratelimiter.aspect.RateLimitAspect;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为 Spring Bean 公共方法声明命名限流策略。
 *
 * <p>策略阈值由配置或外部 Sentinel 动态数据源管理，注解只负责选择策略、
 * 解析动态 key 和声明可选的本地回退方法，避免把治理规则硬编码进业务代码。</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see RateLimitAspect
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限流策略名称。
     *
     * <p>留空时使用 {@code letool.rate-limiter.default-policy}。</p>
     *
     * @return 限流策略名称
     */
    String policy() default "";

    /**
     * 固定业务 key。
     *
     * <p>该值始终按普通文本处理；需要动态计算时使用 {@link #keyExpression()}。
     * 两者均留空时执行策略级全局限流，不能同时配置。</p>
     *
     * @return 固定业务 key
     */
    String key() default "";

    /**
     * 动态业务 key 的 SpEL 方法上下文表达式。
     *
     * <p>例如 {@code #phone}、{@code #request.userId}。表达式结果必须为非空文本，
     * 且不能与 {@link #key()} 同时配置。</p>
     *
     * @return 动态 key 表达式
     */
    String keyExpression() default "";

    /**
     * 本次调用消耗的许可数。
     *
     * @return 正整数许可数
     */
    int permits() default 1;

    /**
     * 请求被拒绝时调用的同类回退方法名称。
     *
     * <p>回退方法必须与目标方法参数列表一致；留空时抛出统一限流业务异常。</p>
     *
     * @return 回退方法名称
     */
    String fallbackMethod() default "";
}
