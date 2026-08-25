package io.github.leylaragg.letool.ruleengine.api;

import io.github.leylaragg.letool.ruleengine.compile.CompilationRequest;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationRequest;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证编译和求值请求只携带一次调用真正需要的不可变输入。
 */
@DisplayName("表达式引擎请求对象")
class ExpressionEngineRequestTest {

    /** 编译请求完整保留源码和事实类型契约。 */
    @Test
    @DisplayName("编译请求保存显式输入")
    void compilationRequestKeepsExplicitInputs() {
        FactContract contract = FactContract.builder("request-v1").build();

        CompilationRequest request = new CompilationRequest("true", contract);

        assertThat(request.source()).isEqualTo("true");
        assertThat(request.factContract()).isSameAs(contract);
    }

    /** 求值请求不允许附带宿主上下文或可变能力。 */
    @Test
    @DisplayName("求值请求只包含产物事实和选项")
    void evaluationRequestKeepsOnlyEvaluationInputs() {
        ExpressionEngine engine = ExpressionEngine.builder().build();
        var compiled = engine.compile(
                "true", FactContract.builder("request-v1").build()).requireCompiled();
        RuleFacts facts = RuleFacts.fromMap(Map.of());
        EvaluationOptions options = EvaluationOptions.defaults();

        EvaluationRequest request = new EvaluationRequest(compiled, facts, options);

        assertThat(request.expression()).isSameAs(compiled);
        assertThat(request.facts()).isSameAs(facts);
        assertThat(request.options()).isSameAs(options);
    }

    /** 空输入在进入核心流水线之前就被拒绝。 */
    @Test
    @DisplayName("请求拒绝空输入")
    void requestsRejectNullInputs() {
        FactContract contract = FactContract.builder("request-v1").build();

        assertThatThrownBy(() -> new CompilationRequest(null, contract))
                .isInstanceOf(RuleEngineException.class);
        assertThatThrownBy(() -> new CompilationRequest("true", null))
                .isInstanceOf(RuleEngineException.class);
    }
}
