package io.github.leylaragg.letool.ai.core;

import io.github.leylaragg.letool.ai.exception.AiErrorCode;
import io.github.leylaragg.letool.ai.exception.AiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AiModelRegistry} 不可变模型路由行为测试。
 */
@DisplayName("AiModelRegistry 模型注册表测试")
class AiModelRegistryTest {

    /**
     * 验证每类只有一个候选且未显式配置默认名称时会自动选择该模型。
     */
    @Test
    @DisplayName("单模型自动成为默认模型")
    void shouldSelectSingleModelsAsDefaults() {
        ChatModel chatModel = new TestChatModel();
        EmbeddingModel embeddingModel = new TestEmbeddingModel();
        AiModelRegistry registry = new AiModelRegistry(
                Map.of("chat", chatModel),
                Map.of("embedding", embeddingModel),
                null,
                null);

        assertSame(chatModel, registry.chatModel());
        assertSame(embeddingModel, registry.embeddingModel());
    }

    /**
     * 验证多模型场景会按去除首尾空白后的显式默认 Bean 名称精确选择模型。
     */
    @Test
    @DisplayName("多模型按显式默认 Bean 名称选择")
    void shouldSelectExplicitDefaultsByExactBeanName() {
        ChatModel primaryChat = new TestChatModel();
        EmbeddingModel primaryEmbedding = new TestEmbeddingModel();
        AiModelRegistry registry = new AiModelRegistry(
                Map.of("backupChat", new TestChatModel(), "primaryChat", primaryChat),
                Map.of("backupEmbedding", new TestEmbeddingModel(),
                        "primaryEmbedding", primaryEmbedding),
                "  primaryChat  ",
                "  primaryEmbedding  ");

        assertSame(primaryChat, registry.chatModel());
        assertSame(primaryChat, registry.chatModel("primaryChat"));
        assertSame(primaryEmbedding, registry.embeddingModel());
        assertSame(primaryEmbedding, registry.embeddingModel("primaryEmbedding"));
    }

    /**
     * 验证默认模型名称会去除 Unicode 全角空格后再精确匹配 Bean 名称。
     */
    @Test
    @DisplayName("默认模型名称支持去除 Unicode 全角空格")
    void shouldStripUnicodeWhitespaceFromDefaultModelNames() {
        ChatModel primaryChat = new TestChatModel();
        EmbeddingModel primaryEmbedding = new TestEmbeddingModel();

        AiModelRegistry registry = assertDoesNotThrow(() -> new AiModelRegistry(
                Map.of("backupChat", new TestChatModel(), "primaryChat", primaryChat),
                Map.of("backupEmbedding", new TestEmbeddingModel(),
                        "primaryEmbedding", primaryEmbedding),
                "\u3000primaryChat\u3000",
                "\u3000primaryEmbedding\u3000"));

        assertSame(primaryChat, registry.chatModel());
        assertSame(primaryEmbedding, registry.embeddingModel());
    }

    /**
     * 验证多 ChatModel 未配置默认名称时在构造阶段失败，并稳定列出排序后的候选名称。
     */
    @Test
    @DisplayName("多 ChatModel 未配置默认名称时拒绝歧义")
    void shouldRejectAmbiguousChatModelsWithSortedCandidates() {
        AiException exception = assertThrows(
                AiException.class,
                () -> new AiModelRegistry(
                        Map.of("zeta", new TestChatModel(), "alpha", new TestChatModel()),
                        Map.of(),
                        null,
                        null));

        assertSame(AiErrorCode.CONFIGURATION_INVALID, exception.getErrorCode());
        assertArrayEquals(
                new Object[]{"ChatModel 存在多个候选，必须配置默认模型，候选名称：[alpha, zeta]"},
                exception.getMessageArgs());
    }

    /**
     * 验证多 EmbeddingModel 未配置默认名称时在构造阶段失败，并稳定列出排序后的候选名称。
     */
    @Test
    @DisplayName("多 EmbeddingModel 未配置默认名称时拒绝歧义")
    void shouldRejectAmbiguousEmbeddingModelsWithSortedCandidates() {
        AiException exception = assertThrows(
                AiException.class,
                () -> new AiModelRegistry(
                        Map.of(),
                        Map.of("zeta", new TestEmbeddingModel(),
                                "alpha", new TestEmbeddingModel()),
                        null,
                        null));

        assertSame(AiErrorCode.CONFIGURATION_INVALID, exception.getErrorCode());
        assertArrayEquals(
                new Object[]{"EmbeddingModel 存在多个候选，必须配置默认模型，候选名称：[alpha, zeta]"},
                exception.getMessageArgs());
    }

    /**
     * 验证非空 ChatModel 集合中的显式默认名称不存在时在构造阶段失败。
     */
    @Test
    @DisplayName("拒绝不存在的默认 ChatModel 名称")
    void shouldRejectUnknownDefaultChatModel() {
        AiException exception = assertThrows(
                AiException.class,
                () -> new AiModelRegistry(
                        Map.of("alpha", new TestChatModel()),
                        Map.of(),
                        "missing",
                        null));

        assertSame(AiErrorCode.CONFIGURATION_INVALID, exception.getErrorCode());
        assertArrayEquals(
                new Object[]{"默认 ChatModel 不存在：missing，候选名称：[alpha]"},
                exception.getMessageArgs());
    }

