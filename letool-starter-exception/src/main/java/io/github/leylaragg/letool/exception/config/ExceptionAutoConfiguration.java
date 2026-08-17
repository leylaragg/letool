package io.github.leylaragg.letool.exception.config;

import io.github.leylaragg.letool.exception.message.DefaultMessageResolver;
import io.github.leylaragg.letool.exception.message.MessageBundleContributor;
import io.github.leylaragg.letool.exception.message.MessageResolver;
import io.github.leylaragg.letool.exception.message.SpringMessageResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 提供异常模块的默认消息解析器和 Starter 消息资源贡献。
 *
 * <p>应用自定义的解析器优先。当启用国际化时，名称为 {@code messageSource} 的 Bean
 * 仅用于覆盖框架消息，本自动配置不会替换它。</p>
 */
@AutoConfiguration(after = MessageSourceAutoConfiguration.class)
@EnableConfigurationProperties(ExceptionProperties.class)
@ConditionalOnProperty(
        prefix = "letool.exception",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ExceptionAutoConfiguration {

    /**
     * 将框架通用异常消息加入国际化查找范围。
     *
     * @return 通用异常消息资源描述
     */
    @Bean
    public MessageBundleContributor commonExceptionMessageBundle() {
        return MessageBundleContributor.of("i18n/letool-exception/messages");
    }

    /**
     * 创建默认消息解析器，同时保留用户对解析器和消息源的控制权。
     *
     * <p>Starter 使用的 {@link ResourceBundleMessageSource} 只作为局部协作者创建，
     * 不会暴露为 {@link MessageSource} Bean，避免与 Spring Boot 或应用提供的
     * {@code messageSource} Bean 发生竞争。</p>
     *
     * @param properties 已绑定的异常配置
     * @param applicationMessageSourceProvider 仅查找应用中名为 {@code messageSource} 的 Bean
     * @param contributors Starter 与扩展模块按顺序提供的消息资源
     * @return 启用国际化时返回国际化解析器，否则返回不依赖资源包的解析器
     * @throws NullPointerException 当国际化配置或默认语言环境为 {@code null} 时抛出
     */
    @Bean
    @ConditionalOnMissingBean(MessageResolver.class)
    public MessageResolver messageResolver(
            ExceptionProperties properties,
            @Qualifier(AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME)
                    ObjectProvider<MessageSource> applicationMessageSourceProvider,
            List<MessageBundleContributor> contributors) {
        ExceptionProperties.I18n i18n =
                Objects.requireNonNull(
                        properties.getI18n(),
                        "letool.exception.i18n must not be null");
        Locale defaultLocale =
                Objects.requireNonNull(
                        i18n.getDefaultLocale(),
                        "letool.exception.i18n.default-locale must not be null");

        if (!i18n.isEnabled()) {
            return new DefaultMessageResolver(defaultLocale);
        }

        List<String> basenames = normalizeBasenames(contributors);

        ResourceBundleMessageSource starterMessageSource = new ResourceBundleMessageSource();
        starterMessageSource.setBasenames(basenames.toArray(String[]::new));
        starterMessageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        starterMessageSource.setFallbackToSystemLocale(i18n.isFallbackToSystemLocale());

        return new SpringMessageResolver(
                applicationMessageSourceProvider.getIfAvailable(),
                starterMessageSource,
                defaultLocale);
    }

    /**
     * 规范化资源包名称并保持贡献者及其内部的声明顺序；名称重复时保留首次出现的资源包。
     */
    static List<String> normalizeBasenames(List<MessageBundleContributor> contributors) {
        LinkedHashSet<String> basenames = new LinkedHashSet<>();
        for (MessageBundleContributor contributor : contributors) {
            for (String basename : contributor.getBasenames()) {
                basenames.add(basename.trim());
            }
        }
        return List.copyOf(basenames);
    }
}
