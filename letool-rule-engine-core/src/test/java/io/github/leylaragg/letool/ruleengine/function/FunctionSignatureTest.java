package io.github.leylaragg.letool.ruleengine.function;

import io.github.leylaragg.letool.ruleengine.exception.RuleEngineErrorCode;
import io.github.leylaragg.letool.ruleengine.exception.RuleEngineException;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.AbstractList;
import java.util.Iterator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 函数参数和签名不变量测试。
 */
class FunctionSignatureTest {

    private static final TypeDescriptor STRING = TypeDescriptor.scalar(TypeKind.STRING, false);
    private static final TypeDescriptor INTEGER = TypeDescriptor.scalar(TypeKind.INTEGER, false);

    /**
     * 验证固定必填参数只接受精确参数数量。
     */
    @Test
    void shouldDescribeFixedRequiredParameters() {
        FunctionSignature signature = FunctionSignature.of(
                FunctionParameter.required("value", STRING),
                FunctionParameter.required("radix", INTEGER));

        assertThat(signature.parameters()).extracting(FunctionParameter::name)
                .containsExactly("value", "radix");
        assertThat(signature.minimumArgumentCount()).isEqualTo(2);
        assertThat(signature.maximumArgumentCount()).isEqualTo(2);
        assertThat(signature.acceptsArgumentCount(1)).isFalse();
        assertThat(signature.acceptsArgumentCount(2)).isTrue();
        assertThat(signature.parameters()).isUnmodifiable();
    }

    /**
     * 验证尾部可选参数扩大合法参数数量范围。
     */
    @Test
    void shouldSupportTrailingOptionalParameters() {
        FunctionSignature signature = FunctionSignature.of(List.of(
                FunctionParameter.required("value", STRING),
                FunctionParameter.optional("locale", STRING),
                FunctionParameter.optional("scale", INTEGER)));

        assertThat(signature.minimumArgumentCount()).isEqualTo(1);
        assertThat(signature.maximumArgumentCount()).isEqualTo(3);
        assertThat(signature.acceptsArgumentCount(0)).isFalse();
        assertThat(signature.acceptsArgumentCount(1)).isTrue();
        assertThat(signature.acceptsArgumentCount(3)).isTrue();
        assertThat(signature.acceptsArgumentCount(4)).isFalse();
    }

    /**
     * 验证唯一尾部可变参数接受任意数量的附加参数。
     */
    @Test
    void shouldSupportSingleTrailingVarargsParameter() {
        FunctionSignature signature = FunctionSignature.of(
                FunctionParameter.required("separator", STRING),
                FunctionParameter.varargs("values", STRING));

        assertThat(signature.minimumArgumentCount()).isEqualTo(1);
        assertThat(signature.maximumArgumentCount()).isEqualTo(256);
        assertThat(signature.hasVarargs()).isTrue();
        assertThat(signature.acceptsArgumentCount(1)).isTrue();
        assertThat(signature.acceptsArgumentCount(256)).isTrue();
        assertThat(signature.acceptsArgumentCount(257)).isFalse();
    }

    /**
     * 验证零参数签名是显式、不可变且可调用的契约。
     */
    @Test
    void shouldSupportExplicitEmptySignature() {
        FunctionSignature signature = FunctionSignature.empty();

        assertThat(signature.parameters()).isEmpty();
        assertThat(signature.minimumArgumentCount()).isZero();
        assertThat(signature.maximumArgumentCount()).isZero();
        assertThat(signature.acceptsArgumentCount(0)).isTrue();
    }

    /**
     * 验证参数名、类型和参数序列的非法组合均被拒绝。
     */
    @Test
    void shouldRejectInvalidParameterDefinitions() {
        assertInvalid(() -> FunctionParameter.required(null, STRING));
        assertInvalid(() -> FunctionParameter.required(" ", STRING));
        assertInvalid(() -> FunctionParameter.required("a.b", STRING));
        assertInvalid(() -> FunctionParameter.required("value", null));
        assertInvalid(() -> FunctionSignature.of((FunctionParameter[]) null));
        assertInvalid(() -> FunctionSignature.of((List<FunctionParameter>) null));
        assertInvalid(() -> FunctionSignature.of(List.of(
                FunctionParameter.required("value", STRING),
                FunctionParameter.required("value", INTEGER))));
        assertInvalid(() -> FunctionSignature.of(List.of(
                FunctionParameter.optional("locale", STRING),
                FunctionParameter.required("value", STRING))));
        assertInvalid(() -> FunctionSignature.of(List.of(
                FunctionParameter.varargs("values", STRING),
                FunctionParameter.optional("locale", STRING))));
        assertInvalid(() -> FunctionSignature.of(List.of(
                FunctionParameter.varargs("first", STRING),
                FunctionParameter.varargs("second", STRING))));
    }

    /**
     * 验证负数参数数量不会被签名接受。
     */
    @Test
    void shouldRejectNegativeArgumentCount() {
        FunctionSignature signature = FunctionSignature.empty();

        assertInvalid(() -> signature.acceptsArgumentCount(-1));
    }

    /**
     * 验证签名最多保存二百五十六个参数。
     */
    @Test
    void shouldBoundSignatureAtTwoHundredFiftySixParameters() {
        List<FunctionParameter> parameters = new java.util.ArrayList<>();
        for (int index = 0; index < 257; index++) {
            parameters.add(FunctionParameter.optional("p" + index, STRING));
        }

        assertThat(FunctionSignature.of(parameters.subList(0, 256)).parameters()).hasSize(256);
        assertInvalid(() -> FunctionSignature.of(parameters));
        assertInvalid(() -> FunctionSignature.of(parameters.toArray(FunctionParameter[]::new)));
    }

    /**
     * 验证恶意参数列表的迭代异常会被净化为无原因非法参数错误。
     */
    @Test
    void shouldSanitizeHostileSignatureListIterationFailure() {
        List<FunctionParameter> hostile = new AbstractList<>() {
            @Override public FunctionParameter get(int index) { return FunctionParameter.required("p", STRING); }
            @Override public int size() { return 1; }
            @Override
            public Iterator<FunctionParameter> iterator() {
                throw new IllegalStateException("secret-signature-list");
            }
        };

        assertThatThrownBy(() -> FunctionSignature.of(hostile))
                .isInstanceOfSatisfying(RuleEngineException.class, exception -> {
                    assertThat(exception.getErrorCode()).isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT);
                    assertThat(exception.getCause()).isNull();
                    assertThat(exception.getMessage()).doesNotContain("secret-signature-list");
                });

        List<FunctionParameter> hostileSize = new AbstractList<>() {
            @Override public FunctionParameter get(int index) { return FunctionParameter.required("p", STRING); }
            @Override public int size() { throw new IllegalStateException("secret-signature-size"); }
            @Override public Iterator<FunctionParameter> iterator() {
                return List.of(FunctionParameter.required("p", STRING)).iterator();
            }
        };
        assertThat(FunctionSignature.of(hostileSize).parameters()).hasSize(1);
    }

    /**
     * 断言操作抛出统一非法参数错误。
     *
     * @param operation 待执行操作
     */
    private static void assertInvalid(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(RuleEngineException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(RuleEngineErrorCode.INVALID_ARGUMENT));
    }
}
