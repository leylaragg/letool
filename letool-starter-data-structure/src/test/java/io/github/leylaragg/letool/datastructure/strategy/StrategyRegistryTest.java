package io.github.leylaragg.letool.datastructure.strategy;

import io.github.leylaragg.letool.datastructure.exception.DataStructureErrorCode;
import io.github.leylaragg.letool.datastructure.exception.DataStructureException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不可变策略注册表的关键契约测试。
 */
class StrategyRegistryTest {

    /**
     * 验证注册表保持注册顺序、不可修改，并与后续构建器变更隔离。
     */
    @Test
    void shouldBuildOrderedImmutableSnapshotIsolatedFromBuilder() {
        StrategyRegistry.Builder<String, MessageStrategy> builder = StrategyRegistry.builder();
        MessageStrategy sms = message -> "sms:" + message;
        MessageStrategy mail = message -> "mail:" + message;

        StrategyRegistry<String, MessageStrategy> registry = builder
                .register("sms", sms)
                .build();
        builder.register("mail", mail);

        assertEquals(List.of("sms"), new ArrayList<>(registry.keys()));
        assertTrue(registry.contains("sms"));
        assertFalse(registry.contains("mail"));
        assertThrows(UnsupportedOperationException.class, () -> registry.asMap().put("mail", mail));
    }

    /**
     * 验证重复注册不会静默覆盖已有策略。
     */
    @Test
    void shouldRejectDuplicateRegistration() {
        StrategyRegistry.Builder<String, MessageStrategy> builder = StrategyRegistry.builder();
        builder.register("sms", message -> message);

        DataStructureException exception = assertThrows(
                DataStructureException.class,
                () -> builder.register("sms", message -> "other:" + message)
        );

        assertEquals(DataStructureErrorCode.DUPLICATE_STRATEGY_KEY, exception.getErrorCode());
    }

    /**
     * 验证策略覆盖必须显式执行，并且只能覆盖已存在键。
     */
    @Test
    void shouldReplaceOnlyExistingStrategy() {
        MessageStrategy replacement = message -> "replacement:" + message;
        StrategyRegistry.Builder<String, MessageStrategy> builder = StrategyRegistry
                .<String, MessageStrategy>builder()
                .register("sms", message -> "sms:" + message)
                .replace("sms", replacement);

        assertEquals(replacement, builder.build().getRequired("sms"));

        DataStructureException exception = assertThrows(
                DataStructureException.class,
                () -> builder.replace("mail", replacement)
        );
        assertEquals(DataStructureErrorCode.STRATEGY_NOT_FOUND, exception.getErrorCode());
    }

    /**
     * 验证可选查询和严格查询对缺失策略提供不同语义。
     */
    @Test
    void shouldProvideOptionalAndStrictLookup() {
        StrategyRegistry<String, MessageStrategy> registry = StrategyRegistry
                .<String, MessageStrategy>builder()
                .register("sms", message -> message)
                .build();

        assertTrue(registry.find("unknown").isEmpty());
        assertTrue(registry.find(null).isEmpty());

        DataStructureException exception = assertThrows(
                DataStructureException.class,
                () -> registry.getRequired("unknown")
        );
        assertEquals(DataStructureErrorCode.STRATEGY_NOT_FOUND, exception.getErrorCode());
    }

    /**
     * 验证批量注册失败时不保留前半段条目，避免构建器进入半更新状态。
     */
    @Test
    void shouldKeepBulkRegistrationAtomicWhenConflictOccurs() {
        StrategyRegistry.Builder<String, MessageStrategy> builder = StrategyRegistry
                .<String, MessageStrategy>builder()
                .register("sms", message -> "sms:" + message);
        Map<String, MessageStrategy> additions = new LinkedHashMap<>();
        additions.put("mail", message -> "mail:" + message);
        additions.put("sms", message -> "duplicate:" + message);

        assertThrows(DataStructureException.class, () -> builder.registerAll(additions));

        assertEquals(List.of("sms"), new ArrayList<>(builder.build().keys()));
    }

    /**
     * 测试使用的消息策略。
     */
    @FunctionalInterface
    private interface MessageStrategy {

        /**
         * 处理一条消息。
         *
         * @param message 原始消息
         * @return 处理结果
         */
        String handle(String message);
    }
}
