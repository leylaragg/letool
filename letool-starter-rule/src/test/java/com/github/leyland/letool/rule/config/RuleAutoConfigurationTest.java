package com.github.leyland.letool.rule.config;

import com.github.leyland.letool.rule.core.RuleTemplate;
import com.yomahub.liteflow.core.FlowExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link RuleAutoConfiguration} 自动配置契约测试。
 *
 * <p>自动配置只负责在 LiteFlow 已提供 {@link FlowExecutor} 时创建便捷模板，
 * 不重复创建规则引擎、解析器、存储或监控等基础设施。</p>
 */
class RuleAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RuleAutoConfiguration.class));

    /**
     * 验证应用未提供 LiteFlow 执行器时不会创建不可用的模板。
     */
    @Test
    void shouldNotCreateRuleTemplateWithoutFlowExecutor() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(FlowExecutor.class);
            assertThat(context).doesNotHaveBean(RuleTemplate.class);
        });
    }

    /**
     * 验证 LiteFlow 执行器存在时自动创建唯一的规则执行模板。
     */
    @Test
    void shouldCreateRuleTemplateWhenFlowExecutorExists() {
        contextRunner
                .withUserConfiguration(FlowExecutorConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(FlowExecutor.class);
                    assertThat(context).hasSingleBean(RuleTemplate.class);
                    assertLegacyInfrastructureAbsent(context);
                });
    }

    /**
     * 验证用户自定义规则执行模板时自动配置主动退让。
     */
    @Test
    void shouldBackOffWhenUserProvidesRuleTemplate() {
        contextRunner
                .withUserConfiguration(UserRuleTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RuleTemplate.class);
                    assertThat(context.getBean(RuleTemplate.class))
                            .isSameAs(context.getBean("userRuleTemplate"));
                    assertLegacyInfrastructureAbsent(context);
                });
    }

    /**
     * 断言自动配置未创建已废弃的自研规则基础设施。
     *
     * @param context 当前自动配置测试上下文
     */
    private void assertLegacyInfrastructureAbsent(AssertableApplicationContext context) {
        assertThat(context).doesNotHaveBean("chainParser");
        assertThat(context).doesNotHaveBean("chainManager");
        assertThat(context).doesNotHaveBean("groovyScriptEngine");
        assertThat(context).doesNotHaveBean("ruleEngine");
        assertThat(context).doesNotHaveBean("ruleStore");
        assertThat(context).doesNotHaveBean("ruleMonitor");
        assertThat(context).doesNotHaveBean("fileWatcher");
        assertThat(context).doesNotHaveBean("ruleHotReloadListener");
        assertThat(context).doesNotHaveBean("ruleController");
    }

    /**
     * 提供 LiteFlow 执行器的用户配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class FlowExecutorConfiguration {

        /**
         * 创建用于验证自动配置条件的 LiteFlow 执行器。
         *
         * @return LiteFlow 执行器
         */
        @Bean
        FlowExecutor flowExecutor() {
            return mock(FlowExecutor.class);
        }
    }

    /**
     * 同时提供 LiteFlow 执行器和用户自定义模板的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserRuleTemplateConfiguration {

        /**
         * 创建用户自行管理的 LiteFlow 执行器。
         *
         * @return LiteFlow 执行器
         */
        @Bean
        FlowExecutor flowExecutor() {
            return mock(FlowExecutor.class);
        }

        /**
         * 创建用户自定义模板，用于验证自动配置退让。
         *
         * @return 用户自定义模板
         */
        @Bean
        RuleTemplate userRuleTemplate() {
            return mock(RuleTemplate.class);
        }
    }
}
