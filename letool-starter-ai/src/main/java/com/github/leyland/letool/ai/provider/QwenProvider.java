package com.github.leyland.letool.ai.provider;

import com.github.leyland.letool.ai.config.AiProperties;

import java.util.Arrays;
import java.util.List;

/**
 * 通义千问（Qwen）提供商实现 —— 阿里云 DashScope 兼容模式 API.
 *
 * <h3>API 兼容性</h3>
 * <p>阿里云 DashScope 提供了 OpenAI 兼容的 API 端点（{@code /compatible-mode/v1}），
 * 因此直接复用 {@link OpenAiProvider} 的请求构建和响应解析逻辑.</p>
 *
 * <h3>默认配置</h3>
 * <ul>
 *   <li>模型：<b>qwen-max</b></li>
 *   <li>终端：<b>https://dashscope.aliyuncs.com/compatible-mode/v1</b></li>
 * </ul>
 *
 * <h3>可用模型</h3>
 * <ul>
 *   <li><b>qwen-max</b> —— 最强性能</li>
 *   <li><b>qwen-plus</b> —— 性价比</li>
 *   <li><b>qwen-turbo</b> —— 低成本</li>
 *   <li><b>qwen-long</b> —— 长文本</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class QwenProvider extends OpenAiProvider {

    // ======================== 常量 ========================

    /** 提供商名称 */
    public static final String PROVIDER_NAME = "qwen";

    // ======================== 构造方法 ========================

    /**
     * 创建通义千问提供商实例.
     *
     * @param config 通义千问配置属性
     */
    public QwenProvider(AiProperties.Qwen config) {
        super(config);
    }

    // ======================== 元信息（覆盖） ========================

    /**
     * 获取提供商名称.
     *
     * @return "qwen"
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    /**
     * 获取可用模型列表.
     *
     * @return 通义千问模型名称列表
     */
    @Override
    public List<String> getAvailableModels() {
        return Arrays.asList("qwen-max", "qwen-plus", "qwen-turbo", "qwen-long",
                "qwq-plus", "qwen2.5-72b-instruct", "qwen2.5-7b-instruct");
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
                    "通义千问 API 密钥未配置，请在 letool.ai.qwen.api-key 中设置", PROVIDER_NAME);
        }
    }
}
