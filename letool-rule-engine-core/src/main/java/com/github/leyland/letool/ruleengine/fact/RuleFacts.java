package com.github.leyland.letool.ruleengine.fact;

import com.github.leyland.letool.ruleengine.api.EngineLimits;

import java.util.Map;
import java.util.Optional;

/**
 * 由宿主输入快照构造的不可变规则事实树。
 */
public final class RuleFacts {

    /** 供便捷路径 API 共享的无状态解析器。 */
    private static final FactResolver DEFAULT_RESOLVER = new FactResolver();

    /** 深层规范化并冻结的事实根对象。 */
    private final ObjectFactValue root;

    /** 接收已完成预算检查的根对象。 */
    private RuleFacts(ObjectFactValue root) {
        this.root = root;
    }

    /**
     * 从字符串键映射构造深层不可变事实快照。
     *
     * @param source 宿主事实映射
     * @return 不可变规则事实
     */
    public static RuleFacts fromMap(Map<String, ?> source) {
        return fromMap(source, EngineLimits.defaults());
    }

    /**
     * 在指定事实预算内构造深层不可变事实快照。
     * @param source 宿主事实映射
     * @param limits 资源限制
     * @return 不可变规则事实
     */
    public static RuleFacts fromMap(Map<String, ?> source, EngineLimits limits) {
        return new RuleFacts(FactValues.fromMap(source, limits));
    }

    /**
     * 按文本路径查询事实值。
     *
     * @param path 普通路径或完整插值路径
     * @return 查找结果
     */
    public Optional<FactValue> resolve(String path) {
        return DEFAULT_RESOLVER.resolve(this, FactPathParser.parse(path));
    }

    /**
     * 按文本路径读取必需事实值。
     *
     * @param path 普通路径或完整插值路径
     * @return 已找到的事实值
     */
    public FactValue require(String path) {
        return DEFAULT_RESOLVER.require(this, FactPathParser.parse(path));
    }

    /**
     * 为同包事实解析器提供根对象访问，避免扩大公开 API。
     *
     * @return 根对象事实
     */
    ObjectFactValue root() {
        return root;
    }

    /**
     * 返回不可修改的深层 Java 视图。
     *
     * @return 安全 Java 值
     */
    public Object toSafeJavaValue() {
        return root.toSafeJavaValue();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RuleFacts that && root.equals(that.root);
    }

    @Override
    public int hashCode() {
        return root.hashCode();
    }

}
