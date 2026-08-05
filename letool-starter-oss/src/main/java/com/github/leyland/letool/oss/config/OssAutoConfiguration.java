package com.github.leyland.letool.oss.config;

import com.github.leyland.letool.oss.core.OssProvider;
import com.github.leyland.letool.oss.core.OssTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * OSS 公共自动配置。
 *
 * <p>公共模块不创建任何伪 Provider。启用 OSS 后，业务项目必须引入一个官方 Provider
 * starter 或注册自己的 {@link OssProvider} Bean；否则 Spring 会在创建模板时明确报告
 * Provider 缺失。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(OssProperties.class)
@ConditionalOnProperty(prefix = "letool.oss", name = "enabled", havingValue = "true")
public class OssAutoConfiguration {

    /**
     * 创建业务统一使用的 OSS 模板。
     *
     * @param ossProvider 官方适配器或业务自定义 Provider
     * @param properties OSS 公共配置
     * @return OSS 模板
     */
    @Bean
    @ConditionalOnMissingBean
    public OssTemplate ossTemplate(OssProvider ossProvider, OssProperties properties) {
        return new OssTemplate(ossProvider, properties);
    }
}
