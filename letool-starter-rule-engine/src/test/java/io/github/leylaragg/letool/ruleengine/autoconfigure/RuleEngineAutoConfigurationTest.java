package io.github.leylaragg.letool.ruleengine.autoconfigure;

import io.github.leylaragg.letool.exception.config.ExceptionAutoConfiguration;
import io.github.leylaragg.letool.exception.message.MessageBundleContributor;
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.api.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticMessageResolver;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineErrorCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.fact.FactValues;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.function.FunctionArguments;
import io.github.leylaragg.letool.ruleengine.function.FunctionCharacteristics;
import io.github.leylaragg.letool.ruleengine.function.FunctionContext;
import io.github.leylaragg.letool.ruleengine.function.FunctionDescriptor;
import io.github.leylaragg.letool.ruleengine.function.FunctionDeterminism;
import io.github.leylaragg.letool.ruleengine.function.FunctionEffect;
import io.github.leylaragg.letool.ruleengine.function.FunctionSignature;
import io.github.leylaragg.letool.ruleengine.function.FunctionThreading;
import io.github.leylaragg.letool.ruleengine.function.RuleFunction;
import io.github.leylaragg.letool.ruleengine.function.RuleFunctionFactory;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Spring Boot Starter 围绕完整表达式引擎建立自动配置边界。
 *
 * <p>Starter 负责绑定资源限制、收集宿主函数和接入诊断文案，不再把编译器或
 * 求值器作为可以单独替换的 Bean 发布。</p>
 */
@DisplayName("规则引擎 Spring Boot 自动配置")
class RuleEngineAutoConfigurationTest {

