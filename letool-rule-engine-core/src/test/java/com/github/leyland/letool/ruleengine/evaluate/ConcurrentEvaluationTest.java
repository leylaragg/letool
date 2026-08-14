package com.github.leyland.letool.ruleengine.evaluate;

import com.github.leyland.letool.ruleengine.api.EngineLimits;
import com.github.leyland.letool.ruleengine.compile.CompiledExpression;
import com.github.leyland.letool.ruleengine.compile.DefaultExpressionCompiler;
import com.github.leyland.letool.ruleengine.fact.FactValue;
import com.github.leyland.letool.ruleengine.fact.FactValues;
import com.github.leyland.letool.ruleengine.fact.RuleFacts;
import com.github.leyland.letool.ruleengine.function.*;
import com.github.leyland.letool.ruleengine.type.FactContract;
import com.github.leyland.letool.ruleengine.type.TypeDescriptor;
import com.github.leyland.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentEvaluationTest {

    @Test
    @DisplayName("同一编译产物、注册表和求值器并发一千次不应串扰会话")
    void shouldEvaluateConcurrentlyWithoutSessionLeakage() throws Exception {
        AtomicInteger creations = new AtomicInteger();
        FunctionDescriptor descriptor = FunctionDescriptor.of(
                "SCOPED", "1", FunctionSignature.empty(),
                TypeDescriptor.scalar(TypeKind.INTEGER, false),
                FunctionCharacteristics.of(FunctionDeterminism.DETERMINISTIC,
                        FunctionEffect.PURE, FunctionThreading.INVOCATION_SCOPED));
        FunctionRegistry registry = FunctionRegistry.builder().register(new RuleFunctionFactory() {
            @Override public FunctionDescriptor descriptor() { return descriptor; }
            @Override public RuleFunction create() {
                int instance = creations.incrementAndGet();
                return new ScopedFunction(descriptor, instance);
            }
        }).build();
        FactContract contract = FactContract.builder("empty").build();
        CompiledExpression expression = new DefaultExpressionCompiler().compile(
                "$SCOPED() > 0", contract, registry, EngineLimits.defaults()).requireCompiled();
        ExpressionEvaluator evaluator = new DefaultExpressionEvaluator();
        RuleFacts facts = RuleFacts.fromMap(Map.of());
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> tasks = new ArrayList<>();
            for (int index = 0; index < 1_000; index++) {
                tasks.add(() -> evaluator.evaluate(expression, facts, registry,
                        EvaluationOptions.defaults()).requireBoolean());
            }

            List<Future<Boolean>> futures = executor.invokeAll(tasks);

            assertThat(futures).allSatisfy(future -> assertThat(future.get()).isTrue());
            assertThat(creations).hasValue(1_001);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 每次调用独立创建的测试函数。 */
    private static final class ScopedFunction implements RuleFunction {
        private final FunctionDescriptor descriptor;
        private final int instance;

        private ScopedFunction(FunctionDescriptor descriptor, int instance) {
            this.descriptor = descriptor;
            this.instance = instance;
        }

        @Override public String code() { return descriptor.code(); }
        @Override public String semanticVersion() { return descriptor.semanticVersion(); }
        @Override public FunctionSignature signature() { return descriptor.signature(); }
        @Override public TypeDescriptor returnType() { return descriptor.returnType(); }
        @Override public FunctionCharacteristics characteristics() { return descriptor.characteristics(); }
        @Override public FactValue execute(FunctionArguments arguments, FunctionContext context) {
            return FactValues.integer(BigInteger.valueOf(instance));
        }
    }
}
