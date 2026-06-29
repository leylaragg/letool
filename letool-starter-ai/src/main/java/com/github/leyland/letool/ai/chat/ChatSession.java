package com.github.leyland.letool.ai.chat;

import com.github.leyland.letool.ai.config.AiProperties;
import com.github.leyland.letool.ai.core.AiTemplate;
import com.github.leyland.letool.ai.core.ChatMessage;
import com.github.leyland.letool.ai.core.ChatRequest;
import com.github.leyland.letool.ai.core.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * 对话会话管理器 —— 管理单次对话的状态、历史记录和上下文.
 *
 * <h3>功能</h3>
 * <ul>
 *   <li>以会话 ID 标识每个独立的对话实例</li>
 *   <li>维护对话历史记录（自动裁剪超出 maxHistoryLength 的最早消息）</li>
 *   <li>支持设置系统提示词，每次请求自动包含</li>
 *   <li>提供 {@code chat(String)} 便捷方法，自动追加用户消息并获取 AI 回复</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Autowired
 * private ChatSession chatSession;  // prototype scope
 *
 * chatSession.setSystemPrompt("你是一个乐于助人的助手");
 *
 * String reply1 = chatSession.chat("我叫小明");
 * String reply2 = chatSession.chat("我叫什么名字？");
 * // reply2: "你叫小明"  ← 因为历史记录了上一轮对话
 *
 * List<ChatMessage> history = chatSession.getHistory();
 * chatSession.clearHistory();  // 重置会话
 * }</pre>
 *
 * <h3>生命周期说明</h3>
 * <p>{@code ChatSession} 定义为 prototype 作用域（每次注入获得新实例），
 * 确保不同用户/对话之间互不干扰.</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class ChatSession {

    private static final Logger log = LoggerFactory.getLogger(ChatSession.class);

    // ======================== 字段 ========================

    /** 会话唯一标识 */
    private final String sessionId;

    /** AI 模板引擎 */
    private final AiTemplate aiTemplate;

    /** 全局 AI 配置 */
    private final AiProperties aiProperties;

    /** 对话历史记录（先进先出，溢出时移除最早的非系统消息） */
    private final LinkedList<ChatMessage> history;

    /** 系统提示词（可为 null） */
    private String systemPrompt;

    // ======================== 构造方法 ========================

    /**
     * 创建对话会话实例.
     *
     * @param aiTemplate   AI 模板引擎
     * @param aiProperties AI 全局配置
     */
    public ChatSession(AiTemplate aiTemplate, AiProperties aiProperties) {
        this.sessionId = UUID.randomUUID().toString().replace("-", "");
        this.aiTemplate = aiTemplate;
        this.aiProperties = aiProperties;
        this.history = new LinkedList<>();
        log.debug("创建 ChatSession: sessionId={}", sessionId);
    }

    // ======================== 会话管理 ========================

    /**
     * 获取会话 ID.
     *
     * @return 会话的唯一标识
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 添加消息到历史记录.
     *
     * <p>当历史记录超过 {@code maxHistoryLength} 时，自动移除最早的非系统消息.</p>
     *
     * @param message 聊天消息
     */
    public void addMessage(ChatMessage message) {
        history.addLast(message);
        trimHistory();
    }

    /**
     * 获取对话历史记录（深拷贝，防止外部修改）.
     *
     * @return 历史消息列表的副本
     */
    public List<ChatMessage> getHistory() {
        return new LinkedList<>(history);
    }

    /**
     * 清空历史记录（保留系统提示词）.
     */
    public void clearHistory() {
        history.clear();
        log.debug("ChatSession 历史已清空: sessionId={}", sessionId);
    }

    /**
     * 完全重置会话（清空历史记录和系统提示词）.
     */
    public void reset() {
        history.clear();
        systemPrompt = null;
        log.debug("ChatSession 已重置: sessionId={}", sessionId);
    }

    // ======================== 系统提示词 ========================

    /**
     * 设置系统提示词.
     *
     * <p>系统提示词设定 AI 在整个会话中的行为和角色.</p>
     *
     * @param prompt 系统提示词内容
     */
    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt;
    }

    /**
     * 获取系统提示词.
     *
     * @return 当前系统提示词，未设置返回 {@code null}
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    // ======================== 对话方法 ========================

    /**
     * 发送用户消息并获取 AI 回复（单轮对话）.
     *
     * <p>流程：</p>
     * <ol>
     *   <li>将用户消息加入历史</li>
     *   <li>构建包含历史 + 系统提示词 + 用户消息的请求</li>
     *   <li>调用 AI 获取响应</li>
     *   <li>将 AI 回复加入历史</li>
     *   <li>返回 AI 的回复文本</li>
     * </ol>
     *
     * @param userMessage 用户输入的消息内容
     * @return AI 的回复文本
     */
    public String chat(String userMessage) {
        // 添加用户消息
        ChatMessage userMsg = ChatMessage.user(userMessage);
        history.addLast(userMsg);

        // 构建请求
        ChatRequest request = buildRequest();

        // 调用 AI
        ChatResponse response = aiTemplate.chat()
                .provider(request.getProvider())
                .model(request.getModel())
                .messages(request.getMessages())
                .temperature(request.getTemperature())
                .maxTokens(request.getMaxTokens())
                .execute();

        // 记录 AI 回复
        if (response.getContent() != null) {
            ChatMessage assistantMsg = ChatMessage.assistant(response.getContent());
            history.addLast(assistantMsg);
        }

        // 裁剪历史
        trimHistory();

        return response.getContent();
    }

    /**
     * 从当前历史记录和系统提示词构建对话请求.
     *
     * @return 构建好的 {@link ChatRequest}
     */
    public ChatRequest buildRequest() {
        ChatRequest request = new ChatRequest();
        request.setMessages(new LinkedList<>());

        // 如果设置了系统提示词，添加到消息列表开头
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            request.getMessages().add(ChatMessage.system(systemPrompt));
        }

        // 添加历史记录
        request.getMessages().addAll(history);

        // 应用默认参数
        request.setTemperature(aiProperties.getChat().getDefaultTemperature());
        request.setMaxTokens(aiProperties.getChat().getDefaultMaxTokens());

        return request;
    }

    // ======================== 内部方法 ========================

    /**
     * 裁剪历史记录，超出最大长度时移除最早的非系统消息.
     */
    private void trimHistory() {
        int maxLength = aiProperties.getChat().getMaxHistoryLength();
        while (history.size() > maxLength) {
            history.removeFirst();
        }
    }
}
