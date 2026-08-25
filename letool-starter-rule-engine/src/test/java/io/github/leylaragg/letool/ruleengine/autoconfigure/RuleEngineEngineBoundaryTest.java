package io.github.leylaragg.letool.ruleengine.autoconfigure;

import io.github.leylaragg.letool.exception.config.ExceptionAutoConfiguration;
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 检查 Starter 只装配完整表达式引擎，不再把内核流水线零件暴露为 Bean。
 */
@DisplayName("规则引擎 Starter 完整门面边界")
class RuleEngineEngineBoundaryTest {

    /** 自动配置上下文仅保留完整引擎入口。 */
    @Test
    @DisplayName("默认上下文不注册局部编译器和求值器")
    void defaultContextDoesNotPublishPipelineComponents() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        ExceptionAutoConfiguration.class,
                        RuleEngineAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(ExpressionEngine.class);
                    assertThat(context).doesNotHaveBean("expressionCompiler");
                    assertThat(context).doesNotHaveBean("expressionEvaluator");
                });
    }
}
