package com.github.leyland.letool.ai.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 函数定义模型 —— 描述一个可被大模型调用的函数.
 *
 * <h3>Function Calling 机制</h3>
 * <p>在对话请求中附带函数定义列表，大模型会根据用户意图决定是否需要调用函数.
 * 如果需要，返回函数名和 JSON 格式的参数，应用执行后把结果回传给大模型.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * FunctionDefinition def = FunctionDefinition.builder()
 *     .name("get_weather")
 *     .description("获取指定城市的天气信息")
 *     .addParameter("city", "string", "城市名，如'北京'、'上海'")
 *     .build();
 * }</pre>
 *
 * <h3>parameters 结构（JSON Schema）</h3>
 * <pre>{@code
 * {
 *   "type": "object",
 *   "properties": {
 *     "city": { "type": "string", "description": "城市名" }
 *   },
 *   "required": ["city"]
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class FunctionDefinition {

    // ======================== 字段 ========================

    /** 函数名称，全局唯一 */
    private String name;

    /** 函数描述，帮助大模型理解何时调用 */
    private String description;

    /** 函数参数定义（JSON Schema 格式） */
    private Map<String, Object> parameters;

    // ======================== 构造方法 ========================

    /** 默认构造 */
    public FunctionDefinition() {}

    /**
     * 创建函数定义.
     *
     * @param name        函数名称
     * @param description 函数描述
     */
    public FunctionDefinition(String name, String description) {
        this.name = name;
        this.description = description;
        this.parameters = new LinkedHashMap<>();
        this.parameters.put("type", "object");
        this.parameters.put("properties", new LinkedHashMap<>());
        this.parameters.put("required", new java.util.ArrayList<>());
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
     * FunctionDefinition 构建器 —— 支持链式调用.
     */
    public static class Builder {
        private String name;
        private String description;
        private Map<String, Object> properties = new LinkedHashMap<>();
        private java.util.List<String> required = new java.util.ArrayList<>();

        /**
         * 设置函数名称.
         *
         * @param name 函数名称
         * @return this
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * 设置函数描述.
         *
         * @param description 函数描述
         * @return this
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 添加一个参数定义.
         *
         * @param paramName   参数名称
         * @param type        参数类型（string / number / integer / boolean / object / array）
         * @param description 参数描述
         * @return this
         */
        @SuppressWarnings("unchecked")
        public Builder addParameter(String paramName, String type, String description) {
            Map<String, Object> paramDef = new LinkedHashMap<>();
            paramDef.put("type", type);
            paramDef.put("description", description);
            properties.put(paramName, paramDef);
            return this;
        }

        /**
         * 添加一个必填参数.
         *
         * @param paramName   参数名称
         * @param type        参数类型
         * @param description 参数描述
         * @return this
         */
        public Builder addRequiredParameter(String paramName, String type, String description) {
            addParameter(paramName, type, description);
            required.add(paramName);
            return this;
        }

        /**
         * 设置必填参数列表.
         *
         * @param requiredFields 必填参数名称数组
         * @return this
         */
        public Builder required(String... requiredFields) {
            this.required = new java.util.ArrayList<>();
            for (String field : requiredFields) {
                this.required.add(field);
            }
            return this;
        }

        /**
         * 直接设置完整的 parameters JSON Schema 对象.
         *
         * @param parameters JSON Schema 格式的参数定义
         * @return this
         */
        public Builder parameters(Map<String, Object> parameters) {
            // 使用传入的 parameters 覆盖已有配置
            return this;
        }

        /**
         * 构建 FunctionDefinition 实例.
         *
         * @return 构建好的函数定义对象
         */
        public FunctionDefinition build() {
            FunctionDefinition def = new FunctionDefinition();
            def.name = this.name;
            def.description = this.description;
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("type", "object");
            params.put("properties", this.properties);
            params.put("required", this.required);
            def.parameters = params;
            return def;
        }
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
     * 获取函数描述.
     *
     * @return 函数描述
     */
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /**
     * 获取参数定义（JSON Schema 格式）.
     *
     * @return 参数定义的 Map
     */
    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
