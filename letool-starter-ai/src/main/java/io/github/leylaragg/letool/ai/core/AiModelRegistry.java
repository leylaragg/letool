package io.github.leylaragg.letool.ai.core;

import io.github.leylaragg.letool.ai.exception.AiErrorCode;
import io.github.leylaragg.letool.ai.exception.AiException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * Spring AI 模型不可变注册表。
 *
 * <p>注册表以 Spring Bean 名称作为稳定路由名称，在构造阶段完成输入校验、排序复制和
 * 默认模型选择。调用方只能获取模型实例和名称快照，无法修改注册表内部状态。</p>
 */
public final class AiModelRegistry {

    /** 按 Bean 名称排序且不可修改的 ChatModel 映射。 */
    private final NavigableMap<String, ChatModel> chatModels;

    /** 按 Bean 名称排序且不可修改的 EmbeddingModel 映射。 */
    private final NavigableMap<String, EmbeddingModel> embeddingModels;

    /** 按 Bean 名称排序且不可修改的 ChatModel 名称快照。 */
    private final Set<String> chatModelNames;

    /** 按 Bean 名称排序且不可修改的 EmbeddingModel 名称快照。 */
    private final Set<String> embeddingModelNames;

    /** 默认 ChatModel Bean 名称；没有可用默认项时为 {@code null}。 */
    private final String defaultChatModel;

    /** 默认 EmbeddingModel Bean 名称；没有可用默认项时为 {@code null}。 */
    private final String defaultEmbeddingModel;

    /**
     * 创建不可变模型注册表。
     *
     * <p>非空模型集合会在构造阶段验证默认模型。只有一个候选且未配置默认名称时自动选择；
     * 多个候选且未配置默认名称时拒绝歧义。某类模型集合为空时允许构造，缺失错误延迟到
     * 实际查询阶段抛出。</p>
     *
     * @param chatModels 以 Spring Bean 名称为键的 ChatModel 集合
     * @param embeddingModels 以 Spring Bean 名称为键的 EmbeddingModel 集合
     * @param defaultChatModel 显式默认 ChatModel Bean 名称；未配置时可为 {@code null} 或空白
     * @param defaultEmbeddingModel 显式默认 EmbeddingModel Bean 名称；未配置时可为 {@code null} 或空白
     * @throws AiException 模型映射不合法、默认模型不存在或多候选存在歧义时抛出
     */
    public AiModelRegistry(
            Map<String, ChatModel> chatModels,
            Map<String, EmbeddingModel> embeddingModels,
            String defaultChatModel,
            String defaultEmbeddingModel) {
        this.chatModels = immutableSortedModels(chatModels, "ChatModel");
        this.embeddingModels = immutableSortedModels(embeddingModels, "EmbeddingModel");
        this.chatModelNames = immutableNames(this.chatModels);
        this.embeddingModelNames = immutableNames(this.embeddingModels);
        this.defaultChatModel = resolveDefaultName(
                "ChatModel",
                this.chatModels,
                this.chatModelNames,
                defaultChatModel);
        this.defaultEmbeddingModel = resolveDefaultName(
                "EmbeddingModel",
                this.embeddingModels,
                this.embeddingModelNames,
                defaultEmbeddingModel);
    }

    /**
     * 获取默认 ChatModel。
     *
     * @return 默认 ChatModel 实例
     * @throws AiException 没有可用默认 ChatModel 时抛出
     */
    public ChatModel chatModel() {
        return requiredChatModel(defaultChatModel, defaultModelDisplayName(defaultChatModel));
    }

    /**
     * 按 Spring Bean 名称获取 ChatModel。
     *
     * @param name ChatModel Bean 名称，使用精确匹配
     * @return 对应的 ChatModel 实例
     * @throws AiException 指定名称不存在时抛出
     */
    public ChatModel chatModel(String name) {
        return requiredChatModel(name, requestedNameDisplay(name));
    }

