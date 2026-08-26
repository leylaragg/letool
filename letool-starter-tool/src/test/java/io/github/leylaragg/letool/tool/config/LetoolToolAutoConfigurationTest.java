package io.github.leylaragg.letool.tool.config;

import io.github.leylaragg.letool.tool.http.HttpTemplate;
import io.github.leylaragg.letool.tool.json.Fastjson2JsonCodec;
import io.github.leylaragg.letool.tool.json.JsonCodec;
import io.github.leylaragg.letool.tool.spring.SpringUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 验证 Tool Starter 只装配通用工具，不再隐式承担 Redis 基础设施职责。
 */
class LetoolToolAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LetoolToolAutoConfiguration.class));

    /** 验证默认 JSON、Spring 上下文和 HTTP 门面都可直接注入。 */
    @Test
    void shouldProvideGeneralPurposeToolBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SpringUtil.class);
            assertThat(context).hasSingleBean(JsonCodec.class);
            assertThat(context.getBean(JsonCodec.class)).isInstanceOf(Fastjson2JsonCodec.class);
            assertThat(context).hasSingleBean(HttpTemplate.class);
        });
    }

    /** 验证业务自定义 JSON 编解码器拥有最高优先级。 */
    @Test
    void shouldBackOffWhenUserProvidesJsonCodec() {
        contextRunner.withUserConfiguration(UserJsonCodecConfiguration.class)
                .run(context -> assertThat(context.getBean(JsonCodec.class))
                        .isSameAs(context.getBean("userJsonCodec")));
    }

    /** 验证业务自定义 HTTP 模板不会被默认实现覆盖。 */
    @Test
    void shouldBackOffWhenUserProvidesHttpTemplate() {
        contextRunner.withUserConfiguration(UserHttpTemplateConfiguration.class)
                .run(context -> assertThat(context.getBean(HttpTemplate.class))
                        .isSameAs(context.getBean("userHttpTemplate")));
    }

    @Configuration(proxyBeanMethods = false)
    static class UserJsonCodecConfiguration {

        @Bean
        JsonCodec userJsonCodec() {
            return mock(JsonCodec.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserHttpTemplateConfiguration {

        @Bean
        HttpTemplate userHttpTemplate() {
            return new HttpTemplate();
        }
    }
}
