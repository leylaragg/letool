package com.github.leyland.letool.ai.chat;

import com.github.leyland.letool.ai.core.ChatMessage;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板引擎 —— 支持变量替换的简单模板系统.
 *
 * <h3>模板语法</h3>
 * <p>使用 <b>{{变量名}}</b> 语法作为占位符，支持中文变量名.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * PromptTemplate template = new PromptTemplate();
 *
 * // 基础渲染
 * String result = template.render(
 *     "你好，{{name}}！今天是{{date}}。",
 *     Map.of("name", "小明", "date", "星期一")
 * );
 * // 结果: "你好，小明！今天是星期一。"
 *
 * // 渲染为 ChatMessage
 * ChatMessage msg = template.renderMessage(
 *     "你是{{role}}，请用{{language}}回答问题。",
 *     Map.of("role", "翻译官", "language", "英文")
 * );
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class PromptTemplate {

    // ======================== 常量 ========================

    /** 变量占位符正则：匹配 {{变量名}} */
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");

    // ======================== 核心方法 ========================

    /**
     * 渲染模板字符串，将所有 {{变量}} 占位符替换为实际值.
     *
     * <p>如果变量在 variables 中不存在，占位符保持原样不替换.</p>
     *
     * @param template   包含 {{变量名}} 占位符的模板字符串
     * @param variables  变量名到值的映射
     * @return 渲染后的字符串
     */
    public String render(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object value = variables.get(varName);
            String replacement = (value != null) ? String.valueOf(value) : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 渲染模板并将结果包装为系统消息.
     *
     * @param template   包含 {{变量名}} 占位符的模板字符串
     * @param variables  变量名到值的映射
     * @return 渲染后的 {@link ChatMessage}（角色为 system）
     */
    public ChatMessage renderSystem(String template, Map<String, Object> variables) {
        return ChatMessage.system(render(template, variables));
    }

    /**
     * 渲染模板并将结果包装为用户消息.
     *
     * @param template   包含 {{变量名}} 占位符的模板字符串
     * @param variables  变量名到值的映射
     * @return 渲染后的 {@link ChatMessage}（角色为 user）
     */
    public ChatMessage renderUser(String template, Map<String, Object> variables) {
        return ChatMessage.user(render(template, variables));
    }

    /**
     * 渲染模板并将结果包装为助手消息.
     *
     * @param template   包含 {{变量名}} 占位符的模板字符串
     * @param variables  变量名到值的映射
     * @return 渲染后的 {@link ChatMessage}（角色为 assistant）
     */
    public ChatMessage renderAssistant(String template, Map<String, Object> variables) {
        return ChatMessage.assistant(render(template, variables));
    }

    // ======================== 便捷方法 ========================

    /**
     * 检查模板字符串中是否包含变量占位符.
     *
     * @param template 模板字符串
     * @return {@code true} 如果包含至少一个 {{变量}} 占位符
     */
    public boolean hasPlaceholders(String template) {
        if (template == null) return false;
        return PLACEHOLDER_PATTERN.matcher(template).find();
    }
}
