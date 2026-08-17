package io.github.leylaragg.letool.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Letool AI 模块路由配置属性。
 *
 * <p>具体模型、鉴权、网络、重试和观测配置由用户选择的 Spring AI Provider Starter
 * 管理；本配置只控制 Letool 便利门面以及多模型场景的默认 Bean 名称。</p>
 */
@ConfigurationProperties(prefix = "letool.ai")
public class AiProperties {

    /** 是否启用 Letool AI 便利门面，默认启用。 */
    private boolean enabled = true;

    /** 默认对话模型的 Spring Bean 名称。 */
    private String defaultChatModel;

    /** 默认嵌入模型的 Spring Bean 名称。 */
    private String defaultEmbeddingModel;

    /**
     * 判断是否启用 Letool AI 便利门面。
     *
     * @return 启用时返回 {@code true}
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 Letool AI 便利门面。
     *
     * @param enabled 是否启用 Letool AI 便利门面
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取默认对话模型的 Spring Bean 名称。
     *
     * @return 默认对话模型 Bean 名称；未配置时返回 {@code null}
     */
    public String getDefaultChatModel() {
        return defaultChatModel;
    }

    /**
     * 设置默认对话模型的 Spring Bean 名称。
     *
     * @param defaultChatModel 默认对话模型 Bean 名称
     */
    public void setDefaultChatModel(String defaultChatModel) {
        this.defaultChatModel = defaultChatModel;
    }

    /**
     * 获取默认嵌入模型的 Spring Bean 名称。
     *
     * @return 默认嵌入模型 Bean 名称；未配置时返回 {@code null}
     */
    public String getDefaultEmbeddingModel() {
        return defaultEmbeddingModel;
    }

    /**
     * 设置默认嵌入模型的 Spring Bean 名称。
     *
     * @param defaultEmbeddingModel 默认嵌入模型 Bean 名称
     */
    public void setDefaultEmbeddingModel(String defaultEmbeddingModel) {
        this.defaultEmbeddingModel = defaultEmbeddingModel;
    }
}
