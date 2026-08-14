package com.github.leyland.letool.ruleengine.type;

import com.github.leyland.letool.ruleengine.api.EngineLimits;
import com.github.leyland.letool.ruleengine.compile.CompiledExpression;
import com.github.leyland.letool.ruleengine.compile.CompilationResult;
import com.github.leyland.letool.ruleengine.compile.DefaultExpressionCompiler;
import com.github.leyland.letool.ruleengine.compile.ExpressionCompiler;
import com.github.leyland.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import com.github.leyland.letool.ruleengine.function.FunctionRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionTypeAnalyzerTest {

    private final ExpressionCompiler compiler = new DefaultExpressionCompiler();
    private final FactContract contract = FactContract.builder("types-1")
            .path("integer", TypeDescriptor.scalar(TypeKind.INTEGER, false))
            .path("decimal", TypeDescriptor.scalar(TypeKind.DECIMAL, true))
            .path("flag", TypeDescriptor.scalar(TypeKind.BOOLEAN, false))
            .path("text", TypeDescriptor.scalar(TypeKind.STRING, true))
            .path("date", TypeDescriptor.scalar(TypeKind.DATE, true))
            .path("dateTime", TypeDescriptor.scalar(TypeKind.DATE_TIME, false))
            .path("instant", TypeDescriptor.scalar(TypeKind.INSTANT, false))
            .build();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'1 + 2', INTEGER",
            "'1 + 2.5', DECIMAL",
            "'4 % 3', INTEGER",
            "'-${decimal}', DECIMAL",
            "'${integer} > ${decimal}', BOOLEAN",
            "'${date} <= DATE ''2026-08-13''', BOOLEAN",
            "'${text} IS NULL', BOOLEAN",
            "'${integer} IN (1, 2.0)', BOOLEAN",
            "'${decimal} BETWEEN 1 AND 10.5', BOOLEAN",
            "'NOT ${flag}', BOOLEAN"
            ,"'${dateTime} < DATETIME ''2026-08-13T10:30:00''', BOOLEAN"
            ,"'${instant} <= INSTANT ''2026-08-13T02:30:00Z''', BOOLEAN"
            ,"'${flag} OR false', BOOLEAN"
            ,"'${integer} NOT IN (2, 3)', BOOLEAN"
            ,"'${text} IS NOT NULL', BOOLEAN"
            ,"'${decimal} % 2', DECIMAL"
    })
    @DisplayName("兼容运算应推导确定结果类型")
    void shouldInferCompatibleOperators(String source, TypeKind expected) {
        CompilationResult<CompiledExpression> result = compile(source);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.requireCompiled().resultType().kind()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "拒绝 {0}")
    @CsvSource({
            "'${text} + ''x'''",
            "'${flag} + 1'",
            "'${date} < ${dateTime}'",
            "'${instant} = ${dateTime}'",
            "'${integer} AND ${flag}'",
            "'NOT ${integer}'",
            "'${text} IN (1, 2)'",
            "'${date} BETWEEN DATE ''2026-01-01'' AND DATETIME ''2026-12-31T00:00:00'''",
            "'${text} IN (''ok'', 2)'",
            "'null + 1'"
    })
    @DisplayName("不兼容运算应报告单个根因类型诊断")
    void shouldRejectIncompatibleOperators(String source) {
        CompilationResult<CompiledExpression> result = compile(source);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.OPERATOR_TYPE_MISMATCH);
    }

    @ParameterizedTest(name = "NULL语义 {0} 成功={1}")
    @CsvSource({
            "'null = null', true",
            "'${text} = null', true",
            "'${integer} = null', true",
            "'null != 1', true",
            "'null = ''text''', true",
            "'null != true', true",
            "'null = DATE ''2026-08-13''', true",
            "'null IS NULL', true"
    })
    @DisplayName("NULL 允许与任意标量做相等比较及显式空值判断")
    void shouldApplyExplicitNullRules(String source, boolean successful) {
        assertThat(compile(source).isSuccessful()).isEqualTo(successful);
    }

    @ParameterizedTest(name = "NULL不得参与 {0}")
    @CsvSource({
            "'null > 1'",
            "'null + 1'",
            "'null IN (1, 2)'",
            "'null BETWEEN 1 AND 2'",
            "'null AND true'"
    })
    @DisplayName("NULL相等语义不得放宽排序算术集合范围或逻辑运算")
    void shouldRejectNullOutsideEqualityAndNullChecks(String source) {
        assertThat(compile(source).isSuccessful()).isFalse();
    }

    @ParameterizedTest(name = "禁止字符串隐式转换: {0}")
    @CsvSource({
            "'''1'' = 1'",
            "'''1'' = 1.0'",
            "'''true'' = true'",
            "'''2026-08-13'' = DATE ''2026-08-13'''",
            "'''2026-08-13T10:30:00'' = DATETIME ''2026-08-13T10:30:00'''",
            "'''2026-08-13T02:30:00Z'' = INSTANT ''2026-08-13T02:30:00Z'''"
    })
    @DisplayName("STRING不得隐式转换为数值布尔或时间")
    void shouldRejectStringCoercion(String source) {
        CompilationResult<CompiledExpression> result = compile(source);

        assertThat(result.diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.OPERATOR_TYPE_MISMATCH);
    }

    private CompilationResult<CompiledExpression> compile(String source) {
        return compiler.compile(source, contract,
                FunctionRegistry.builder().build(), EngineLimits.defaults());
    }
}
