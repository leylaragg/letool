package com.github.leyland.letool.ai.core;

/**
 * 函数调用模型 —— 表示大模型返回的函数调用请求信息.
 *
 * <h3>使用场景</h3>
 * <p>当大模型判断需要调用外部函数时，会在 ChatResponse 的 ChatChoice 中返回
 * 一个 FunctionCall 对象，包含函数名和 JSON 格式的参数.</p>
 *
 * <h3>数据结构</h3>
 * <pre>{@code
 * {
 *   "name": "get_weather",
 *   "arguments": "{\"city\": \"北京\"}"
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class FunctionCall {

    /** 要调用的函数名称 */
    private String name;

    /** 函数参数，JSON 字符串格式 */
    private String arguments;

    // ======================== 构造方法 ========================

    /** 默认构造 */
    public FunctionCall() {}

    /**
     * 创建函数调用对象.
     *
     * @param name      函数名称
     * @param arguments JSON 格式的参数
     */
    public FunctionCall(String name, String arguments) {
        this.name = name;
        this.arguments = arguments;
    }

    // ======================== getter / setter ========================

    /**
     * 获取函数名称.
     *
     * @return 函数名称
     */
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    /**
     * 获取函数参数（JSON 字符串）.
     *
     * @return JSON 格式的参数字符串
     */
    public String getArguments() { return arguments; }
    public void setArguments(String arguments) { this.arguments = arguments; }
}
