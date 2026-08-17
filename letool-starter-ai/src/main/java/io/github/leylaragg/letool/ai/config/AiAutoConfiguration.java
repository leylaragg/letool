package io.github.leylaragg.letool.ai.config;

import io.github.leylaragg.letool.ai.core.AiChatClientCustomizer;
import io.github.leylaragg.letool.ai.core.AiModelRegistry;
import io.github.leylaragg.letool.ai.core.AiTemplate;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * Letool AI 模块自动配置。
 *
 * <p>自动配置只负责收集业务项目已经提供的 Spring AI 模型、建立按 Bean 名称路由的
 * 不可变注册表并创建统一调用门面，不会创建具体模型、网络客户端、向量存储或 RAG 实现。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(AiProperties.class)
@ConditionalOnProperty(
        prefix = "letool.ai",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AiAutoConfiguration {

    /**
     * 创建 Spring AI 模型不可变注册表。
     *
     * <p>模型映射由 Spring 按 Bean 名称注入。没有模型属于合法扩展边界，应用可以先
     * 启动，待实际查询模型时再获得结构化缺失异常。</p>
     *
     * @param chatModels 以 Spring Bean 名称为键的全部对话模型
     * @param embeddingModels 以 Spring Bean 名称为键的全部嵌入模型
     * @param properties AI 路由配置属性
     * @return 不可变模型注册表
     */
    @Bean
    @ConditionalOnMissingBean(AiModelRegistry.class)
    public AiModelRegistry aiModelRegistry(
            Map<String, ChatModel> chatModels,
            Map<String, EmbeddingModel> embeddingModels,
            AiProperties properties) {
        return new AiModelRegistry(
                chatModels,
                embeddingModels,
                properties.getDefaultChatModel(),
                properties.getDefaultEmbeddingModel());
    }

    /**
     * 创建基于 Spring AI 原生客户端的统一调用门面。
     *
     * @param modelRegistry 不可变模型注册表
     * @param customizers 业务项目提供的客户端构建器定制器
     * @return AI 统一调用门面
     */
    @Bean
    @ConditionalOnMissingBean(AiTemplate.class)
    public AiTemplate aiTemplate(
            AiModelRegistry modelRegistry,
            ObjectProvider<AiChatClientCustomizer> customizers) {
        return new AiTemplate(modelRegistry, customizers.orderedStream().toList());
    }
}
