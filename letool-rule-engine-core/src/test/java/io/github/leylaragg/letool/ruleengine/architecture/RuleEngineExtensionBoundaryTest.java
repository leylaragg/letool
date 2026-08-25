package io.github.leylaragg.letool.ruleengine.architecture;

import io.github.leylaragg.letool.ruleengine.api.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.compile.DependencyCoverage;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证无关业务域只通过事实契约接入同一套标量求值语义。
 *
 * <p>测试不要求 core 认识任何宿主模型，用两个差异明显的事实形状防止未来把
 * 某个接入方的字段、上下文或转换规则固化进框架入口。</p>
 */
@DisplayName("规则引擎宿主扩展边界")
class RuleEngineExtensionBoundaryTest {

    /** 临床与保险事实应共享同一引擎，只由各自契约决定可读路径。 */
    @Test
    @DisplayName("无关宿主通过事实契约独立编译和求值")
    void unrelatedHostsUseTheSameScalarEngine() {
        ExpressionEngine engine = ExpressionEngine.builder().build();
        FactContract clinicalContract = FactContract.builder("clinical-v1")
                .path("subject.age", TypeDescriptor.scalar(TypeKind.INTEGER, false))
                .build();
        FactContract insuranceContract = FactContract.builder("insurance-v1")
                .path("policy.amount", TypeDescriptor.scalar(TypeKind.DECIMAL, false))
                .build();

        CompiledExpression adult = engine.compile(
                "${subject.age} >= 18", clinicalContract).requireCompiled();
        CompiledExpression highValue = engine.compile(
                "${policy.amount} > 1000.00", insuranceContract).requireCompiled();

        assertThat(engine.evaluate(
                adult,
                RuleFacts.fromMap(Map.of("subject", Map.of("age", 21))),
                EvaluationOptions.defaults()).requireBoolean()).isTrue();
        assertThat(engine.evaluate(
                highValue,
                RuleFacts.fromMap(Map.of(
                        "policy", Map.of("amount", new BigDecimal("1200.00")))),
                EvaluationOptions.defaults()).requireBoolean()).isTrue();
        assertThat(adult.dependencyCoverage()).isEqualTo(DependencyCoverage.COMPLETE);
        assertThat(highValue.dependencyCoverage()).isEqualTo(DependencyCoverage.COMPLETE);
    }
}