    private static final FactContract EMPTY_CONTRACT =
            FactContract.builder("starter-empty-v1").build();
    private static final TypeDescriptor INTEGER =
            TypeDescriptor.scalar(TypeKind.INTEGER, false);

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ExceptionAutoConfiguration.class,
                    RuleEngineAutoConfiguration.class));

    /** 默认上下文只发布完整引擎及其框架协作者。 */
    @Test
    @DisplayName("默认上下文提供唯一完整引擎")
    void defaultContextProvidesCompleteEngine() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ExpressionEngine.class);
            assertThat(context).hasSingleBean(DiagnosticMessageResolver.class);
            assertThat(context).hasSingleBean(RuleEngineProperties.class);
            assertThat(context).doesNotHaveBean("expressionCompiler");
            assertThat(context).doesNotHaveBean("expressionEvaluator");
        });
    }

    /** 规则资源贡献者保留独立 Bean 名，供异常模块统一聚合。 */
    @Test
    @DisplayName("规则诊断资源参与统一消息解析")
    void ruleMessageBundleIsPublished() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("ruleEngineMessageBundle");
            MessageBundleContributor contributor =
                    context.getBean("ruleEngineMessageBundle", MessageBundleContributor.class);
            assertThat(contributor.getBasenames())
                    .containsExactly("i18n/letool-rule-engine/messages");
        });
    }

    /** 总开关关闭后不留下半套规则引擎协作者。 */
    @Test
    @DisplayName("总开关关闭整套自动配置")
    void disabledPropertyBacksOffEntireAutoConfiguration() {
        contextRunner.withPropertyValues("letool.rule-engine.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(ExpressionEngine.class);
            assertThat(context).doesNotHaveBean(DiagnosticMessageResolver.class);
            assertThat(context).doesNotHaveBean(RuleEngineProperties.class);
            assertThat(context).doesNotHaveBean("ruleEngineMessageBundle");
        });
    }

    /** 应用可以提供一个已经完整构建的引擎，自动配置只对整个门面退让。 */
    @Test
    @DisplayName("用户完整引擎替换默认门面")
    void userEngineBacksOffDefaultEngine() {
        contextRunner.withUserConfiguration(CustomEngineConfiguration.class).run(context -> {
            ExpressionEngine expected = context.getBean("customEngine", ExpressionEngine.class);
            assertThat(context).hasSingleBean(ExpressionEngine.class);
            assertThat(context.getBean(ExpressionEngine.class)).isSameAs(expected);
            assertThat(context).hasSingleBean(DiagnosticMessageResolver.class);
        });
    }

    /** 用户诊断解析器可以独立替换，而不改变引擎语义。 */
    @Test
    @DisplayName("用户诊断解析器替换默认文案")
    void userDiagnosticResolverBacksOffDefaultResolver() {
        contextRunner.withUserConfiguration(CustomDiagnosticConfiguration.class).run(context -> {
            DiagnosticMessageResolver resolver =
                    context.getBean(DiagnosticMessageResolver.class);
            assertThat(resolver.resolve(
                    TestDiagnostics.unknownCharacter(), Locale.ROOT)).isEqualTo("custom");
            assertThat(context).hasSingleBean(ExpressionEngine.class);
        });
    }

    /** 共享函数和调用级工厂都要进入同一个引擎函数目录。 */
    @Test
    @DisplayName("宿主函数由完整引擎统一收集")
    void hostFunctionsAreRegisteredIntoEngineSnapshot() {
        contextRunner.withUserConfiguration(FunctionConfiguration.class).run(context -> {
            ExpressionEngine engine = context.getBean(ExpressionEngine.class);
            CompiledExpression expression = engine.compile(
                    "$ONE() + $TWO()", EMPTY_CONTRACT).requireCompiled();

            assertThat(engine.evaluate(
                    expression, RuleFacts.fromMap(Map.of()), EvaluationOptions.defaults())
                    .requireValue().toSafeJavaValue())
                    .isEqualTo(BigInteger.valueOf(3));
        });
    }

    /** 重复函数编码必须让应用启动失败，不能由 Bean 顺序决定覆盖者。 */
    @Test
    @DisplayName("重复函数编码阻止引擎启动")
    void duplicateFunctionCodesFailStartup() {
        contextRunner.withUserConfiguration(DuplicateFunctionConfiguration.class).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertThat(failure).hasRootCauseInstanceOf(RuleEngineException.class);
            assertThat(rootRuleEngineException(failure).getErrorCode())
                    .isSameAs(RuleEngineErrorCode.REGISTRATION_CONFLICT);
        });
    }

    /** 直接函数只能使用线程安全模型，调用级实例必须通过工厂创建。 */
    @Test
    @DisplayName("非线程安全直接函数阻止引擎启动")
    void unsafeDirectFunctionFailsStartup() {
        contextRunner.withUserConfiguration(UnsafeFunctionConfiguration.class).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertThat(failure).hasRootCauseInstanceOf(RuleEngineException.class);
            assertThat(rootRuleEngineException(failure).getErrorCode())
                    .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
        });
    }

    /** 宿主工厂异常不能把内部信息拼进对外错误消息。 */
    @Test
    @DisplayName("函数工厂异常在启动边界被净化")
    void hostileFactoryFailureDoesNotLeakMessage() {
        contextRunner.withUserConfiguration(HostileFactoryConfiguration.class).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertThat(failure).hasRootCauseInstanceOf(RuleEngineException.class);
            RuleEngineException root = rootRuleEngineException(failure);
            assertThat(root.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
            assertThat(root.getMessage()).doesNotContain("factory-secret");
        });
    }

    /** 属性绑定后的源码限制必须作用于真实编译主流程。 */
    @Test
    @DisplayName("源码限制进入完整引擎快照")
    void configuredSourceLimitIsEnforced() {
        contextRunner
                .withPropertyValues("letool.rule-engine.limits.max-source-length=4")
                .run(context -> assertThat(context.getBean(ExpressionEngine.class)
                        .compile("false", EMPTY_CONTRACT)
                        .diagnostics())
                        .extracting(diagnostic -> diagnostic.code())
                        .containsExactly(RuleDiagnosticCode.SOURCE_LIMIT_EXCEEDED));
    }

    /** 属性绑定后的函数调用限制必须约束实际求值。 */
    @Test
    @DisplayName("函数调用限制进入完整引擎快照")
    void configuredFunctionLimitIsEnforced() {
        contextRunner
                .withUserConfiguration(FunctionConfiguration.class)
                .withPropertyValues("letool.rule-engine.limits.max-function-calls=1")
                .run(context -> {
                    ExpressionEngine engine = context.getBean(ExpressionEngine.class);
                    CompiledExpression expression = engine.compile(
                            "$ONE() + $TWO()", EMPTY_CONTRACT).requireCompiled();
                    assertThat(engine.evaluate(
                            expression, RuleFacts.fromMap(Map.of()),
                            EvaluationOptions.defaults()).diagnostics())
                            .extracting(diagnostic -> diagnostic.code())
                            .containsExactly(RuleDiagnosticCode.FUNCTION_CALL_LIMIT_EXCEEDED);
                });
    }

    /** 沿异常链寻找框架根异常，便于断言自动配置失败原因。 */
    private static RuleEngineException rootRuleEngineException(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return (RuleEngineException) current;
    }

    /** 应用提供完整引擎的示例配置。 */
    @Configuration(proxyBeanMethods = false)
    static class CustomEngineConfiguration {
        @Bean
        ExpressionEngine customEngine() {
            return ExpressionEngine.builder().build();
        }
    }

    /** 应用提供诊断文案的示例配置。 */
    @Configuration(proxyBeanMethods = false)
    static class CustomDiagnosticConfiguration {
        @Bean
        DiagnosticMessageResolver customDiagnosticResolver() {
            return (diagnostic, locale) -> "custom";
        }
    }

    /** 同时注册共享函数与调用级函数工厂。 */
    @Configuration(proxyBeanMethods = false)
    static class FunctionConfiguration {
        @Bean
        RuleFunction oneFunction() {
            return new ConstantFunction("ONE", 1, FunctionThreading.THREAD_SAFE);
        }

        @Bean
        RuleFunctionFactory twoFunctionFactory() {
            ConstantFunction prototype =
                    new ConstantFunction("TWO", 2, FunctionThreading.INVOCATION_SCOPED);
            FunctionDescriptor descriptor = FunctionDescriptor.from(prototype);
            return new RuleFunctionFactory() {
                @Override
                public FunctionDescriptor descriptor() {
                    return descriptor;
                }

                @Override
                public RuleFunction create() {
                    return new ConstantFunction(
                            "TWO", 2, FunctionThreading.INVOCATION_SCOPED);
                }
            };
        }
    }

    /** 提供规范化后会冲突的两个函数编码。 */
    @Configuration(proxyBeanMethods = false)
    static class DuplicateFunctionConfiguration {
        @Bean
        RuleFunction lowerFunction() {
            return new ConstantFunction("same", 1, FunctionThreading.THREAD_SAFE);
        }

        @Bean
        RuleFunction upperFunction() {
            return new ConstantFunction("SAME", 2, FunctionThreading.THREAD_SAFE);
        }
    }

    /** 错误地把调用级函数作为共享 Bean 暴露。 */
    @Configuration(proxyBeanMethods = false)
    static class UnsafeFunctionConfiguration {
        @Bean
        RuleFunction unsafeFunction() {
            return new ConstantFunction("UNSAFE", 1, FunctionThreading.INVOCATION_SCOPED);
        }
    }

    /** 模拟在读取描述符时抛出宿主敏感异常的工厂。 */
    @Configuration(proxyBeanMethods = false)
    static class HostileFactoryConfiguration {
        @Bean
        RuleFunctionFactory hostileFactory() {
            return new RuleFunctionFactory() {
                @Override
                public FunctionDescriptor descriptor() {
                    throw new IllegalStateException("factory-secret");
                }

                @Override
                public RuleFunction create() {
                    return new ConstantFunction(
                            "UNREACHABLE", 1, FunctionThreading.INVOCATION_SCOPED);
                }
            };
        }
    }

    /** 为 Starter 测试提供无参数、固定整数结果的纯函数。 */
    private static final class ConstantFunction implements RuleFunction {
        private final String code;
        private final long value;
        private final FunctionThreading threading;

        /** 保存已经由测试场景决定的函数元数据。 */
        private ConstantFunction(String code, long value, FunctionThreading threading) {
            this.code = code;
            this.value = value;
            this.threading = threading;
        }

        /** {@inheritDoc} */
        @Override
        public String code() {
            return code;
        }

        /** {@inheritDoc} */
        @Override
        public String semanticVersion() {
            return "1";
        }

        /** {@inheritDoc} */
        @Override
        public FunctionSignature signature() {
            return FunctionSignature.empty();
        }

        /** {@inheritDoc} */
        @Override
        public TypeDescriptor returnType() {
            return INTEGER;
        }

        /** {@inheritDoc} */
        @Override
        public FunctionCharacteristics characteristics() {
            return FunctionCharacteristics.of(
                    FunctionDeterminism.DETERMINISTIC,
                    FunctionEffect.PURE,
                    threading);
        }

        /** {@inheritDoc} */
        @Override
        public FactValue execute(FunctionArguments arguments, FunctionContext context) {
            return FactValues.integer(value);
        }
    }

    /** 创建一个最小诊断，验证自定义解析器确实获得框架诊断对象。 */
    private static final class TestDiagnostics {
        private TestDiagnostics() {
        }

        /** 返回词法阶段的示例诊断。 */
        private static io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic
                unknownCharacter() {
            return new io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic(
                    RuleDiagnosticCode.UNKNOWN_CHARACTER,
                    io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticSeverity.ERROR,
                    io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase.LEXICAL,
                    0,
                    1,
                    List.of(),
                    null);
        }
    }
}