    /**
     * 验证非空 EmbeddingModel 集合中的显式默认名称不存在时在构造阶段失败。
     */
    @Test
    @DisplayName("拒绝不存在的默认 EmbeddingModel 名称")
    void shouldRejectUnknownDefaultEmbeddingModel() {
        AiException exception = assertThrows(
                AiException.class,
                () -> new AiModelRegistry(
                        Map.of(),
                        Map.of("alpha", new TestEmbeddingModel()),
                        null,
                        "missing"));

        assertSame(AiErrorCode.CONFIGURATION_INVALID, exception.getErrorCode());
        assertArrayEquals(
                new Object[]{"默认 EmbeddingModel 不存在：missing，候选名称：[alpha]"},
                exception.getMessageArgs());
    }

    /**
     * 验证没有模型时允许构造，直到查询默认模型才抛出对应的结构化缺失异常。
     */
    @Test
    @DisplayName("空注册表查询默认模型时抛结构化缺失异常")
    void shouldReportMissingDefaultModelsFromEmptyRegistry() {
        AiModelRegistry registry = emptyRegistry(null, null);

        AiException chatException = assertThrows(AiException.class, registry::chatModel);
        AiException embeddingException = assertThrows(
                AiException.class,
                registry::embeddingModel);

        assertSame(AiErrorCode.CHAT_MODEL_NOT_FOUND, chatException.getErrorCode());
        assertArrayEquals(new Object[]{"默认模型"}, chatException.getMessageArgs());
        assertSame(AiErrorCode.EMBEDDING_MODEL_NOT_FOUND, embeddingException.getErrorCode());
        assertArrayEquals(new Object[]{"默认模型"}, embeddingException.getMessageArgs());
    }

    /**
     * 验证空注册表按名称查询时抛出对应的结构化缺失异常并保留请求名称。
     */
    @Test
    @DisplayName("空注册表按名称查询时保留缺失模型名称")
    void shouldReportRequestedNamesFromEmptyRegistry() {
        AiModelRegistry registry = emptyRegistry(null, null);

        AiException chatException = assertThrows(
                AiException.class,
                () -> registry.chatModel("missingChat"));
        AiException embeddingException = assertThrows(
                AiException.class,
                () -> registry.embeddingModel("missingEmbedding"));

        assertSame(AiErrorCode.CHAT_MODEL_NOT_FOUND, chatException.getErrorCode());
        assertArrayEquals(new Object[]{"missingChat"}, chatException.getMessageArgs());
        assertSame(AiErrorCode.EMBEDDING_MODEL_NOT_FOUND, embeddingException.getErrorCode());
        assertArrayEquals(new Object[]{"missingEmbedding"}, embeddingException.getMessageArgs());
    }

    /**
     * 验证某类集合为空时可保留显式默认名称，并在实际默认查询时报告该名称缺失。
     */
    @Test
    @DisplayName("空集合的显式默认名称延迟到查询阶段校验")
    void shouldDeferConfiguredDefaultsForEmptyModelTypes() {
        AiModelRegistry registry = emptyRegistry("  configuredChat  ", "  configuredEmbedding  ");

        AiException chatException = assertThrows(AiException.class, registry::chatModel);
        AiException embeddingException = assertThrows(
                AiException.class,
                registry::embeddingModel);

        assertSame(AiErrorCode.CHAT_MODEL_NOT_FOUND, chatException.getErrorCode());
        assertArrayEquals(new Object[]{"configuredChat"}, chatException.getMessageArgs());
        assertSame(AiErrorCode.EMBEDDING_MODEL_NOT_FOUND, embeddingException.getErrorCode());
        assertArrayEquals(new Object[]{"configuredEmbedding"}, embeddingException.getMessageArgs());
    }

    /**
     * 验证名称集合是构造时生成的、按名称排序且不可修改的快照。
     */
    @Test
    @DisplayName("模型名称返回排序且不可修改的快照")
    void shouldExposeSortedUnmodifiableNameSnapshots() {
        Map<String, ChatModel> chatModels = new LinkedHashMap<>();
        chatModels.put("zeta", new TestChatModel());
        chatModels.put("alpha", new TestChatModel());
        Map<String, EmbeddingModel> embeddingModels = new LinkedHashMap<>();
        embeddingModels.put("omega", new TestEmbeddingModel());
        embeddingModels.put("beta", new TestEmbeddingModel());
        AiModelRegistry registry = new AiModelRegistry(
                chatModels,
                embeddingModels,
                "alpha",
                "beta");

        Set<String> chatNames = registry.chatModelNames();
        Set<String> embeddingNames = registry.embeddingModelNames();
        chatModels.put("laterChat", new TestChatModel());
        embeddingModels.put("laterEmbedding", new TestEmbeddingModel());

        assertEquals(Set.of("alpha", "zeta"), chatNames);
        assertEquals("[alpha, zeta]", chatNames.toString());
        assertEquals(Set.of("beta", "omega"), embeddingNames);
        assertEquals("[beta, omega]", embeddingNames.toString());
        assertThrows(UnsupportedOperationException.class, () -> chatNames.remove("alpha"));
        assertThrows(UnsupportedOperationException.class, () -> embeddingNames.add("later"));
        assertFalse(registry.containsChatModel("laterChat"));
        assertFalse(registry.containsEmbeddingModel("laterEmbedding"));
    }

