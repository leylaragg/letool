package com.github.leyland.letool.cache.config;

import com.github.leyland.letool.cache.consistency.CacheConsistencyMode;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 判断配置文件是否启用了至少一个 DURABLE KV 缓存。
 *
 * <p>默认 JDBC Outbox 和恢复线程只应在确实使用 DURABLE 时创建，避免普通 TRANSACTIONAL
 * 项目因为类路径中恰好存在 Redis 与 JdbcTemplate 就访问未创建的 Outbox 表。</p>
 */
final class DurableCacheConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        CacheProperties properties = Binder.get(context.getEnvironment())
                .bind("letool.cache", Bindable.of(CacheProperties.class))
                .orElseGet(CacheProperties::new);
        if (properties.getConsistency().getMode() == CacheConsistencyMode.DURABLE) {
            return true;
        }
        return properties.getInstances().stream()
                .anyMatch(instance -> instance.getConsistencyMode() == CacheConsistencyMode.DURABLE);
    }
}
