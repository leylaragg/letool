package com.github.leyland.letool.ai.core;

import com.github.leyland.letool.ai.exception.AiErrorCode;
import com.github.leyland.letool.ai.exception.AiException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基于 Spring AI 原生类型的统一调用门面。
 *
 * <p>门面在构造阶段为每个 {@link ChatModel} 创建并缓存独立的 {@link ChatClient}，
 * 同时保留按 Spring Bean 名称选择模型的能力。模型调用、流式响应、工具调用和顾问机制
 * 均由 Spring AI 负责，本类只处理稳定路由和客户端定制。</p>
 */
public final class AiTemplate {

    /** 不可变模型注册表。 */
    private final AiModelRegistry modelRegistry;

    /** 按模型 Bean 名称排序且不可修改的客户端缓存。 */
    private final Map<String, ChatClient> chatClients;

    /**
     * 创建 AI 统一调用门面。
     *
     * <p>定制器列表会进行非空校验、防御复制并按 Spring 规则排序。每个模型使用独立
     * 构建器执行全部定制器；定制器失败会转换为包含模型名称和原因链的结构化异常。</p>
     *
     * @param modelRegistry 不可变模型注册表
     * @param customizers 对话客户端构建器定制器列表
     * @throws NullPointerException 注册表、定制器列表或任一定制器为空时抛出
     * @throws AiException 任一定制器执行失败时抛出
     */
    public AiTemplate(
            AiModelRegistry modelRegistry,
            List<AiChatClientCustomizer> customizers) {
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "模型注册表不能为空");
        List<AiChatClientCustomizer> orderedCustomizers = orderedCustomizers(customizers);
        this.chatClients = buildChatClients(modelRegistry, orderedCustomizers);
    }

    /**
     * 获取默认对话客户端。
     *
     * @return 默认模型对应的缓存客户端
     * @throws AiException 没有可用默认对话模型时抛出
     */
    public ChatClient chatClient() {
        String defaultModelName = modelRegistry.defaultChatModelName();
        if (defaultModelName == null) {
            // 复用注册表的稳定错误码和“默认模型”显示参数。
            modelRegistry.chatModel();
        }
        return requiredChatClient(defaultModelName);
    }

    /**
     * 按 Spring Bean 名称获取对话客户端。
     *
     * @param modelName 对话模型的 Spring Bean 名称
     * @return 指定模型对应的缓存客户端
     * @throws AiException 指定对话模型不存在时抛出
     */
    public ChatClient chatClient(String modelName) {
        // 先由注册表校验名称，确保模型与客户端查询使用相同的结构化异常语义。
        modelRegistry.chatModel(modelName);
        return requiredChatClient(modelName);
    }

    /**
     * 获取默认对话模型。
     *
     * @return 默认对话模型
     * @throws AiException 没有可用默认对话模型时抛出
     */
    public ChatModel chatModel() {
        return modelRegistry.chatModel();
    }

    /**
     * 按 Spring Bean 名称获取对话模型。
     *
     * @param modelName 对话模型的 Spring Bean 名称
     * @return 指定对话模型
     * @throws AiException 指定对话模型不存在时抛出
     */
    public ChatModel chatModel(String modelName) {
        return modelRegistry.chatModel(modelName);
    }

    /**
     * 获取默认嵌入模型。
     *
     * @return 默认嵌入模型
     * @throws AiException 没有可用默认嵌入模型时抛出
     */
    public EmbeddingModel embeddingModel() {
        return modelRegistry.embeddingModel();
    }

    /**
     * 按 Spring Bean 名称获取嵌入模型。
     *
     * @param modelName 嵌入模型的 Spring Bean 名称
     * @return 指定嵌入模型
     * @throws AiException 指定嵌入模型不存在时抛出
     */
    public EmbeddingModel embeddingModel(String modelName) {
        return modelRegistry.embeddingModel(modelName);
    }

    /**
     * 获取全部对话模型 Bean 名称。
     *
     * @return 按名称排序且不可修改的名称快照
     */
    public Set<String> chatModelNames() {
        return modelRegistry.chatModelNames();
    }

    /**
     * 获取全部嵌入模型 Bean 名称。
     *
     * @return 按名称排序且不可修改的名称快照
     */
    public Set<String> embeddingModelNames() {
        return modelRegistry.embeddingModelNames();
    }

    /**
     * 校验、复制并排序客户端定制器。
     *
     * @param customizers 调用方提供的客户端定制器列表
     * @return 排序后的不可修改定制器列表
     * @throws NullPointerException 列表或任一元素为空时抛出
     */
    private static List<AiChatClientCustomizer> orderedCustomizers(
            List<AiChatClientCustomizer> customizers) {
        Objects.requireNonNull(customizers, "客户端定制器列表不能为空");
        List<AiChatClientCustomizer> copy = new ArrayList<>(customizers.size());
        for (AiChatClientCustomizer customizer : customizers) {
            copy.add(Objects.requireNonNull(customizer, "客户端定制器不能为空"));
        }
        AnnotationAwareOrderComparator.sort(copy);
        return List.copyOf(copy);
    }

    /**
     * 为全部对话模型创建独立客户端并形成不可变缓存。
     *
     * @param modelRegistry 不可变模型注册表
     * @param customizers 已排序的客户端定制器列表
     * @return 按模型名称排序且不可修改的客户端缓存
     * @throws AiException 任一定制器执行失败时抛出
     */
    private static Map<String, ChatClient> buildChatClients(
            AiModelRegistry modelRegistry,
            List<AiChatClientCustomizer> customizers) {
        Map<String, ChatClient> clients = new LinkedHashMap<>();
        for (String modelName : modelRegistry.chatModelNames()) {
            ChatClient.Builder builder = ChatClient.builder(modelRegistry.chatModel(modelName));
            for (AiChatClientCustomizer customizer : customizers) {
                try {
                    customizer.customize(modelName, builder);
                } catch (RuntimeException exception) {
                    throw AiException.causedBy(
                            AiErrorCode.CLIENT_CUSTOMIZATION_FAILED,
                            exception,
                            modelName);
                }
            }
            clients.put(modelName, builder.build());
        }
        return Collections.unmodifiableMap(clients);
    }

    /**
     * 从客户端缓存获取指定实例。
     *
     * @param modelName 已通过注册表校验的模型 Bean 名称
     * @return 对应的缓存客户端
     * @throws AiException 客户端缓存与注册表意外不一致时抛出
     */
    private ChatClient requiredChatClient(String modelName) {
        ChatClient client = chatClients.get(modelName);
        if (client == null) {
            throw AiException.of(AiErrorCode.CHAT_MODEL_NOT_FOUND, String.valueOf(modelName));
        }
        return client;
    }
}
