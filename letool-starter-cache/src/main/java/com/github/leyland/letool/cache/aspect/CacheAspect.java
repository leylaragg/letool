package com.github.leyland.letool.cache.aspect;

import com.github.leyland.letool.cache.annotation.MultiLevelCacheEvict;
import com.github.leyland.letool.cache.annotation.MultiLevelCachePut;
import com.github.leyland.letool.cache.annotation.MultiLevelCacheable;
import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.core.MultiLevelCache;
import com.github.leyland.letool.cache.exception.CacheException;
import com.github.leyland.letool.tool.util.SpelUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 二级缓存注解切面。
 *
 * <p>该切面负责拦截 {@link MultiLevelCacheable}、{@link MultiLevelCachePut}
 * 和 {@link MultiLevelCacheEvict} 三类注解，把业务方法调用转换为缓存读写操作。
 * 注解上的 {@code name} 必须对应一个已经通过 {@link CacheManager} 注册的缓存实例；
 * 注解上的 {@code key} 会按 SpEL 表达式解析，表达式变量来自目标方法的参数名。</p>
 */
@Aspect
public class CacheAspect {

    /**
     * 缓存实例管理器，负责按缓存名称找到真正的二级缓存对象。
     */
    private final CacheManager cacheManager;

    public CacheAspect(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 处理 {@code @MultiLevelCacheable}。
     *
     * <p>读取流程为：先根据 SpEL 解析缓存 key，再从指定缓存实例中读取；
     * 如果 L1/L2 均未命中，则执行原业务方法并把结果写回缓存。
     * 当注解显式配置 {@code ttl > 0} 时，本次加载结果会使用注解上的 TTL，
     * 否则使用缓存实例自身的默认 L2 TTL。</p>
     *
     * <p>如果目标业务方法抛出异常，切面会把原始异常原样抛回给调用方。
     * 这样业务系统接入缓存注解后，原有的异常捕获逻辑不会因为缓存框架包装而失效。</p>
     */
    @Around("@annotation(annotation)")
    public Object handleCacheable(ProceedingJoinPoint joinPoint, MultiLevelCacheable annotation) throws Throwable {
        String cacheName = annotation.name();
        String keyExpression = annotation.key();
        long ttl = annotation.ttl();

        Object key = resolveKey(keyExpression, joinPoint);
        MultiLevelCache<Object, Object> cache = cacheManager.get(cacheName);

        java.util.function.Function<Object, Object> loader = k -> {
            try {
                return joinPoint.proceed();
            } catch (RuntimeException e) {
                throw e;
            } catch (Throwable e) {
                throw new BusinessInvocationException(e);
            }
        };
        try {
            if (ttl > 0) {
                return cache.getOrLoad(key, loader, Duration.ofSeconds(ttl));
            }
            return cache.getOrLoad(key, loader);
        } catch (CacheException e) {
            Throwable businessException = unwrapBusinessException(e);
            if (businessException != null) {
                throw businessException;
            }
            throw e;
        }
    }

    /**
     * 处理 {@code @MultiLevelCachePut}。
     *
     * <p>该注解适用于“方法一定要执行，并把返回值刷新到缓存”的场景。
     * 因此这里会先调用目标方法拿到最新结果，再按注解 key 写入对应缓存。</p>
     */
    @Around("@annotation(annotation)")
    public Object handleCachePut(ProceedingJoinPoint joinPoint, MultiLevelCachePut annotation) throws Throwable {
        String cacheName = annotation.name();
        String keyExpression = annotation.key();
        long ttl = annotation.ttl();

        Object result = joinPoint.proceed();
        Object key = resolveKey(keyExpression, joinPoint);
        MultiLevelCache<Object, Object> cache = cacheManager.get(cacheName);

        if (ttl > 0) {
            cache.put(key, result, Duration.ofSeconds(ttl));
        } else {
            cache.put(key, result);
        }
        return result;
    }

    /**
     * 处理 {@code @MultiLevelCacheEvict}。
     *
     * <p>该注解适用于数据被删除或变更后主动清理缓存的场景。
     * 目标方法成功执行后，当前 JVM 的 L1、Redis L2 以及其他 JVM 的 L1 都会被清理。</p>
     */
    @Around("@annotation(annotation)")
    public Object handleCacheEvict(ProceedingJoinPoint joinPoint, MultiLevelCacheEvict annotation) throws Throwable {
        String cacheName = annotation.name();
        String keyExpression = annotation.key();

        Object result = joinPoint.proceed();
        Object key = resolveKey(keyExpression, joinPoint);
        MultiLevelCache<Object, Object> cache = cacheManager.get(cacheName);
        cache.evict(key);
        return result;
    }

    /**
     * 根据注解中的 SpEL 表达式解析缓存 key。
     *
     * <p>例如业务方法参数为 {@code userId}，注解 key 写成 {@code #userId}，
     * 这里会把方法参数名和参数值组装成变量上下文，再交给 {@link SpelUtil} 求值。</p>
     */
    private Object resolveKey(String expression, ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        Map<String, Object> variables = new HashMap<>();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                variables.put(paramNames[i], args[i]);
            }
        }
        return SpelUtil.eval(expression, null, variables);
    }

    /**
     * 从缓存层包装异常中拆出原始业务异常。
     *
     * <p>{@link MultiLevelCache#getOrLoad(Object, java.util.function.Function)} 的 loader
     * 使用 {@link java.util.function.Function}，不能直接抛 checked exception。
     * 切面内部会先用 {@link BusinessInvocationException} 包一层业务异常，缓存实现再把它包成
     * {@link CacheException}。这里识别该内部标记异常，并把业务原异常原样抛回给调用方，
     * 避免业务代码因为接入缓存注解而改变异常语义。</p>
     */
    private Throwable unwrapBusinessException(CacheException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof BusinessInvocationException businessInvocationException) {
            return businessInvocationException.getCause();
        }
        return null;
    }

    /**
     * 仅供切面内部使用的异常标记。
     *
     * <p>它表示异常来自目标业务方法，而不是缓存框架自身。外部调用方不应该感知这个类型。</p>
     */
    private static class BusinessInvocationException extends RuntimeException {
        BusinessInvocationException(Throwable cause) {
            super(cause);
        }
    }
}
