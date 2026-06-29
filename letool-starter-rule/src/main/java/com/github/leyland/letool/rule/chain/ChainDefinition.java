package com.github.leyland.letool.rule.chain;

import com.github.leyland.letool.tool.util.JsonUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则链定义模型 —— 描述一条完整的规则链结构，包括链名称、描述和节点树.
 *
 * <h3>设计说明</h3>
 * <p>规则链定义是规则引擎的核心数据结构，描述了节点的编排方式。一条规则链
 * 由一个根节点开始，通过嵌套的 {@link NodeDefinition} 树表达执行流程.</p>
 *
 * <h3>节点类型</h3>
 * <ul>
 *   <li><b>THEN</b> —— 顺序执行，依次执行所有子节点</li>
 *   <li><b>WHEN</b> —— 并行执行，所有子节点并发执行</li>
 *   <li><b>IF</b> —— 条件分支，根据条件决定执行哪个子节点</li>
 *   <li><b>SWITCH</b> —— 多路分支，根据条件值选择执行路径</li>
 *   <li><b>FOR</b> —— 循环执行，对集合中的每个元素执行子节点</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 从 YAML 解析
 * ChainDefinition chain = ChainDefinition.fromYaml(yamlString);
 *
 * // 从 JSON 解析
 * ChainDefinition chain = ChainDefinition.fromJson(jsonString);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see ChainParser
 * @see ChainManager
 */
public class ChainDefinition {

    /** 规则链名称，全局唯一 */
    private String name;

    /** 规则链描述信息 */
    private String description;

    /** 根节点列表（链的入口节点） */
    private List<NodeDefinition> nodes;

    // ======================== 构造方法 ========================

    /**
     * 创建空的规则链定义.
     */
    public ChainDefinition() {
        this.nodes = new ArrayList<>();
    }

    /**
     * 创建带名称的规则链定义.
     *
     * @param name 规则链名称
     */
    public ChainDefinition(String name) {
        this.name = name;
        this.nodes = new ArrayList<>();
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 从 YAML 字符串解析规则链定义.
     *
     * @param yaml YAML 格式的规则链定义文本
     * @return 解析后的规则链定义
     * @throws com.github.leyland.letool.rule.exception.RuleException 解析失败时抛出
     */
    public static ChainDefinition fromYaml(String yaml) {
        ChainParser parser = new ChainParser();
        return parser.parseYaml(yaml);
    }

    /**
     * 从 JSON 字符串解析规则链定义.
     *
     * @param json JSON 格式的规则链定义文本
     * @return 解析后的规则链定义
     * @throws com.github.leyland.letool.rule.exception.RuleException 解析失败时抛出
     */
    public static ChainDefinition fromJson(String json) {
        return JsonUtil.parseObject(json, ChainDefinition.class);
    }

    // ======================== 便捷方法 ========================

    /**
     * 添加一个子节点到根节点列表.
     *
     * @param node 要添加的节点定义
     */
    public void addNode(NodeDefinition node) {
        this.nodes.add(node);
    }

    // ======================== getter / setter ========================

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<NodeDefinition> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeDefinition> nodes) {
        this.nodes = nodes;
    }

    // ======================== 内部类：节点定义 ========================

    /**
     * 规则节点定义 —— 描述规则链中单个节点的属性和子节点.
     *
     * <p>节点支持嵌套，通过 {@code children} 构建树状结构。每个节点可以携带
     * 自定义属性（{@code properties}），供执行时使用.</p>
     */
    public static class NodeDefinition {

        /** 节点名称，对应 {@link com.github.leyland.letool.rule.component.NodeComponent} 的名称 */
        private String name;

        /**
         * 节点类型：
         * <ul>
         *   <li>THEN —— 顺序执行</li>
         *   <li>WHEN —— 并行执行</li>
         *   <li>IF —— 条件分支</li>
         *   <li>SWITCH —— 多路分支</li>
         *   <li>FOR —— 循环执行</li>
         * </ul>
         */
        private String type;

        /** 条件表达式（IF/SWITCH 节点使用），支持 SpEL 或 Groovy 表达式 */
        private String condition;

        /** 子节点列表，支持嵌套 */
        private List<NodeDefinition> children;

        /** 自定义属性，在节点执行时可通过上下文获取 */
        private Map<String, Object> properties;

        // ======================== 构造方法 ========================

        /**
         * 创建空的节点定义.
         */
        public NodeDefinition() {
            this.children = new ArrayList<>();
            this.properties = new HashMap<>();
        }

        /**
         * 创建带名称的节点定义.
         *
         * @param name 节点名称
         */
        public NodeDefinition(String name) {
            this.name = name;
            this.children = new ArrayList<>();
            this.properties = new HashMap<>();
        }

        /**
         * 创建带名称和类型的节点定义.
         *
         * @param name 节点名称
         * @param type 节点类型（THEN/WHEN/IF/SWITCH/FOR）
         */
        public NodeDefinition(String name, String type) {
            this.name = name;
            this.type = type;
            this.children = new ArrayList<>();
            this.properties = new HashMap<>();
        }

        // ======================== 便捷方法 ========================

        /**
         * 添加一个子节点.
         *
         * @param child 子节点定义
         */
        public void addChild(NodeDefinition child) {
            this.children.add(child);
        }

        /**
         * 设置自定义属性.
         *
         * @param key   属性键
         * @param value 属性值
         */
        public void setProperty(String key, Object value) {
            this.properties.put(key, value);
        }

        /**
         * 获取自定义属性.
         *
         * @param key 属性键
         * @return 属性值，不存在时返回 null
         */
        public Object getProperty(String key) {
            return this.properties.get(key);
        }

        // ======================== getter / setter ========================

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }

        public List<NodeDefinition> getChildren() {
            return children;
        }

        public void setChildren(List<NodeDefinition> children) {
            this.children = children;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, Object> properties) {
            this.properties = properties;
        }
    }
}
