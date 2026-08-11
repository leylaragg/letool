package com.github.leyland.letool.datastructure.chain;

import com.github.leyland.letool.datastructure.exception.DataStructureErrorCode;
import com.github.leyland.letool.datastructure.exception.DataStructureException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 决策链顺序、快照、安全异常和回调边界测试。
 */
class DecisionChainTest {

    /**
     * 验证决策链按顺序执行首个命中规则，并公开安全诊断信息。
     */
    @Test
    void shouldExecuteFirstMatchAndReportDiagnostics() {
        DecisionChain<Integer, String> chain = DecisionChain.<Integer, String>builder()
                .when(number -> number > 10, number -> "大于10")
                .when(number -> number > 5, number -> "大于5")
                .otherwise(number -> "默认")
                .build();

        assertEquals("大于10", chain.execute(15));
        assertEquals("大于5", chain.execute(7));
        assertEquals("默认", chain.execute(3));
        assertEquals(3, chain.size());
        assertTrue(chain.hasDefault());
    }

    /**
     * 验证已构建决策链与构建器后续规则变更隔离。
     */
    @Test
    void shouldIsolateBuiltChainFromBuilderChanges() {
        DecisionChainBuilder<Integer, String> builder = DecisionChain.<Integer, String>builder()
                .when(number -> number > 10, number -> "大于10");
        DecisionChain<Integer, String> firstChain = builder.build();
        builder.when(number -> number > 5, number -> "大于5");

        assertEquals(1, firstChain.size());
        assertFalse(firstChain.hasDefault());
        assertEquals(2, builder.build().size());
    }

    /**
     * 验证未命中时使用稳定错误码，并且异常消息不输出业务上下文。
     */
    @Test
    void shouldFailWithoutLeakingContextWhenNoRuleMatches() {
        DecisionChain<SecretContext, String> chain = DecisionChain
                .<SecretContext, String>builder()
                .when(context -> false, context -> "never")
                .build();

        DataStructureException exception = assertThrows(
                DataStructureException.class,
                () -> chain.execute(new SecretContext())
        );

        assertEquals(DataStructureErrorCode.DECISION_NOT_MATCHED, exception.getErrorCode());
        assertFalse(exception.getMessage().contains("SECRET-CONTEXT"));
    }

    /**
     * 验证构建器非法状态统一进入稳定参数错误。
     */
    @Test
    void shouldRejectInvalidBuilderStateWithStableError() {
        DataStructureException empty = assertThrows(
                DataStructureException.class,
                () -> DecisionChain.builder().build()
        );
        DataStructureException missingCondition = assertThrows(
                DataStructureException.class,
                () -> DecisionChain.<Integer, String>builder().when(null, value -> "value")
        );
        DataStructureException duplicateDefault = assertThrows(
                DataStructureException.class,
                () -> DecisionChain.<Integer, String>builder()
                        .otherwise(value -> "first")
                        .otherwise(value -> "second")
        );
        DataStructureException unreachableRule = assertThrows(
                DataStructureException.class,
                () -> DecisionChain.<Integer, String>builder()
                        .otherwise(value -> "default")
                        .when(value -> true, value -> "unreachable")
        );

        assertEquals(DataStructureErrorCode.INVALID_ARGUMENT, empty.getErrorCode());
        assertEquals(DataStructureErrorCode.INVALID_ARGUMENT, missingCondition.getErrorCode());
        assertEquals(DataStructureErrorCode.INVALID_ARGUMENT, duplicateDefault.getErrorCode());
        assertEquals(DataStructureErrorCode.INVALID_ARGUMENT, unreachableRule.getErrorCode());
    }

    /**
     * 验证用户动作异常保持原始类型和实例，不被注册框架无差别包装。
     */
    @Test
    void shouldPropagateCallbackFailureWithoutWrapping() {
        BusinessFailure failure = new BusinessFailure();
        DecisionChain<Integer, String> chain = DecisionChain.<Integer, String>builder()
                .otherwise(value -> {
                    throw failure;
                })
                .build();

        BusinessFailure actual = assertThrows(BusinessFailure.class, () -> chain.execute(1));

        assertSame(failure, actual);
    }

    /**
     * 用于验证异常消息不会调用或输出业务上下文。
     */
    private static final class SecretContext {

        /**
         * 返回模拟敏感内容。
         *
         * @return 模拟敏感上下文
         */
        @Override
        public String toString() {
            return "SECRET-CONTEXT";
        }
    }

    /**
     * 模拟业务回调主动抛出的异常。
     */
    private static final class BusinessFailure extends RuntimeException {

        /** 序列化版本。 */
        private static final long serialVersionUID = 1L;
    }
}
