package com.github.leyland.letool.cache.aspect;

import com.github.leyland.letool.cache.annotation.MultiLevelCacheable;
import com.github.leyland.letool.cache.core.CacheConfig;
import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.serializer.JacksonCacheSerializer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    private ProceedingJoinPoint mockJoinPointThrowing(Throwable throwable) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getParameterNames()).thenReturn(new String[0]);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenThrow(throwable);
        return joinPoint;
    }
}
