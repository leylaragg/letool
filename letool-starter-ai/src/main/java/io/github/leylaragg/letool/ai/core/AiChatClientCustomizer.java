package io.github.leylaragg.letool.ai.core;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Spring AI 对话客户端构建器定制扩展点。
 *
 * <p>每个已注册的 {@code ChatModel} 都会获得独立的构建器。业务项目可以实现本接口，
 * 按模型名称添加系统提示词、顾问、工具或默认选项；多个实现遵循 Spring 排序规则执行。</p>
 */
@FunctionalInterface
public interface AiChatClientCustomizer {

    /**
     * 定制指定模型对应的对话客户端构建器。
     *
     * @param modelName 对话模型的 Spring Bean 名称
     * @param builder 当前模型专属的对话客户端构建器
     */
    void customize(String modelName, ChatClient.Builder builder);
}
