package io.github.leylaragg.letool.ratelimiter.aspect;

import io.github.leylaragg.letool.ratelimiter.annotation.RateLimit;
import io.github.leylaragg.letool.ratelimiter.core.RateLimitTemplate;
import io.github.leylaragg.letool.ratelimiter.exception.RateLimitConfigurationException;
import io.github.leylaragg.letool.ratelimiter.exception.RateLimitException;
import io.github.leylaragg.letool.tool.util.SpelUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * {@link RateLimit} 声明式限流切面。
 *
 * <p>切面只处理 Letool 提供的策略选择、SpEL key 与本地回退方法；
 * 实际限流判定委托 {@link RateLimitTemplate} 和 Sentinel 完成。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@Aspect
public class RateLimitAspect {

    /**
     * 限流模板。
     */
    private final RateLimitTemplate rateLimitTemplate;

    /**
     * 创建声明式限流切面。
     *
     * @param rateLimitTemplate 限流模板
     */
    public RateLimitAspect(RateLimitTemplate rateLimitTemplate) {
        this.rateLimitTemplate = rateLimitTemplate;
    }

    /**
     * 在目标方法执行前完成限流判定。
     *
     * @param joinPoint 方法连接点
     * @param rateLimit 方法上的限流注解
     * @return 目标方法或回退方法的结果
     * @throws Throwable 目标方法或回退方法抛出的异常
     */
    @Around("@annotation(rateLimit)")
    public Object aroundRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit)
            throws Throwable {
        Method method = resolveMethod(joinPoint);
        String key = resolveKey(joinPoint, method, rateLimit);
        boolean allowed = rateLimitTemplate.tryAcquire(
                rateLimit.policy(), key, rateLimit.permits());
        if (allowed) {
            return joinPoint.proceed();
        }

        if (!rateLimit.fallbackMethod().isBlank()) {
            return invokeFallback(joinPoint, method, rateLimit.fallbackMethod());
        }

        String policy = rateLimit.policy().isBlank()
                ? rateLimitTemplate.getDefaultPolicy()
                : rateLimit.policy();
        throw RateLimitException.rejected(policy);
    }

    /**
     * 解析目标实现类上的实际方法。
     *
     * @param joinPoint 方法连接点
     * @return 实际目标方法
     */
    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return AopUtils.getMostSpecificMethod(
                signature.getMethod(),
                joinPoint.getTarget().getClass()
        );
    }

    /**
     * 解析固定 key 或方法上下文 SpEL 表达式。
     *
     * @param joinPoint 方法连接点
     * @param method    实际目标方法
     * @param rateLimit 限流注解
     * @return 动态业务 key；留空时返回 {@code null}
     */
    private String resolveKey(ProceedingJoinPoint joinPoint,
                              Method method,
                              RateLimit rateLimit) {
        String fixedKey = rateLimit.key();
        String keyExpression = rateLimit.keyExpression();
        if (!fixedKey.isBlank() && !keyExpression.isBlank()) {
            throw RateLimitConfigurationException.invalid("annotation.key");
        }
        if (!fixedKey.isBlank()) {
            return fixedKey;
        }
        if (keyExpression.isBlank()) {
            return null;
        }

        String resolvedKey = SpelUtil.evalMethod(
                keyExpression,
                joinPoint.getTarget(),
                method,
                joinPoint.getArgs(),
                String.class
        );
        if (resolvedKey == null || resolvedKey.isBlank()) {
            throw RateLimitConfigurationException.invalid("annotation.key");
        }
        return resolvedKey;
    }

    /**
     * 调用参数签名与目标方法一致的本地回退方法。
     *
     * @param joinPoint      方法连接点
     * @param targetMethod   被限流的目标方法
     * @param fallbackMethod 回退方法名称
     * @return 回退方法结果
     * @throws Throwable 回退方法抛出的原始异常
     */
    private Object invokeFallback(ProceedingJoinPoint joinPoint,
                                  Method targetMethod,
                                  String fallbackMethod) throws Throwable {
        Object target = joinPoint.getTarget();
        Method method = ReflectionUtils.findMethod(
                target.getClass(),
                fallbackMethod,
                targetMethod.getParameterTypes()
        );
        if (method == null || !isReturnTypeCompatible(targetMethod, method)) {
            throw RateLimitConfigurationException.invalidFallback(fallbackMethod);
        }

        ReflectionUtils.makeAccessible(method);
        try {
            return method.invoke(target, joinPoint.getArgs());
        } catch (InvocationTargetException exception) {
            throw exception.getTargetException();
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            throw RateLimitConfigurationException.invalidFallback(fallbackMethod);
        }
    }

    /**
     * 判断回退方法返回类型是否兼容目标方法。
     *
     * @param targetMethod   目标方法
     * @param fallbackMethod 回退方法
     * @return 返回类型兼容时返回 {@code true}
     */
    private boolean isReturnTypeCompatible(Method targetMethod, Method fallbackMethod) {
        Class<?> targetType = targetMethod.getReturnType();
        Class<?> fallbackType = fallbackMethod.getReturnType();
        return targetType == Void.TYPE
                ? fallbackType == Void.TYPE
                : targetType.isAssignableFrom(fallbackType);
    }
}
