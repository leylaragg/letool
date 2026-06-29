package com.github.leyland.letool.rule.component;

import com.github.leyland.letool.rule.context.RuleContext;

/**
 * 规则节点抽象基类 —— 所有规则引擎节点的父类，定义节点的标准生命周期和执行契约.
 *
 * <h3>设计说明</h3>
 * <p>每个规则节点对应规则链中的一个执行步骤。节点通过
 * {@link com.github.leyland.letool.rule.annotation.RuleComponent @RuleComponent}
 * 注解标记后，由规则引擎自动发现和实例化.</p>
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>{@link #init()} —— 引擎启动时调用，节点进行初始化准备</li>
 *   <li>{@link #process(RuleContext)} —— 每次执行规则链时调用，执行核心逻辑</li>
 *   <li>{@link #destroy()} —— 引擎关闭时调用，节点释放资源</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @RuleComponent("riskScoreCalculator")
 * public class RiskScoreCalculator extends NodeComponent {
 *
 *     @Override
 *     public void process(RuleContext context) {
 *         Integer score = context.getParam("score");
 *         if (score > 80) {
 *             context.setResult("riskLevel", "HIGH");
 *         }
 *     }
 *
 *     @Override
 *     public String getName() {
 *         return "riskScoreCalculator";
 *     }
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see com.github.leyland.letool.rule.annotation.RuleComponent
 * @see com.github.leyland.letool.rule.engine.RuleEngine
 */
public abstract class NodeComponent {

    /**
     * 执行该节点的核心业务逻辑.
     *
     * <p>子类必须实现此方法，在方法中完成具体的规则处理。可以通过
     * {@link RuleContext} 读取输入参数、写入中间结果.</p>
     *
     * @param context 规则执行上下文，承载输入参数和中间结果
     */
    public abstract void process(RuleContext context);

    /**
     * 获取节点名称.
     *
     * <p>默认返回类名，子类可重写以返回自定义名称。此名称用于在规则链
     * YAML/JSON 定义中引用该节点.</p>
     *
     * @return 节点名称
     */
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 条件判断 —— 用于 IF 类型节点的条件评估.
     *
     * <p>子类可重写此方法提供条件逻辑。默认返回 true（表示条件始终满足）.
     * 当节点类型为 IF 时，规则引擎会先调用此方法判断是否执行.</p>
     *
     * @param context 规则执行上下文
     * @return true 表示条件满足，继续执行；false 表示跳过此节点
     */
    public boolean condition(RuleContext context) {
        return true;
    }

    // ======================== 生命周期方法 ========================

    /**
     * 节点初始化 —— 在规则引擎启动时调用.
     *
     * <p>子类可重写此方法进行资源初始化（如建立数据库连接、加载配置文件等）.
     * 默认实现为空.</p>
     */
    public void init() {
        // 默认空实现，子类按需重写
    }

    /**
     * 节点销毁 —— 在规则引擎关闭时调用.
     *
     * <p>子类可重写此方法释放资源（如关闭连接、清理缓存等）.
     * 默认实现为空.</p>
     */
    public void destroy() {
        // 默认空实现，子类按需重写
    }
}