    /**
     * 验证存在性判断使用 Bean 名称精确匹配，并安全处理空值与空白值。
     */
    @Test
    @DisplayName("模型存在性判断精确匹配且安全处理空名称")
    void shouldCheckModelNamesExactly() {
        AiModelRegistry registry = new AiModelRegistry(
                Map.of("chat", new TestChatModel()),
                Map.of("embedding", new TestEmbeddingModel()),
                null,
                null);

        assertTrue(registry.containsChatModel("chat"));
        assertFalse(registry.containsChatModel(" chat "));
        assertFalse(registry.containsChatModel(""));
        assertFalse(registry.containsChatModel("  "));
        assertFalse(registry.containsChatModel(null));
        assertTrue(registry.containsEmbeddingModel("embedding"));
        assertFalse(registry.containsEmbeddingModel(" embedding "));
        assertFalse(registry.containsEmbeddingModel(""));
        assertFalse(registry.containsEmbeddingModel("  "));
        assertFalse(registry.containsEmbeddingModel(null));
    }

    /**
     * 验证构造器将空 Map 输入映射为结构化配置异常。
     */
    @Test
    @DisplayName("拒绝 null 模型 Map")
    void shouldRejectNullMaps() {
        assertConfigurationInvalid(() -> new AiModelRegistry(null, Map.of(), null, null));
        assertConfigurationInvalid(() -> new AiModelRegistry(Map.of(), null, null, null));
    }

    /**
     * 验证构造器将空白 Bean 名称映射为结构化配置异常。
     */
    @Test
    @DisplayName("拒绝空白模型 Bean 名称")
    void shouldRejectBlankKeys() {
        Map<String, ChatModel> chatModels = new HashMap<>();
        chatModels.put("  ", new TestChatModel());
        Map<String, EmbeddingModel> embeddingModels = new HashMap<>();
        embeddingModels.put("", new TestEmbeddingModel());

        assertConfigurationInvalid(
                () -> new AiModelRegistry(chatModels, Map.of(), null, null));
        assertConfigurationInvalid(
                () -> new AiModelRegistry(Map.of(), embeddingModels, null, null));
    }

    /**
     * 验证构造器将空模型实例映射为结构化配置异常。
     */
    @Test
    @DisplayName("拒绝 null 模型实例")
    void shouldRejectNullValues() {
        Map<String, ChatModel> chatModels = new HashMap<>();
        chatModels.put("chat", null);
        Map<String, EmbeddingModel> embeddingModels = new HashMap<>();
        embeddingModels.put("embedding", null);

        assertConfigurationInvalid(
                () -> new AiModelRegistry(chatModels, Map.of(), null, null));
        assertConfigurationInvalid(
                () -> new AiModelRegistry(Map.of(), embeddingModels, null, null));
    }

    /**
     * 创建不包含任何模型的注册表。
     *
     * @param defaultChatModel 默认 ChatModel Bean 名称
     * @param defaultEmbeddingModel 默认 EmbeddingModel Bean 名称
     * @return 空模型注册表
     */
    private static AiModelRegistry emptyRegistry(
            String defaultChatModel,
            String defaultEmbeddingModel) {
        return new AiModelRegistry(
                Map.of(),
                Map.of(),
                defaultChatModel,
                defaultEmbeddingModel);
    }

    /**
     * 断言指定构造操作抛出配置不合法异常。
     *
     * @param operation 待执行的构造操作
     */
    private static void assertConfigurationInvalid(Runnable operation) {
        AiException exception = assertThrows(AiException.class, operation::run);
        assertSame(AiErrorCode.CONFIGURATION_INVALID, exception.getErrorCode());
    }

    /**
     * 仅用于验证注册表路由的最小 ChatModel 实现。
     */
    private static final class TestChatModel implements ChatModel {

        /**
         * 返回空响应；注册表测试不会触发真实模型调用。
         *
         * @param prompt 对话提示词
         * @return 空响应
         */
        @Override
        public ChatResponse call(Prompt prompt) {
            return null;
        }
    }

    /**
     * 仅用于验证注册表路由的最小 EmbeddingModel 实现。
     */
    private static final class TestEmbeddingModel implements EmbeddingModel {

        /**
         * 返回空响应；注册表测试不会触发真实嵌入调用。
         *
         * @param request 嵌入请求
         * @return 空响应
         */
        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return null;
        }

        /**
         * 返回空向量；注册表测试不会触发真实嵌入调用。
         *
         * @param document 待嵌入文档
         * @return 空向量
         */
        @Override
        public float[] embed(Document document) {
            return new float[0];
        }
    }
}
