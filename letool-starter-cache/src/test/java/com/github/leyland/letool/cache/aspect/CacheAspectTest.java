package com.github.leyland.letool.cache.aspect;

import com.github.leyland.letool.cache.annotation.MultiLevelCacheable;
import com.github.leyland.letool.cache.annotation.MultiLevelCachePut;
import com.github.leyland.letool.cache.consistency.CacheWritePolicy;
import com.github.leyland.letool.cache.consistency.CacheMutationCoordinator;
import com.github.leyland.letool.cache.consistency.CacheMutation;
import com.github.leyland.letool.cache.core.CacheConfig;
import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.serializer.JacksonCacheSerializer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

/**
 * 缓存注解切面的生产语义测试。
 *
 * <p>这里不启动完整 Spring 容器，而是直接模拟 AOP 调用点，重点验证切面本身
 * 对业务方法异常、缓存 key 解析和缓存读写委托的处理是否稳定。</p>
 */
@DisplayName("缓存注解切面")
class CacheAspectTest {

    @Test
    @DisplayName("@MultiLevelCacheable 回源失败时应保留业务原始异常")
    void cacheableShouldRethrowOriginalBusinessException() throws Throwable {
        CacheManager cacheManager = new CacheManager(null, new JacksonCacheSerializer());
        cacheManager.getOrCreate(CacheConfig.<String, String>builder("users"));
        CacheAspect aspect = new CacheAspect(cacheManager);

        IllegalStateException original = new IllegalStateException("database unavailable");
        ProceedingJoinPoint joinPoint = mockJoinPointThrowing(original);
        MultiLevelCacheable annotation = mock(MultiLevelCacheable.class);
        when(annotation.name()).thenReturn("users");
        when(annotation.key()).thenReturn("'u1'");
        when(annotation.ttl()).thenReturn(0L);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> aspect.handleCacheable(joinPoint, annotation));
        assertSame(original, thrown);
    }

    @Test
    @DisplayName("@MultiLevelCachePut 默认在业务成功后失效旧缓存")
    void cachePutShouldInvalidateByDefault() throws Throwable {
        CacheManager cacheManager = new CacheManager(null, new JacksonCacheSerializer());
        var cache = cacheManager.getOrCreate(CacheConfig.<String, String>builder("users")
                .l2Enabled(false));
        cache.put("u1", "old");
        CacheAspect aspect = new CacheAspect(cacheManager);

        ProceedingJoinPoint joinPoint = mockJoinPointReturning("new");
        MultiLevelCachePut annotation = mock(MultiLevelCachePut.class);
        when(annotation.name()).thenReturn("users");
        when(annotation.key()).thenReturn("'u1'");
        when(annotation.ttl()).thenReturn(0L);

        assertEquals("new", aspect.handleCachePut(joinPoint, annotation));
        assertNull(cache.getIfPresent("u1"));
    }

    @Test
    @DisplayName("@MultiLevelCachePut 显式 UPDATE 时写回业务返回值")
    void cachePutShouldUpdateWhenConfigured() throws Throwable {
        CacheManager cacheManager = new CacheManager(null, new JacksonCacheSerializer());
        var cache = cacheManager.getOrCreate(CacheConfig.<String, String>builder("users")
                .l2Enabled(false)
                .writePolicy(CacheWritePolicy.UPDATE));
        cache.put("u1", "old");
        CacheAspect aspect = new CacheAspect(cacheManager);

        ProceedingJoinPoint joinPoint = mockJoinPointReturning("new");
        MultiLevelCachePut annotation = mock(MultiLevelCachePut.class);
        when(annotation.name()).thenReturn("users");
        when(annotation.key()).thenReturn("'u1'");
        when(annotation.ttl()).thenReturn(0L);

        assertEquals("new", aspect.handleCachePut(joinPoint, annotation));
        assertEquals("new", cache.getIfPresent("u1"));
    }

    @Test
    @DisplayName("修改协调器使用缓存配置的稳定 Key 序列化结果")
    void mutationShouldUseConfiguredKeySerializer() throws Throwable {
        CacheManager cacheManager = new CacheManager(null, new JacksonCacheSerializer());
        cacheManager.getOrCreate(CacheConfig.<String, String>builder("users")
                .l2Enabled(false)
                .keySerializer(key -> "stable-" + key));
        CacheMutationCoordinator coordinator = mock(CacheMutationCoordinator.class);
        when(coordinator.execute(any(CacheMutation.class), any(), any())).thenAnswer(invocation -> {
            CacheMutationCoordinator.ThrowingSupplier<?> action = invocation.getArgument(1);
            return action.get();
        });
        CacheAspect aspect = new CacheAspect(cacheManager, coordinator);
        ProceedingJoinPoint joinPoint = mockJoinPointReturning("new");
        MultiLevelCachePut annotation = mock(MultiLevelCachePut.class);
        when(annotation.name()).thenReturn("users");
        when(annotation.key()).thenReturn("'u1'");

        aspect.handleCachePut(joinPoint, annotation);

        verify(coordinator).execute(eq(new CacheMutation(
                com.github.leyland.letool.cache.consistency.CacheConsistencyMode.TRANSACTIONAL,
                "users", "stable-u1")), any(), any());
    }

    private ProceedingJoinPoint mockJoinPointThrowing(Throwable throwable) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(throwable);
        return joinPoint;
    }

    private ProceedingJoinPoint mockJoinPointReturning(Object value) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(value);
        return joinPoint;
    }
}
