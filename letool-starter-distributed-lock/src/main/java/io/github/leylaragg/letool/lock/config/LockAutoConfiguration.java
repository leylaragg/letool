package io.github.leylaragg.letool.lock.config;

import io.github.leylaragg.letool.lock.aspect.IdempotentAspect;
import io.github.leylaragg.letool.lock.aspect.LockAspect;
import io.github.leylaragg.letool.lock.core.DistributedLock;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.lock.idempotent.IdempotentService;
import io.github.leylaragg.letool.lock.idempotent.IdempotentStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 分布式锁通用契约的自动配置。
 *
 * <p>本模块只观察业务提供或后端 Starter 注册的 SPI Bean，不选择具体存储实现。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(LockProperties.class)
@ConditionalOnProperty(prefix = "letool.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LockAutoConfiguration {

    /**
     * @param distributedLock 已注册的锁后端
     * @return 自动管理锁句柄的函数式模板
     */
    @Bean
    @ConditionalOnBean(DistributedLock.class)
    @ConditionalOnMissingBean(LockTemplate.class)
    public LockTemplate lockTemplate(DistributedLock distributedLock) {
        return new LockTemplate(distributedLock);
    }

    /**
     * @param lockTemplate 锁模板
     * @return 声明式锁切面
     */
    @Bean
    @ConditionalOnBean(LockTemplate.class)
    @ConditionalOnMissingBean(LockAspect.class)
    public LockAspect lockAspect(LockTemplate lockTemplate) {
        return new LockAspect(lockTemplate);
    }

    /**
     * @param store 幂等占位存储
     * @return 后端无关的幂等执行服务
     */
    @Bean
    @ConditionalOnBean(IdempotentStore.class)
    @ConditionalOnProperty(prefix = "letool.lock.idempotent", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(IdempotentService.class)
    public IdempotentService idempotentService(IdempotentStore store) {
        return new IdempotentService(store);
    }

    /**
     * @param service 幂等服务
     * @return 声明式幂等切面
     */
    @Bean
    @ConditionalOnBean(IdempotentService.class)
    @ConditionalOnMissingBean(IdempotentAspect.class)
    public IdempotentAspect idempotentAspect(IdempotentService service) {
        return new IdempotentAspect(service);
    }
}
