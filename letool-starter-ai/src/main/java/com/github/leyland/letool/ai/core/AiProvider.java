package com.github.leyland.letool.ai.core;

import com.github.leyland.letool.ai.exception.AiException;

import java.util.List;
import java.util.function.Consumer;

/**
 * AI 提供商接口 —— 定义所有 LLM 提供商必须实现的核心能力.
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>发送对话请求（{@link #chat(ChatRequest)}）</li>
 *   <li>发送流式对话请求（{@link #chatStream(ChatRequest, Consumer)}）</li>
 *   <li>获取文本向量（{@link #embedding(EmbeddingRequest)}）</li>
 *   <li>返回提供商元信息（{@link #getProviderName()}、{@link #getAvailableModels()}）</li>
 *   <li>可用性检查（{@link #isAvailable()}）</li>
 * </ul>
 *
 * <h3>实现建议</h3>
 * <p>实现类应处理以下场景：</p>
 * <ul>
 *   <li>API 密钥未配置时，{@link #chat(ChatRequest)} 抛出 {@link AiException}</li>
 *   <li>HTTP 请求失败时，包装原始异常</li>
 *   <li>API 返回错误时，解析错误信息并抛出有意义的异常</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public interface AiProvider {

    // ======================== 核心能力 ========================

    /**
     * 发送对话请求并获取响应.
     *
     * @param request 对话请求（包含消息、模型、参数等）
     * @return 大模型返回的对话响应
     * @throws AiException 当 API 调用失败时
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 发送流式对话请求并逐段回调增量文本。
     *
     * <p>默认实现表示当前提供商暂不支持流式输出，具体 provider 可覆盖该方法。</p>
     *
     * @param request 对话请求（包含消息、模型、参数等）
     * @param onDelta 增量文本回调
     * @throws AiException 当 API 调用失败或 provider 不支持流式输出时
     */
    default void chatStream(ChatRequest request, Consumer<String> onDelta) {
        throw new AiException("AI 提供商暂不支持流式对话: " + getProviderName(), getProviderName());
    }

    /**
     * 获取文本的向量表示（嵌入）.
     *
     * @param request 嵌入请求（包含待向量化的文本列表）
     * @return 嵌入响应（包含每个文本对应的向量）
     * @throws AiException 当 API 调用失败时
     */
    EmbeddingResponse embedding(EmbeddingRequest request);

    // ======================== 元信息 ========================

    /**
     * 获取提供商名称.
     *
     * @return 提供商唯一标识（如 "openai"、"deepseek"）
     */
    String getProviderName();

    /**
     * 获取当前提供商可用的模型列表.
     *
     * @return 模型名称列表
     */
    List<String> getAvailableModels();

    /**
     * 检查提供商是否可用.
     *
     * <p>检查逻辑包括：</p>
     * <ul>
     *   <li>API 密钥是否已配置</li>
     *   <li>网络是否可达（可选，取决于实现）</li>
     * </ul>
     *
     * @return {@code true} 如果可用
     */
    boolean isAvailable();
}
