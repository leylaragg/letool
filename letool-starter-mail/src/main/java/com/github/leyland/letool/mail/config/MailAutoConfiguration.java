package com.github.leyland.letool.mail.config;

import com.github.leyland.letool.mail.core.DefaultMailSender;
import com.github.leyland.letool.mail.core.MailSender;
import com.github.leyland.letool.mail.core.MailTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

// ======================== 类级别说明 ========================

/**
 * <p>邮件模块的 Spring Boot 自动配置类。</p>
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li>注册 {@link MailSender} Bean — 邮件发送的核心执行器 ({@link DefaultMailSender})。</li>
 *   <li>注册 {@link MailTemplate} Bean — 面向用户的链式调用模板，提供 Builder 风格的邮件发送 API。</li>
 * </ul>
 *
 * <h3>启用条件</h3>
 * <ul>
 *   <li>当 {@code letool.mail.enabled=true} 时生效（该属性默认为 {@code true}，即引入依赖后自动启用）。</li>
 *   <li>可通过设置 {@code letool.mail.enabled=false} 显式禁用整个邮件模块。</li>
 * </ul>
 *
 * <h3>Bean 覆盖机制</h3>
 * <ul>
 *   <li>{@link MailSender} 标注了 {@code @ConditionalOnMissingBean}，用户可自行提供自定义实现来覆盖默认行为。</li>
 *   <li>{@link MailTemplate} 不标注 {@code @ConditionalOnMissingBean}，始终由本配置类创建。</li>
 * </ul>
 *
 * @author leyland
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(MailProperties.class)
@ConditionalOnProperty(prefix = "letool.mail", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MailAutoConfiguration {

    // ======================== 日志记录器 ========================

    private static final Logger log = LoggerFactory.getLogger(MailAutoConfiguration.class);

    // ======================== Bean 定义 ========================

    /**
     * 创建邮件发送器 Bean。
     *
     * <p>基于 {@code properties} 中的活跃账户配置构建 {@link DefaultMailSender} 实例。
     * 日志会记录当前使用的 SMTP 主机和协议信息，便于启动时排查配置问题。</p>
     *
     * @param properties 邮件配置属性（由 Spring Boot 自动绑定）
     * @return 邮件发送器实例
     */
    @Bean
    @ConditionalOnMissingBean
    public MailSender mailSender(MailProperties properties) {
        log.info("Initializing mail sender: host={}, protocol={}",
                properties.getActiveAccount().getHost(), properties.getActiveAccount().getProtocol());
        return new DefaultMailSender(properties);
    }

    /**
     * 创建邮件模板 Bean — 用户操作邮件的核心入口。
     *
     * <p>{@link MailTemplate} 封装了 {@link MailSender} 与异步线程池，
     * 提供 Builder 模式的链式调用 API，简化邮件构建与发送流程。</p>
     *
     * @param mailSender 邮件发送器
     * @param properties 邮件配置属性（用于读取异步线程池大小）
     * @return 邮件模板实例
     */
    @Bean
    public MailTemplate mailTemplate(MailSender mailSender, MailProperties properties) {
        return new MailTemplate(mailSender, properties.getAsyncPoolSize());
    }
}
