package com.github.leyland.letool.ai.provider;

import com.github.leyland.letool.ai.config.AiProperties;

import java.util.Arrays;
import java.util.List;

/**
 * DeepSeek 提供商实现 —— DeepSeek API 与 OpenAI 完全兼容，继承 OpenAiProvider.
 *
 * <h3>API 兼容性</h3>
 * <p>DeepSeek 的 Chat Completions API 与 OpenAI 格式一致，因此直接复用
 * {@link OpenAiProvider} 的请求构建和响应解析逻辑.</p>
 *
 * <h3>默认配置</h3>
 * <ul>
 *   <li>模型：<b>deepseek-chat</b></li>
 *   <li>终端：<b>https://api.deepseek.com/v1</b></li>
 * </ul>
 *
 * <h3>可用模型</h3>
 * <ul>
 *   <li><b>deepseek-chat</b> —— 通用对话模型</li>
 *   <li><b>deepseek-reasoner</b> —— 深度推理模型（R1）</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class DeepSeekProvider extends OpenAiProvider {

    // ======================== 常量 ========================

    /** 提供商名称 */
    public static final String PROVIDER_NAME = "deepseek";

    // ======================== 构造方法 ========================

    /**
     * 创建 DeepSeek 提供商实例.
     *
     * @param config DeepSeek 配置属性
     */
    public DeepSeekProvider(AiProperties.DeepSeek config) {
        super(config);
    }

    // ======================== 元信息（覆盖） ========================

    /**
     * 获取提供商名称.
     *
     * @return "deepseek"
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 获取可用模型列表.
     *
     * @return DeepSeek 模型名称列表
     */
    @Override
    public List<String> getAvailableModels() {
        return Arrays.asList("deepseek-chat", "deepseek-reasoner");
    }

    // ======================== 检查 API 密钥 ========================

    /**
     * 检查 API 密钥.
     *
     * @throws com.github.leyland.letool.ai.exception.AiException 如果密钥未配置
     */
    @Override
    protected void checkApiKey() {
        if (config.getApiKey() == null || config.getApiKey().isEmpty()) {
            throw new com.github.leyland.letool.ai.exception.AiException(
                    "DeepSeek API 密钥未配置，请在 letool.ai.deepseek.api-key 中设置", PROVIDER_NAME);
        }
    }
}
