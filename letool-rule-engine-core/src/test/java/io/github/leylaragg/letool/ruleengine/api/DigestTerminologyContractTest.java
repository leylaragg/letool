package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeCompatibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 固定 3.0 使用内容摘要表达缓存一致性，不再沿用容易误解的指纹术语。
 */
@DisplayName("规则引擎摘要术语")
class DigestTerminologyContractTest {

    /** 公开值对象只提供语义明确的摘要访问器。 */
    @Test
    @DisplayName("公开 API 不再包含 fingerprint 命名")
    void publicContractsUseDigestTerminology() {
        assertMethods(CompiledExpression.class, "artifactDigest", "environmentDigest",
                "factContractDigest");
        assertMethods(FactContract.class, "contractDigest");
        assertMethods(FunctionRegistry.class, "catalogDigest");

        assertThat(Arrays.stream(TypeCompatibility.class.getFields())
                .map(field -> field.getName()))
                .contains("TYPE_CATALOG_DIGEST")
                .noneMatch(name -> name.contains("FINGERPRINT"));
    }

    /** 编译产物直接保存创建它的完整环境摘要。 */
    @Test
    @DisplayName("编译产物绑定完整执行环境")
    void compiledExpressionBindsCompleteExecutionEnvironment() {
        ExpressionEngine engine = ExpressionEngine.builder().build();
        CompiledExpression expression = engine.compile(
                "true", FactContract.builder("digest-v1").build()).requireCompiled();

        assertThat(expression.environmentDigest())
                .isEqualTo(engine.executionModel().environmentDigest());
        assertThat(expression.artifactDigest()).matches("[0-9a-f]{64}");
    }

    /** 同时断言新方法存在、旧方法已经从 3.0 契约移除。 */
    private static void assertMethods(Class<?> type, String... expected) {
        assertThat(Arrays.stream(type.getMethods()).map(Method::getName))
                .contains(expected)
                .noneMatch(name -> name.toLowerCase().contains("fingerprint"));
    }
}