    /**
     * 获取默认 EmbeddingModel。
     *
     * @return 默认 EmbeddingModel 实例
     * @throws AiException 没有可用默认 EmbeddingModel 时抛出
     */
    public EmbeddingModel embeddingModel() {
        return requiredEmbeddingModel(
                defaultEmbeddingModel,
                defaultModelDisplayName(defaultEmbeddingModel));
    }

    /**
     * 按 Spring Bean 名称获取 EmbeddingModel。
     *
     * @param name EmbeddingModel Bean 名称，使用精确匹配
     * @return 对应的 EmbeddingModel 实例
     * @throws AiException 指定名称不存在时抛出
     */
    public EmbeddingModel embeddingModel(String name) {
        return requiredEmbeddingModel(name, requestedNameDisplay(name));
    }

    /**
     * 获取全部 ChatModel Bean 名称。
     *
     * @return 按名称排序且不可修改的构造时快照
     */
    public Set<String> chatModelNames() {
        return chatModelNames;
    }

    /**
     * 获取全部 EmbeddingModel Bean 名称。
     *
     * @return 按名称排序且不可修改的构造时快照
     */
    public Set<String> embeddingModelNames() {
        return embeddingModelNames;
    }

    /**
     * 获取已解析的默认对话模型 Bean 名称。
     *
     * <p>该包级方法仅供同包调用门面按名称定位客户端，避免同一模型实例绑定多个
     * Bean 名称时通过对象身份反推名称产生歧义。</p>
     *
     * @return 默认对话模型 Bean 名称；没有可用默认项时返回 {@code null}
     */
    String defaultChatModelName() {
        return defaultChatModel;
    }

    /**
     * 判断是否注册了指定 ChatModel Bean 名称。
     *
     * @param name ChatModel Bean 名称，使用精确匹配
     * @return 名称非空白且已注册时返回 {@code true}
     */
    public boolean containsChatModel(String name) {
        return isNonBlank(name) && chatModels.containsKey(name);
    }

    /**
     * 判断是否注册了指定 EmbeddingModel Bean 名称。
     *
     * @param name EmbeddingModel Bean 名称，使用精确匹配
     * @return 名称非空白且已注册时返回 {@code true}
     */
    public boolean containsEmbeddingModel(String name) {
        return isNonBlank(name) && embeddingModels.containsKey(name);
    }

    /**
     * 获取必需的 ChatModel，并将缺失情况映射为结构化异常。
     *
     * @param name 用于查找的 Bean 名称
     * @param displayName 缺失异常中使用的稳定显示名称
     * @return 已注册的 ChatModel 实例
     * @throws AiException 指定名称不存在时抛出
     */
    private ChatModel requiredChatModel(String name, String displayName) {
        ChatModel model = name == null ? null : chatModels.get(name);
        if (model == null) {
            throw AiException.of(AiErrorCode.CHAT_MODEL_NOT_FOUND, displayName);
        }
        return model;
    }

    /**
     * 获取必需的 EmbeddingModel，并将缺失情况映射为结构化异常。
     *
     * @param name 用于查找的 Bean 名称
     * @param displayName 缺失异常中使用的稳定显示名称
     * @return 已注册的 EmbeddingModel 实例
     * @throws AiException 指定名称不存在时抛出
     */
    private EmbeddingModel requiredEmbeddingModel(String name, String displayName) {
        EmbeddingModel model = name == null ? null : embeddingModels.get(name);
        if (model == null) {
            throw AiException.of(AiErrorCode.EMBEDDING_MODEL_NOT_FOUND, displayName);
        }
        return model;
    }

