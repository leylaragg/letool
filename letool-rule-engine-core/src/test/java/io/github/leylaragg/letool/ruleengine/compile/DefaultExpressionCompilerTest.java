package io.github.leylaragg.letool.ruleengine.compile;

import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.diagnostic.DiagnosticPhase;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnosticCode;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.fact.FactValues;
import io.github.leylaragg.letool.ruleengine.function.FunctionArguments;
import io.github.leylaragg.letool.ruleengine.function.FunctionCharacteristics;
import io.github.leylaragg.letool.ruleengine.function.FunctionContext;
import io.github.leylaragg.letool.ruleengine.function.FunctionDescriptor;
import io.github.leylaragg.letool.ruleengine.function.FunctionDeterminism;
import io.github.leylaragg.letool.ruleengine.function.FunctionEffect;
import io.github.leylaragg.letool.ruleengine.function.FunctionParameter;
import io.github.leylaragg.letool.ruleengine.function.FunctionRegistry;
import io.github.leylaragg.letool.ruleengine.function.FunctionSignature;
import io.github.leylaragg.letool.ruleengine.function.FunctionThreading;
import io.github.leylaragg.letool.ruleengine.function.RuleFunction;
import io.github.leylaragg.letool.ruleengine.function.RuleFunctionFactory;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultExpressionCompilerTest {

    private final ExpressionCompiler compiler = new DefaultExpressionCompiler();
    private final TypeDescriptor integer = TypeDescriptor.scalar(TypeKind.INTEGER, false);
    private final TypeDescriptor decimal = TypeDescriptor.scalar(TypeKind.DECIMAL, true);
    private final FactContract contract = FactContract.builder("facts-1")
            .path("amount", decimal)
            .path("count", integer)
            .path("active", TypeDescriptor.scalar(TypeKind.BOOLEAN, false))
            .path("created", TypeDescriptor.scalar(TypeKind.DATE, true))
            .build();

    @Test
    @DisplayName("有效表达式应生成类型化依赖和布尔结果")
    void shouldCompileValidExpression() {
        CompilationResult<CompiledExpression> result = compiler.compile(
                "${amount} >= 100 AND ${active}", contract,
                registry(new FixedFunction("ROUND", FunctionSignature.of(
                        FunctionParameter.required("value", decimal),
                        FunctionParameter.optional("scale", integer)), decimal)),
                EngineLimits.defaults());

        assertThat(result.isSuccessful()).isTrue();
        CompiledExpression compiled = result.requireCompiled();
        assertThat(compiled.resultType())
                .isEqualTo(TypeDescriptor.scalar(TypeKind.BOOLEAN, false));
        assertThat(compiled.dependencies().values())
                .extracting(dependency -> dependency.path().toString())
                .containsExactly("amount", "active");
        assertThat(compiled.dependencies().values().get(0).expectedType()).isEqualTo(decimal);
        assertThat(compiled.functionDependencies()).isEmpty();
    }

    @Test
    @DisplayName("编译应一次返回互不依赖的多个语义错误")
    void shouldAggregateIndependentSemanticErrors() {
        CompilationResult<CompiledExpression> result = compiler.compile(
                "${missingA} > 1 AND $UNKNOWN(${missingB})",
                contract, FunctionRegistry.builder().build(), EngineLimits.defaults());

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.UNKNOWN_FACT_PATH,
                        RuleDiagnosticCode.UNKNOWN_FUNCTION,
                        RuleDiagnosticCode.UNKNOWN_FACT_PATH);
        assertThat(result.diagnostics()).allSatisfy(diagnostic -> {
            assertThat(diagnostic.phase()).isEqualTo(DiagnosticPhase.SEMANTIC);
            assertThat(diagnostic.startPosition()).isGreaterThanOrEqualTo(0);
            assertThat(diagnostic.endPosition()).isGreaterThan(diagnostic.startPosition());
        });
    }

    @Test
    @DisplayName("非法路径和非法时间文本应变为语义诊断")
    void shouldReportInvalidPathAndTemporalLiteral() {
        CompilationResult<CompiledExpression> path = compiler.compile(
                "${items[*].price} = 1", contract,
                FunctionRegistry.builder().build(), EngineLimits.defaults());
        CompilationResult<CompiledExpression> temporal = compiler.compile(
                "DATE '2026-02-30' = DATE '2026-03-01'", contract,
                FunctionRegistry.builder().build(), EngineLimits.defaults());

        assertThat(path.diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.INVALID_FACT_PATH);
        assertThat(temporal.diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.INVALID_TEMPORAL_LITERAL);
        assertThat(temporal.diagnostics().get(0).phase()).isEqualTo(DiagnosticPhase.SEMANTIC);
    }

    @Test
    @DisplayName("函数参数数量、类型、可选和可变参数应按签名精确检查")
    void shouldValidateFunctionArguments() {
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(new FixedFunction("OPT", FunctionSignature.of(
                        FunctionParameter.required("first", integer),
                        FunctionParameter.optional("second", decimal)), decimal))
                .register(new FixedFunction("VAR", FunctionSignature.of(
                        FunctionParameter.required("first", integer),
                        FunctionParameter.varargs("rest", integer)), integer))
                .build();

        assertThat(compiler.compile("$OPT(1)", contract, registry,
                EngineLimits.defaults()).isSuccessful()).isTrue();
        assertThat(compiler.compile("$VAR(1, 2, 3)", contract, registry,
                EngineLimits.defaults()).isSuccessful()).isTrue();
        assertThat(compiler.compile("$OPT()", contract, registry,
                EngineLimits.defaults()).diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.ARGUMENT_COUNT_MISMATCH);
        assertThat(compiler.compile("$VAR(1, 'bad')", contract, registry,
                EngineLimits.defaults()).diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.ARGUMENT_TYPE_MISMATCH);
        assertThat(compiler.compile("$VAR(${amount})", contract, registry,
                EngineLimits.defaults()).diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.ARGUMENT_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("编译期不得在注册探测之后再次实例化调用级函数")
    void shouldNotInstantiateInvocationFunctionDuringCompilation() {
        AtomicInteger creations = new AtomicInteger();
        FunctionDescriptor descriptor = FunctionDescriptor.of(
                "SCOPED", "1", FunctionSignature.empty(), integer,
                FunctionCharacteristics.of(FunctionDeterminism.DETERMINISTIC,
                        FunctionEffect.PURE, FunctionThreading.INVOCATION_SCOPED));
        RuleFunctionFactory factory = new RuleFunctionFactory() {
            @Override
            public FunctionDescriptor descriptor() {
                return descriptor;
            }

            @Override
            public RuleFunction create() {
                creations.incrementAndGet();
                return new FixedFunction("SCOPED", FunctionSignature.empty(), integer,
                        FunctionThreading.INVOCATION_SCOPED);
            }
        };
        FunctionRegistry registry = FunctionRegistry.builder().register(factory).build();
        assertThat(creations).hasValue(1);

        CompilationResult<CompiledExpression> result = compiler.compile(
                "$SCOPED() = 1", contract, registry, EngineLimits.defaults());

        assertThat(result.isSuccessful()).isTrue();
        assertThat(creations).hasValue(1);
        assertThat(result.requireCompiled().functionDependencies()).containsExactly("SCOPED");
    }

    @Test
    @DisplayName("多个函数依赖应按首次出现顺序去重")
    void shouldOrderAndDeduplicateFunctionDependencies() {
        FunctionRegistry registry = FunctionRegistry.builder()
                .register(new FixedFunction("A", FunctionSignature.empty(), integer))
                .register(new FixedFunction("B", FunctionSignature.empty(), integer)).build();

        CompiledExpression compiled = compiler.compile("$A() + $B() + $A()", contract,
                registry, EngineLimits.defaults()).requireCompiled();

        assertThat(compiled.functionDependencies()).containsExactly("A", "B");
    }

    @Test
    @DisplayName("超长未知路径和函数应返回有界诊断而非抛出异常")
    void shouldBoundUnknownIdentifierDiagnostics() {
        String longPath = "a".repeat(300);
        String longFunction = "A".repeat(300);

        CompilationResult<CompiledExpression> path = compiler.compile("${" + longPath + "} = 1",
                contract, FunctionRegistry.builder().build(), EngineLimits.defaults());
        CompilationResult<CompiledExpression> function = compiler.compile("$" + longFunction + "()",
                contract, FunctionRegistry.builder().build(), EngineLimits.defaults());

        assertThat(path.diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.UNKNOWN_FACT_PATH);
        assertThat(function.diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.UNKNOWN_FUNCTION);
        assertThat(path.diagnostics().get(0).arguments().get(0).toString()).hasSizeLessThanOrEqualTo(256);
        assertThat(function.diagnostics().get(0).arguments().get(0).toString()).hasSizeLessThanOrEqualTo(256);
    }

    @Test
    @DisplayName("源码、Token 和 AST 超限应返回失败结果而非抛出异常")
    void shouldReturnDiagnosticsForCompilationLimits() {
        EngineLimits sourceLimit = new EngineLimits(4, 100, 20, 10, 10, 10);
        EngineLimits tokenLimit = new EngineLimits(100, 3, 20, 10, 10, 10);
        EngineLimits depthLimit = new EngineLimits(100, 100, 2, 10, 10, 10);

        assertThat(compiler.compile("12345", contract, FunctionRegistry.builder().build(),
                sourceLimit).diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.SOURCE_LIMIT_EXCEEDED);
        assertThat(compiler.compile("1 + 2", contract, FunctionRegistry.builder().build(),
                tokenLimit).diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.TOKEN_LIMIT_EXCEEDED);
        assertThat(compiler.compile("1 + 2 * 3", contract, FunctionRegistry.builder().build(),
                depthLimit).diagnostics()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(RuleDiagnosticCode.AST_DEPTH_EXCEEDED);
    }

    private static FunctionRegistry registry(RuleFunction function) {
        return FunctionRegistry.builder().register(function).build();
    }

    /** 测试使用的固定元数据函数。 */
    private static final class FixedFunction implements RuleFunction {
        private final String code;
        private final FunctionSignature signature;
        private final TypeDescriptor returnType;
        private final FunctionThreading threading;

        private FixedFunction(String code, FunctionSignature signature, TypeDescriptor returnType) {
            this(code, signature, returnType, FunctionThreading.THREAD_SAFE);
        }

        private FixedFunction(String code, FunctionSignature signature,
                TypeDescriptor returnType, FunctionThreading threading) {
            this.code = code;
            this.signature = signature;
            this.returnType = returnType;
            this.threading = threading;
        }

        @Override public String code() { return code; }
        @Override public String semanticVersion() { return "1"; }
        @Override public FunctionSignature signature() { return signature; }
        @Override public TypeDescriptor returnType() { return returnType; }
        @Override public FunctionCharacteristics characteristics() {
            return FunctionCharacteristics.of(FunctionDeterminism.DETERMINISTIC,
                    FunctionEffect.PURE, threading);
        }
        @Override public FactValue execute(FunctionArguments arguments, FunctionContext context) {
            return FactValues.integer(1);
        }
    }
}
