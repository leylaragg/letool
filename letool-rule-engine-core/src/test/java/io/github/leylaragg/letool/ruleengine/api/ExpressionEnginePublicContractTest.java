package io.github.leylaragg.letool.ruleengine.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 固定 3.0 表达式引擎向宿主公开的完整门面边界。
 *
 * <p>这些测试刻意从外部调用者视角检查 API，避免内部流水线再次被误当成可独立
 * 替换的框架扩展点。</p>
 */
@DisplayName("表达式引擎 3.0 公共契约")
class ExpressionEnginePublicContractTest {

    /** 构建器只允许配置完整引擎需要的函数与资源限制。 */
    @Test
    @DisplayName("构建器不再开放局部编译器和求值器")
    void builderDoesNotExposePartialPipelineReplacement() {
        assertThat(Arrays.stream(ExpressionEngineBuilder.class.getMethods())
                .map(Method::getName))
                .doesNotContain("compiler", "evaluator");
    }

    /** 每个引擎快照都要公开可用于缓存校验的完整语义身份。 */
    @Test
    @DisplayName("引擎公开不可变执行模型")
    void engineExposesExecutionModel() {
        assertThat(Arrays.stream(ExpressionEngine.class.getMethods())
                .map(Method::getName))
                .contains("executionModel");
    }

    /** 旧的局部扩展接口在大版本升级后不再属于可加载的公共契约。 */
    @Test
    @DisplayName("Compiler 和 Evaluator 不再是公开 SPI")
    void partialPipelineInterfacesAreRemoved() {
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Class.forName(
                        "io.github.leylaragg.letool.ruleengine.compile.ExpressionCompiler"));
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Class.forName(
                        "io.github.leylaragg.letool.ruleengine.evaluate.ExpressionEvaluator"));
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Class.forName(
                        "io.github.leylaragg.letool.ruleengine.compile.DefaultExpressionCompiler"));
        assertThatExceptionOfType(ClassNotFoundException.class)
                .isThrownBy(() -> Class.forName(
                        "io.github.leylaragg.letool.ruleengine.evaluate.DefaultExpressionEvaluator"));
    }

    /** 编译产物只公开语义元数据，不把内核 AST 作为宿主调用契约。 */
    @Test
    @DisplayName("编译产物不公开 AST 和构造器")
    void compiledExpressionKeepsRuntimeStructureInternal() {
        assertThat(Arrays.stream(CompiledExpression.class.getMethods())
                .map(Method::getName))
                .doesNotContain("ast");
        assertThat(Arrays.stream(CompiledExpression.class.getDeclaredConstructors())
                .allMatch(constructor -> !Modifier.isPublic(constructor.getModifiers())))
                .isTrue();
    }
}
