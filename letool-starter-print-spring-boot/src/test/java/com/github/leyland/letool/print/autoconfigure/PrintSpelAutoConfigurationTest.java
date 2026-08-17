package com.github.leyland.letool.print.autoconfigure;

import com.github.leyland.letool.print.spel.RestrictedSpelConditionExpression;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 受限 SpEL 额外依赖与显式开关的自动配置测试。
 *
 * @author leyland
 */
class PrintSpelAutoConfigurationTest {

    /** 同时加载 SpEL 条件配置和主打印配置。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PrintSpelAutoConfiguration.class, PrintAutoConfiguration.class));

    /** 模块在类路径中但开关关闭时不注册表达式提供方。 */
    @Test
    void shouldNotRegisterSpelWhenSwitchIsOff() {
        contextRunner.run(context -> assertThat(context)
                .doesNotHaveBean(RestrictedSpelConditionExpression.class));
    }

    /** 额外依赖存在并显式开启后注册唯一受限实现。 */
    @Test
    void shouldRegisterSpelWhenPresentAndEnabled() {
        contextRunner.withPropertyValues("letool.print.spel.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(RestrictedSpelConditionExpression.class));
    }

    /** 开关开启但额外模块缺失时给出稳定配置错误。 */
    @Test
    void shouldFailWhenSwitchEnabledWithoutSpelModule() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(RestrictedSpelConditionExpression.class))
                .withPropertyValues("letool.print.spel.enabled=true")
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause()
                        .hasMessageContaining("letool-starter-print-expression-spel"));
    }
}
