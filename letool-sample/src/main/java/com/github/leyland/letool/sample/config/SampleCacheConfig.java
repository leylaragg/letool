package com.github.leyland.letool.sample.config;

import com.github.leyland.letool.cache.core.CacheConfig;
import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.core.MultiLevelCache;
import com.github.leyland.letool.sample.entity.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 演示缓存配置.
 */
@Configuration
public class SampleCacheConfig {

    @Bean
    public MultiLevelCache<Long, User> userCache(CacheManager cacheManager) {
        return cacheManager.getOrCreate(
                CacheConfig.<Long, User>builder("userCache")
                        .l1MaxSize(100)
                        .l1Ttl(Duration.ofHours(1))
                        .l2Ttl(Duration.ofHours(12))
                        .nullValueCache(true)
                        .nullValueTtl(Duration.ofMinutes(5))
                        .build()
        );
    }
}