    /**
     * 校验、排序并复制模型映射。
     *
     * @param source 调用方提供的模型映射
     * @param modelType 用于配置异常的模型类型名称
     * @param <T> Spring AI 模型类型
     * @return 按 Bean 名称排序且不可修改的映射副本
     * @throws AiException 映射为空、名称为空白或模型实例为空时抛出
     */
    private static <T> NavigableMap<String, T> immutableSortedModels(
            Map<String, T> source,
            String modelType) {
        if (source == null) {
            throw configurationInvalid(modelType + " 映射不能为 null");
        }

        TreeMap<String, T> sortedModels = new TreeMap<>();
        for (Map.Entry<String, T> entry : source.entrySet()) {
            String name = entry.getKey();
            if (!isNonBlank(name)) {
                throw configurationInvalid(modelType + " Bean 名称不能为空白");
            }
            if (entry.getValue() == null) {
                throw configurationInvalid(modelType + " Bean [" + name + "] 对应的模型不能为 null");
            }
            sortedModels.put(name, entry.getValue());
        }
        return Collections.unmodifiableNavigableMap(sortedModels);
    }

    /**
     * 从已排序模型映射创建不可修改的名称快照。
     *
     * @param models 已排序模型映射
     * @param <T> Spring AI 模型类型
     * @return 保持排序迭代顺序的不可修改名称集合
     */
    private static <T> Set<String> immutableNames(NavigableMap<String, T> models) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(models.navigableKeySet()));
    }

    /**
     * 根据显式配置和候选数量解析默认模型名称。
     *
     * @param modelType 用于配置异常的模型类型名称
     * @param models 已校验且不可修改的模型映射
     * @param modelNames 已排序且不可修改的模型名称快照
     * @param configuredName 调用方配置的默认 Bean 名称
     * @param <T> Spring AI 模型类型
     * @return 解析后的默认名称；空集合且未配置时返回 {@code null}
     * @throws AiException 非空集合的显式默认项不存在或多候选存在歧义时抛出
     */
    private static <T> String resolveDefaultName(
            String modelType,
            NavigableMap<String, T> models,
            Set<String> modelNames,
            String configuredName) {
        String normalizedName = normalizeConfiguredName(configuredName);

        // 空模型集合属于用户尚未提供实现的合法扩展边界，查询时再报告缺失。
        if (models.isEmpty()) {
            return normalizedName;
        }
        if (normalizedName != null) {
            if (!models.containsKey(normalizedName)) {
                throw configurationInvalid(
                        "默认 " + modelType + " 不存在：" + normalizedName
                                + "，候选名称：" + modelNames);
            }
            return normalizedName;
        }
        if (models.size() == 1) {
            return models.firstKey();
        }
        throw configurationInvalid(
                modelType + " 存在多个候选，必须配置默认模型，候选名称：" + modelNames);
    }

    /**
     * 规范化显式默认 Bean 名称。
     *
     * @param configuredName 原始配置值
     * @return 去除首尾空白后的名称；未配置时返回 {@code null}
     */
    private static String normalizeConfiguredName(String configuredName) {
        if (configuredName == null) {
            return null;
        }
        String normalizedName = configuredName.strip();
        return normalizedName.isEmpty() ? null : normalizedName;
    }

    /**
     * 获取默认模型查询失败时使用的显示名称。
     *
     * @param defaultName 已解析的默认模型名称
     * @return 默认名称；未解析出默认项时返回稳定占位文本
     */
    private static String defaultModelDisplayName(String defaultName) {
        return defaultName == null ? "默认模型" : defaultName;
    }

    /**
     * 获取按名称查询失败时使用的稳定显示文本。
     *
     * @param requestedName 调用方请求的 Bean 名称
     * @return 原始名称；空值时返回文本 {@code null}
     */
    private static String requestedNameDisplay(String requestedName) {
        return String.valueOf(requestedName);
    }

    /**
     * 判断名称是否包含至少一个非空白字符。
     *
     * @param name 待验证名称
     * @return 名称非空且非空白时返回 {@code true}
     */
    private static boolean isNonBlank(String name) {
        return name != null && !name.isBlank();
    }

    /**
     * 创建 AI 配置不合法异常。
     *
     * @param detail 稳定的配置错误详情
     * @return 结构化 AI 配置异常
     */
    private static AiException configurationInvalid(String detail) {
        return AiException.of(AiErrorCode.CONFIGURATION_INVALID, detail);
    }
}
