package io.github.leylaragg.letool.cache.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CacheException} 稳定错误码和安全消息测试。
 */
@DisplayName("CacheException 统一异常测试")
class CacheExceptionTest {

    @Test
    @DisplayName("配置错误应携带稳定错误码和安全字段名")
    void shouldCreateConfigurationException() {
        CacheException exception =
                CacheException.configurationInvalid("l1-ttl");

        assertThat(exception.getCode()).isEqualTo("CACHE_001");
        assertThat(exception.getMessage()).contains("l1-ttl");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("缓存不存在错误不应泄露业务缓存名称")
    void shouldCreateSafeCacheNotFoundException() {
        CacheException exception = CacheException.cacheNotFound();

        assertThat(exception.getCode()).isEqualTo("CACHE_002");
        assertThat(exception.getMessage())
                .doesNotContain("customer")
                .doesNotContain("user:1001");
    }

    @Test
    @DisplayName("回源失败应保留原因但不泄露业务 key")
    void shouldCreateSafeLoaderException() {
        IllegalStateException cause =
                new IllegalStateException("key=user:1001");

        CacheException exception = CacheException.loaderFailed(cause);

        assertThat(exception.getCode()).isEqualTo("CACHE_003");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.getMessage()).doesNotContain("user:1001");
    }

    @Test
    @DisplayName("非法失效消息应使用独立错误码")
    void shouldCreateInvalidationMessageException() {
        CacheException exception =
                CacheException.invalidationMessageInvalid();

        assertThat(exception.getCode()).isEqualTo("CACHE_004");
    }

    @Test
    @DisplayName("缓存类型冲突应使用稳定错误码且不泄露缓存名称")
    void shouldCreateSafeCacheTypeConflictException() {
        CacheException exception = CacheException.cacheTypeConflict();

        assertThat(exception.getCode()).isEqualTo("CACHE_005");
        assertThat(exception.getMessage())
                .doesNotContain("business-cache")
                .doesNotContain("shared-name");
    }

    @Test
    @DisplayName("异常工厂应拒绝空字段名和空原因")
    void shouldRejectInvalidFactoryArguments() {
        assertThatThrownBy(() -> CacheException.configurationInvalid(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CacheException.loaderFailed(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
