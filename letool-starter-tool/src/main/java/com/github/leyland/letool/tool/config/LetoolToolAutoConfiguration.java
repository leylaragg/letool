package com.github.leyland.letool.tool.config;

import com.github.leyland.letool.tool.redis.RedisUtil;
import com.github.leyland.letool.tool.spring.SpringUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Auto-configuration for the base tool starter.
 *
 * <p>This module is the lightweight toolkit foundation. It registers only the
 * Spring adapter beans that are useful by default and keeps optional adapters,
 * such as Redis, behind explicit classpath and bean conditions.</p>
 */
@AutoConfiguration
public class LetoolToolAutoConfiguration {

    /**
     * Registers the Spring application-context helper unless the application owns it.
     *
     * @return Spring application-context helper.
     */
    @Bean
    @ConditionalOnMissingBean(SpringUtil.class)
    public SpringUtil springUtil() {
        return new SpringUtil();
    }

    /**
     * Redis-specific adapter configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisTemplate.class)
    static class RedisToolConfiguration {

        /**
         * Registers Redis helper only when application Redis infrastructure exists.
         *
         * @param redisTemplate Spring Redis object template.
         * @return Redis helper wrapper.
         */
        @Bean
        @ConditionalOnBean(name = "redisTemplate")
        @ConditionalOnMissingBean(RedisUtil.class)
        public RedisUtil redisUtil(RedisTemplate<String, Object> redisTemplate) {
            return new RedisUtil(redisTemplate);
        }
    }
}
