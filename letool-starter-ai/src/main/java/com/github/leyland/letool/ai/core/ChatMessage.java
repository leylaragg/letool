package com.github.leyland.letool.ai.core;

/**
 * 对话消息模型 —— 表示一条聊天消息，包含角色、内容、函数调用等信息.
 *
 * <h3>角色类型</h3>
 * <ul>
 *   <li><b>system</b> —— 系统提示词，设定 AI 的行为和风格</li>
 *   <li><b>user</b> —— 用户输入的消息</li>
 *   <li><b>assistant</b> —— AI 的回复消息</li>
 *   <li><b>function</b> —— 函数调用返回的结果</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 静态工厂方法
 * ChatMessage systemMsg = ChatMessage.system("你是一个乐于助人的助手");
 * ChatMessage userMsg = ChatMessage.user("今天天气怎么样？");
 * ChatMessage assistantMsg = ChatMessage.assistant("今天晴天，25度");
 * ChatMessage funcMsg = ChatMessage.function("get_weather", "晴天，25度");
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class ChatMessage {

    // ======================== 常量 ========================

    /** 角色：系统 */
    public static final String ROLE_SYSTEM = "system";
    /** 角色：用户 */
    public static final String ROLE_USER = "user";
    /** 角色：助手 */
    public static final String ROLE_ASSISTANT = "assistant";
    /** 角色：函数返回 */
    public static final String ROLE_FUNCTION = "function";

    // ======================== 字段 ========================

    /** 消息角色（system / user / assistant / function） */
    private String role;

    /** 消息内容文本 */
    private String content;

    /** 函数消息的名称（仅 role=function 时有效） */
    private String name;

    /** 函数调用信息（仅 role=assistant 且 AI 决定调用函数时有效） */
    private FunctionCall functionCall;

    // ======================== 构造方法 ========================

    /** 默认构造 */
    public ChatMessage() {}

    /**
     * 创建指定角色和内容的消息.
     *
     * @param role    角色
     * @param content 消息内容
     */
    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 创建系统消息 —— 用于设定 AI 的行为和风格.
     *
     * @param content 系统提示词内容
     * @return 系统消息实例
     */
    public static ChatMessage system(String content) {
        return new ChatMessage(ROLE_SYSTEM, content);
    }

    /**
     * 创建用户消息 —— 用户对 AI 说的话.
     *
     * @param content 用户输入内容
     * @return 用户消息实例
     */
    public static ChatMessage user(String content) {
        return new ChatMessage(ROLE_USER, content);
    }

    /**
     * 创建助手消息 —— AI 的回复.
     *
     * @param content AI 回复内容
     * @return 助手消息实例
     */
    public static ChatMessage assistant(String content) {
        return new ChatMessage(ROLE_ASSISTANT, content);
    }

    /**
     * 创建函数返回消息 —— 函数执行结果回传给 AI.
     *
     * @param name    函数名称
     * @param content 函数执行结果（JSON 字符串或普通文本）
     * @return 函数消息实例
     */
    public static ChatMessage function(String name, String content) {
        ChatMessage msg = new ChatMessage(ROLE_FUNCTION, content);
        msg.name = name;
        return msg;
    }

    // ======================== getter / setter ========================

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public FunctionCall getFunctionCall() { return functionCall; }
    public void setFunctionCall(FunctionCall functionCall) { this.functionCall = functionCall; }

    // ======================== 辅助方法 ========================

    /**
     * 判断是否为系统消息.
     *
     * @return {@code true} 如果角色为 "system"
     */
    public boolean isSystem() { return ROLE_SYSTEM.equals(role); }

    /**
     * 判断是否为用户消息.
     *
     * @return {@code true} 如果角色为 "user"
     */
    public boolean isUser() { return ROLE_USER.equals(role); }

    /**
     * 判断是否为助手消息.
     *
     * @return {@code true} 如果角色为 "assistant"
     */
    public boolean isAssistant() { return ROLE_ASSISTANT.equals(role); }

    /**
     * 判断是否为函数返回消息.
     *
     * @return {@code true} 如果角色为 "function"
     */
    public boolean isFunction() { return ROLE_FUNCTION.equals(role); }

    /**
     * 判断此消息是否包含函数调用请求.
     *
     * @return {@code true} 如果存在函数调用信息
     */
    public boolean hasFunctionCall() {
        return functionCall != null;
    }
}
