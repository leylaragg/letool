package io.github.leylaragg.letool.lock.idempotent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 基于原子占位存储实现的后端无关幂等服务。
 *
 * <p>重复请求不执行业务回调并返回 {@code null}；首次请求的业务执行失败时撤销占位，
 * 让后续请求可以重新尝试。</p>
 */
public class IdempotentService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentService.class);

    private final IdempotentStore store;

    /** @param store 能够原子占位的幂等存储 */
    public IdempotentService(IdempotentStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    /**
     * 在幂等占位保护下执行业务回调。
     *
     * @param key 完整幂等 key
     * @param ttl 占位存活时间
     * @param supplier 仅首次请求执行的业务回调
     * @param <T> 业务返回类型
     * @return 首次请求的业务结果；重复请求返回 {@code null}
     */
    public <T> T execute(String key, Duration ttl, Supplier<T> supplier) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("幂等 key 不能为空");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("幂等 TTL 必须大于零");
        }
        Objects.requireNonNull(supplier, "supplier must not be null");
        if (!store.putIfAbsent(key, ttl)) {
            log.debug("Duplicate idempotent request: key={}", key);
            return null;
        }
        try {
            return supplier.get();
        } catch (RuntimeException | Error exception) {
            store.remove(key);
            throw exception;
        }
    }

    /**
     * 秒单位兼容入口，内部统一转换为 {@link Duration}。
     *
     * @param key 完整幂等 key
     * @param ttlSeconds 占位秒数
     * @param supplier 业务回调
     * @param <T> 业务返回类型
     * @return 幂等执行结果
     */
    public <T> T execute(String key, long ttlSeconds, Supplier<T> supplier) {
        return execute(key, Duration.ofSeconds(ttlSeconds), supplier);
    }
}
