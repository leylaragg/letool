package com.github.leyland.letool.ai.core;

/**
 * 对话响应模型 —— 封装 LLM 返回的完整对话响应.
 *
 * <h3>包含信息</h3>
 * <ul>
 *   <li>{@code id} —— 本次请求的唯一标识</li>
 *   <li>{@code provider} —— 实际使用的提供商</li>
 *   <li>{@code model} —— 实际使用的模型</li>
 *   <li>{@code choice} —— AI 的回复内容（含角色、文本、函数调用）</li>
 *   <li>{@code usage} —— Token 使用统计</li>
 *   <li>{@code latencyMs} —— 请求耗时（毫秒）</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
public class ChatResponse {

    // ======================== 字段 ========================

    /** 本次请求的唯一标识 */
    private String id;

    /** 供应商名称 */
    private String provider;

    /** 实际使用的模型名称 */
    private String model;

    /** AI 的回复选项 */
    private ChatChoice choice;

    /** Token 使用统计 */
    private Usage usage;

    /** 请求耗时（毫秒） */
    private long latencyMs;

    // ======================== 辅助方法 ========================

    /**
     * 获取 AI 回复的文本内容.
     *
     * @return AI 回复的文本，若无内容返回 {@code null}
     */
    public String getContent() {
        return choice != null ? choice.getContent() : null;
    }

    /**
     * 判断是否包含函数调用.
     *
     * @return {@code true} 如果 AI 决定调用函数
     */
    public boolean hasFunctionCall() {
        return choice != null && choice.getFunctionCall() != null;
    }

    // ======================== getter / setter ========================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public ChatChoice getChoice() { return choice; }
    public void setChoice(ChatChoice choice) { this.choice = choice; }
    public Usage getUsage() { return usage; }
    public void setUsage(Usage usage) { this.usage = usage; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    // ======================== 内部类：ChatChoice ========================

    /**
     * 对话选项 —— 表示 AI 返回的一条回复.
     *
     * <p>包含回复内容、角色、完成原因和可选的函数调用信息.</p>
     */
    public static class ChatChoice {

        /** 回复文本内容 */
        private String content;

        /** 回复角色（通常为 "assistant"） */
        private String role;

        /** 完成原因：stop（正常结束）、length（Token 限制）、function_call（函数调用）等 */
        private String finishReason;

        /** 函数调用信息（仅当 finishReason 为 "function_call" 时有值） */
        private FunctionCall functionCall;

        // ======================== getter / setter ========================

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getFinishReason() { return finishReason; }
        public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
        public FunctionCall getFunctionCall() { return functionCall; }
        public void setFunctionCall(FunctionCall functionCall) { this.functionCall = functionCall; }
    }

    // ======================== 内部类：Usage ========================

    /**
     * Token 使用统计 —— 记录本次请求消耗的 Token 数量.
     */
    public static class Usage {

        /** 提示 Token 数（输入） */
        private int promptTokens;

        /** 完成 Token 数（输出） */
        private int completionTokens;

        /** 总 Token 数 */
        private int totalTokens;

        // ======================== getter / setter ========================

        /**
         * 获取提示 Token 数.
         *
         * @return 输入的 Token 数量
         */
        public int getPromptTokens() { return promptTokens; }
        public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

        /**
         * 获取完成 Token 数.
         *
         * @return 输出的 Token 数量
         */
        public int getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

        /**
         * 获取总 Token 数.
         *
         * @return promptTokens + completionTokens
         */
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    }
}
