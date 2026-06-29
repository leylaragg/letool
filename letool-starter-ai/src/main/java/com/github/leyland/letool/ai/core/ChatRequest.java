package com.github.leyland.letool.ai.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话请求模型 —— 封装一次 LLM 对话请求的所有参数.
 *
 * <h3>包含信息</h3>
 * <ul>
 *   <li>提供商与模型选择</li>
 *   <li>消息列表（system、user、assistant、function）</li>
 *   <li>温度、最大 Token 数等生成参数</li>
 *   <li>可选的函数定义列表（Function Calling）</li>
 * </ul>
 *
 * <h3>推荐使用 Builder 模式构建</h3>
 * <pre>{@code
 * ChatRequest request = ChatRequest.builder()
 *     .provider("openai")
 *     .model("gpt-4o")
 *     .addSystem("你是一个助手")
 *     .addUser("你好")
 *     .temperature(0.7)
 *     .maxTokens(2048)
 *     .build();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class ChatRequest {

    // ======================== 字段 ========================

    /** 提供商名称 */
    private String provider;

    /** 模型名称 */
    private String model;

    /** 对话消息列表 */
    private List<ChatMessage> messages;

    /** 温度参数（0.0~2.0），控制随机性 */
    private double temperature;

    /** 最大生成 Token 数 */
    private int maxTokens;

    /** 函数定义列表（用于 Function Calling） */
    private List<FunctionDefinition> functions;

    // ======================== 构造方法 ========================

    /** 默认构造，初始化消息列表 */
    public ChatRequest() {
        this.messages = new ArrayList<>();
    }

    // ======================== 静态工厂：Builder ========================

    /**
     * 创建构建器.
     *
     * @return {@link Builder} 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    // ======================== Builder 内部类 ========================

    /**
     * ChatRequest 构建器 —— 支持链式调用.
     */
    public static class Builder {
        private String provider;
        private String model;
        private List<ChatMessage> messages = new ArrayList<>();
        private double temperature;
        private int maxTokens;
        private List<FunctionDefinition> functions;

        /**
         * 设置提供商名称.
         *
         * @param provider 提供商名称（如 "openai"）
         * @return this
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * 设置模型名称.
         *
         * @param model 模型名称（如 "gpt-4o"）
         * @return this
         */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        /**
         * 设置消息列表（覆盖已有的消息）.
         *
         * @param messages 消息列表
         * @return this
         */
        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        /**
         * 添加一条消息.
         *
         * @param message 消息对象
         * @return this
         */
        public Builder addMessage(ChatMessage message) {
            this.messages.add(message);
            return this;
        }

        /**
         * 添加系统消息.
         *
         * @param content 系统提示词
         * @return this
         */
        public Builder addSystem(String content) {
            this.messages.add(ChatMessage.system(content));
            return this;
        }

        /**
         * 添加用户消息.
         *
         * @param content 用户输入
         * @return this
         */
        public Builder addUser(String content) {
            this.messages.add(ChatMessage.user(content));
            return this;
        }

        /**
         * 添加助手消息.
         *
         * @param content AI 回复
         * @return this
         */
        public Builder addAssistant(String content) {
            this.messages.add(ChatMessage.assistant(content));
            return this;
        }

        /**
         * 设置温度参数.
         *
         * @param temperature 温度值（0.0~2.0）
         * @return this
         */
        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        /**
         * 设置最大 Token 数.
         *
         * @param maxTokens 最大 Token 数
         * @return this
         */
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * 设置函数定义列表.
         *
         * @param functions 函数定义列表
         * @return this
         */
        public Builder functions(List<FunctionDefinition> functions) {
            this.functions = functions;
            return this;
        }

        /**
         * 构建 ChatRequest 实例.
         *
         * @return 构建好的请求对象
         */
        public ChatRequest build() {
            ChatRequest request = new ChatRequest();
            request.provider = this.provider;
            request.model = this.model;
            request.messages = this.messages;
            request.temperature = this.temperature;
            request.maxTokens = this.maxTokens;
            request.functions = this.functions;
            return request;
        }
    }

    // ======================== getter / setter ========================

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) { this.messages = messages; }
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public List<FunctionDefinition> getFunctions() { return functions; }
    public void setFunctions(List<FunctionDefinition> functions) { this.functions = functions; }
}
