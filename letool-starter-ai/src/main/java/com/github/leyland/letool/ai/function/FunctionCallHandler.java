package com.github.leyland.letool.ai.function;

import com.github.leyland.letool.ai.core.FunctionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 函数调用处理器 —— 管理可被大模型调用的函数注册、执行和元数据.
 *
 * <h3>Function Calling 流程</h3>
 * <ol>
 *   <li>使用 {@link #register(String, Function)} 注册函数</li>
 *   <li>通过 {@link #getFunctionDefinitions()} 获取函数元数据</li>
 *   <li>将元数据发送给大模型（在 ChatRequest 的 functions 字段中）</li>
 *   <li>大模型返回函数调用请求（FunctionCall 对象）</li>
 *   <li>使用 {@link #invoke(String, Map)} 执行函数</li>
 *   <li>将执行结果作为 function 角色消息回传给大模型</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * FunctionCallHandler handler = new FunctionCallHandler();
 *
 * // 注册函数
 * handler.register("get_weather", "获取指定城市的天气信息", params -> {
 *     String city = (String) params.get("city");
 *     return weatherService.query(city);
 * });
 *
 * // 获取函数定义（发送给 LLM）
 * List<FunctionDefinition> defs = handler.getFunctionDefinitions();
 *
 * // 执行函数调用
 * String result = handler.invoke("get_weather", Map.of("city", "北京"));
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class FunctionCallHandler {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallHandler.class);

    // ======================== 字段 ========================

    /** 函数执行器映射（函数名 -> Function） */
    private final Map<String, Function<Map<String, Object>, String>> executorMap;

    /** 函数描述映射（函数名 -> 描述文本） */
    private final Map<String, String> descriptionMap;

    // ======================== 构造方法 ========================

    /**
     * 创建函数调用处理器.
     */
    public FunctionCallHandler() {
        this.executorMap = new LinkedHashMap<>();
        this.descriptionMap = new HashMap<>();
    }

    // ======================== 注册方法 ========================

    /**
     * 注册一个可被大模型调用的函数.
     *
     * @param name        函数名称（全局唯一）
     * @param description 函数描述（帮助大模型理解何时调用）
     * @param executor    函数执行逻辑（接收参数 Map，返回结果字符串）
     */
    public void register(String name, String description, Function<Map<String, Object>, String> executor) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("函数名称不能为空");
        }
        if (executor == null) {
            throw new IllegalArgumentException("函数执行器不能为空: " + name);
        }
        executorMap.put(name, executor);
        descriptionMap.put(name, description != null ? description : "");
        log.debug("注册 AI 函数: name={}, description={}", name, description);
    }

    /**
     * 注册一个可被大模型调用的函数（简写方法，使用 FunctionCallHandler 自带的函数名）.
     *
     * @param name     函数名称
     * @param executor 函数执行逻辑
     */
    public void register(String name, Function<Map<String, Object>, String> executor) {
        register(name, null, executor);
    }

    // ======================== 执行方法 ========================

    /**
     * 调用已注册的函数.
     *
     * @param name      函数名称
     * @param arguments 函数参数（key-value）
     * @return 函数执行结果的字符串
     * @throws IllegalArgumentException 如果函数未注册或执行失败
     */
    public String invoke(String name, Map<String, Object> arguments) {
        if (name == null) {
            throw new IllegalArgumentException("函数名称不能为空");
        }
        Function<Map<String, Object>, String> executor = executorMap.get(name);
        if (executor == null) {
            throw new IllegalArgumentException("未注册的 AI 函数: " + name);
        }
        try {
            log.debug("调用 AI 函数: name={}, args={}", name, arguments);
            String result = executor.apply(arguments != null ? arguments : Collections.emptyMap());
            log.debug("AI 函数执行结果: name={}, result={}", name, result);
            return result;
        } catch (Exception e) {
            log.error("AI 函数执行失败: name={}", name, e);
            throw new IllegalArgumentException("函数执行失败: " + name, e);
        }
    }

    // ======================== 元数据方法 ========================

    /**
     * 获取所有已注册函数的元数据（FunctionDefinition 列表）.
     *
     * <p>可将此列表直接设置到 {@code ChatRequest.functions} 中发送给大模型.</p>
     *
     * @return 函数定义列表
     */
    public List<FunctionDefinition> getFunctionDefinitions() {
        return executorMap.keySet().stream()
                .map(name -> {
                    FunctionDefinition def = new FunctionDefinition();
                    def.setName(name);
                    def.setDescription(descriptionMap.getOrDefault(name, ""));
                    def.setParameters(buildDefaultParameters());
                    return def;
                })
                .collect(Collectors.toList());
    }

    /**
     * 判断指定函数是否已注册.
     *
     * @param name 函数名称
     * @return {@code true} 如果已注册
     */
    public boolean hasFunction(String name) {
        return executorMap.containsKey(name);
    }

    /**
     * 获取所有已注册函数名称.
     *
     * @return 函数名称集合
     */
    public Set<String> getRegisteredFunctionNames() {
        return Collections.unmodifiableSet(executorMap.keySet());
    }

    /**
     * 获取已注册函数的数量.
     *
     * @return 函数数量
     */
    public int count() {
        return executorMap.size();
    }

    /**
     * 移除已注册的函数.
     *
     * @param name 函数名称
     */
    public void unregister(String name) {
        executorMap.remove(name);
        descriptionMap.remove(name);
        log.debug("移除 AI 函数: name={}", name);
    }

    /**
     * 清除所有已注册的函数.
     */
    public void clear() {
        executorMap.clear();
        descriptionMap.clear();
        log.debug("清除所有 AI 函数");
    }

    // ======================== 内部方法 ========================

    /**
     * 构建默认的参数 JSON Schema.
     *
     * <p>当用户没有明确指定参数定义时，使用一个宽松的 JSON Schema.</p>
     *
     * @return 参数定义的 Map
     */
    private Map<String, Object> buildDefaultParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", new LinkedHashMap<>());
        params.put("required", new ArrayList<>());
        return params;
    }
}
