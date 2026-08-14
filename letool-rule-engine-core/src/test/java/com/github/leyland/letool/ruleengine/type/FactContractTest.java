package com.github.leyland.letool.ruleengine.type;

import com.github.leyland.letool.ruleengine.exception.RuleEngineException;
import com.github.leyland.letool.ruleengine.exception.RuleEngineErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 类型描述与最小事实契约测试。
 */
class FactContractTest {

    /**
     * 验证类型描述工厂只允许合法组合，并提供稳定规范字符串。
     */
    @Test
    void shouldCreateOnlyValidTypeDescriptors() {
        TypeDescriptor unknown = TypeDescriptor.scalar(TypeKind.UNKNOWN, true);
        TypeDescriptor integer = TypeDescriptor.scalar(TypeKind.INTEGER, false);
        TypeDescriptor array = TypeDescriptor.array(integer, true);
        TypeDescriptor object = TypeDescriptor.object(false);

        assertThat(unknown.toCanonicalString()).isEqualTo("UNKNOWN?");
        assertThat(integer.toCanonicalString()).isEqualTo("INTEGER!");
        assertThat(array.toCanonicalString()).isEqualTo("ARRAY<INTEGER!>?");
        assertThat(object.toCanonicalString()).isEqualTo("OBJECT!");
        assertThat(TypeDescriptor.scalar(TypeKind.INTEGER, false)).isEqualTo(integer);

        assertInvalid(() -> TypeDescriptor.scalar(null, false));
        assertInvalid(() -> TypeDescriptor.scalar(TypeKind.ARRAY, false));
        assertInvalid(() -> TypeDescriptor.scalar(TypeKind.OBJECT, false));
        assertInvalid(() -> TypeDescriptor.array(null, false));
    }

    /**
     * 验证事实契约可按普通或插值路径查询并保持构建后不可变。
     */
    @Test
    void shouldBuildAndQueryImmutableContract() {
        FactContract.Builder builder = FactContract.builder("v1")
                .path("customer.age", TypeDescriptor.scalar(TypeKind.INTEGER, false));
        FactContract contract = builder.build();
        builder.path("customer.name", TypeDescriptor.scalar(TypeKind.STRING, true));

        assertThat(contract.version()).isEqualTo("v1");
        assertThat(contract.descriptor("${customer.age}"))
                .contains(TypeDescriptor.scalar(TypeKind.INTEGER, false));
        assertThat(contract.descriptor("customer.name")).isEmpty();
        assertThat(contract.descriptors()).isUnmodifiable();
        assertThat(contract.fingerprint()).matches("[0-9a-f]{64}");
        assertThat(FactContract.builder("empty").build().descriptors()).isEmpty();
    }

    /**
     * 验证指纹不依赖注册顺序，但会响应版本、路径和类型变化。
     */
    @Test
    void shouldProduceDeterministicSemanticFingerprint() {
        TypeDescriptor integer = TypeDescriptor.scalar(TypeKind.INTEGER, false);
        TypeDescriptor string = TypeDescriptor.scalar(TypeKind.STRING, true);
        FactContract first = FactContract.builder("v1")
                .path("customer.age", integer)
                .path("customer.name", string)
                .build();
        FactContract reordered = FactContract.builder("v1")
                .path("customer.name", string)
                .path("customer.age", integer)
                .build();

        assertThat(first.fingerprint()).isEqualTo(reordered.fingerprint());
        assertThat(first.fingerprint())
                .isEqualTo("44fb5153c328be7051871a5f4814cd344cf111e151919bc620bb602f688a7cb7");
        assertThat(first).isEqualTo(reordered);
        assertThat(first.hashCode()).isEqualTo(reordered.hashCode());
        assertThat(FactContract.builder("v2")
                .path("customer.age", integer)
                .path("customer.name", string)
                .build().fingerprint()).isNotEqualTo(first.fingerprint());
        assertThat(first).isNotEqualTo(FactContract.builder("v2")
                .path("customer.age", integer).path("customer.name", string).build());
        assertThat(FactContract.builder("v1")
                .path("customer.age", TypeDescriptor.scalar(TypeKind.DECIMAL, false))
                .path("customer.name", string)
                .build().fingerprint()).isNotEqualTo(first.fingerprint());
        assertThat(FactContract.builder("v1")
                .path("customer.height", integer)
                .path("customer.name", string)
                .build().fingerprint()).isNotEqualTo(first.fingerprint());
    }

    /**
     * 验证空版本、重复路径以及任一方向的父子路径冲突都会被拒绝。
     */
    @Test
    void shouldRejectInvalidContractDefinitions() {
        TypeDescriptor object = TypeDescriptor.object(false);
        TypeDescriptor integer = TypeDescriptor.scalar(TypeKind.INTEGER, false);

        assertInvalid(() -> FactContract.builder(null));
        assertInvalid(() -> FactContract.builder(" "));
        assertInvalid(() -> FactContract.builder("v1\u0000bad"));
        assertInvalid(() -> FactContract.builder("v1\nbad"));
        assertInvalid(() -> FactContract.builder("v1 "));
        assertInvalid(() -> FactContract.builder("x".repeat(129)));
        assertInvalid(() -> FactContract.builder("v1")
                .path("customer.age", integer)
                .path("${customer.age}", integer));
        assertInvalid(() -> FactContract.builder("v1")
                .path("customer", object)
                .path("customer.age", integer));
        assertInvalid(() -> FactContract.builder("v1")
                .path("items[0].price", integer)
                .path("items", TypeDescriptor.array(object, false)));
    }

    /**
     * 验证数组类型嵌套最多允许六十四层，超出后快速拒绝。
     */
    @Test
    void shouldBoundArrayTypeNestingAtSixtyFourLevels() {
        TypeDescriptor depth64 = TypeDescriptor.scalar(TypeKind.STRING, false);
        for (int depth = 0; depth < 64; depth++) {
            depth64 = TypeDescriptor.array(depth64, false);
        }
        TypeDescriptor accepted = depth64;

        assertThat(accepted.toCanonicalString()).startsWith("ARRAY<ARRAY<");
        assertThat(accepted).isEqualTo(accepted);
        assertThatCode(accepted::hashCode).doesNotThrowAnyException();
        assertInvalid(() -> TypeDescriptor.array(accepted, false));
    }

    /**
     * 验证多层数组规范串按内层到外层保留每一层可空性。
     */
    @Test
    void shouldPreserveNestedArrayNullabilityInCanonicalStringAndFingerprint() {
        TypeDescriptor string = TypeDescriptor.scalar(TypeKind.STRING, false);
        TypeDescriptor innerNullable = TypeDescriptor.array(string, true);
        TypeDescriptor outerRequired = TypeDescriptor.array(innerNullable, false);
        TypeDescriptor innerRequired = TypeDescriptor.array(string, false);
        TypeDescriptor outerNullable = TypeDescriptor.array(innerRequired, true);

        assertThat(outerRequired.toCanonicalString())
                .isEqualTo("ARRAY<ARRAY<STRING!>?>!");
        assertThat(outerNullable.toCanonicalString())
                .isEqualTo("ARRAY<ARRAY<STRING!>!>?");
        assertThat(outerRequired).isNotEqualTo(outerNullable);
        assertThat(outerRequired.hashCode()).isNotEqualTo(outerNullable.hashCode());
        assertThat(FactContract.builder("v1").path("matrix", outerRequired).build().fingerprint())
                .isNotEqualTo(FactContract.builder("v1")
                        .path("matrix", outerNullable).build().fingerprint());
    }

    /**
     * 断言操作抛出统一的非法参数错误码。
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
