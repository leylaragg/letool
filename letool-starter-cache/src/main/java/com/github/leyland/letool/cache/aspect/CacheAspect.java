package com.github.leyland.letool.cache.aspect;

import com.github.leyland.letool.cache.annotation.MultiLevelCacheEvict;
import com.github.leyland.letool.cache.annotation.MultiLevelCachePut;
import com.github.leyland.letool.cache.annotation.MultiLevelCacheable;
import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.core.MultiLevelCache;
import com.github.leyland.letool.tool.util.SpelUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存注解切面 —— 拦截 {@code @MultiLevelCacheable} / {@code @MultiLevelCachePut} / {@code @MultiLevelCacheEvict}.
 */
@Aspect
public class CacheAspect {

    private final CacheManager cacheManager;

    public CacheAspect(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Around("@annotation(annotation)")
    public Object handleCacheable(ProceedingJoinPoint joinPoint, MultiLevelCacheable annotation) throws Throwable {
        String cacheName = annotation.name();
        String keyExpression = annotation.key();
        long ttl = annotation.ttl();

        Object key = resolveKey(keyExpression, joinPoint);
        MultiLevelCache<Object, Object> cache = cacheManager.get(cacheName);

        return cache.getOrLoad(key, k -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

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
}
