package com.github.leyland.letool.mail.config;

import com.github.leyland.letool.mail.core.DefaultMailSender;
import com.github.leyland.letool.mail.core.MailSender;
import com.github.leyland.letool.mail.core.MailTemplate;
import com.github.leyland.letool.mail.exception.MailException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 邮件模块 Spring Boot 自动配置。
 *
 * <p>配置属性始终可以绑定，只有显式设置 {@code letool.mail.enabled=true}
 * 才创建运行时基础设施。用户提供 {@link MailSender} 或 {@link MailTemplate}
 * 时对应默认 Bean 会退让；自定义发送器不依赖 Letool SMTP 账户配置。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(MailProperties.class)
public class MailAutoConfiguration {

    /**
     * 显式启用后创建邮件运行时基础设施。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "letool.mail",
            name = "enabled",
            havingValue = "true"
    )
    static class MailInfrastructureConfiguration {

        /**
         * 创建 Jakarta Mail 默认发送器。
         *
         * <p>创建前解析默认账户，使缺失或不合法配置在启动阶段失败。
         * 用户提供自定义 {@link MailSender} 时本方法不会执行。</p>
         *
         * @param properties 邮件配置
         * @return 默认邮件发送器
         */
        @Bean
        @ConditionalOnMissingBean
        public MailSender mailSender(MailProperties properties) {
            properties.getActiveAccount();
            return new DefaultMailSender(properties);
        }

        /**
         * 创建邮件构建和发送门面。
         *
         * @param mailSender 实际邮件发送器
         * @param properties 邮件配置
         * @return 邮件门面
         * @throws MailException 当异步线程数不合法时抛出
         */
        @Bean
        @ConditionalOnMissingBean(MailTemplate.class)
        public MailTemplate mailTemplate(
                MailSender mailSender,
                MailProperties properties) {
            if (properties.getAsyncPoolSize() <= 0) {
                throw MailException.configurationInvalid("async-pool-size");
            }
            if (properties.getAsyncQueueCapacity() <= 0) {
                throw MailException.configurationInvalid(
                        "async-queue-capacity"
                );
            }
            return new MailTemplate(
                    mailSender,
                    properties.getAsyncPoolSize(),
                    properties.getAsyncQueueCapacity()
            );
        }
    }
}
