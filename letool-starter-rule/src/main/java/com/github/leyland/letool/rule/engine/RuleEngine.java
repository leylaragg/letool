package com.github.leyland.letool.rule.engine;

import com.github.leyland.letool.rule.chain.ChainDefinition;
import com.github.leyland.letool.rule.chain.ChainManager;
import com.github.leyland.letool.rule.component.NodeComponent;
import com.github.leyland.letool.rule.context.RuleContext;
import com.github.leyland.letool.rule.context.RuleResult;
import com.github.leyland.letool.rule.exception.RuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 规则引擎核心 —— 负责按规则链定义编排执行各个节点组件.
 *
 * <h3>执行机制</h3>
 * <p>规则引擎根据 {@link ChainDefinition} 中的节点树结构，按类型执行各节点：</p>
 * <ul>
 *   <li><b>THEN</b> —— 顺序执行所有子节点，前一个成功才执行下一个</li>
 *   <li><b>WHEN</b> —— 并行执行所有子节点（通过 {@link CompletableFuture}），
 *       等待全部完成后继续</li>
 *   <li><b>IF</b> —— 先调用节点的 {@link NodeComponent#condition(RuleContext)} 方法，
 *       条件为 true 时才执行子节点</li>
 *   <li><b>SWITCH</b> —— 根据条件值选择匹配的子节点执行</li>
 *   <li><b>FOR</b> —— 对指定集合中的每个元素执行子节点</li>
 * </ul>
 *
 * <h3>上下文传递</h3>
 * <p>所有节点共享同一个 {@link RuleContext}，节点通过上下文读写参数和中间结果，
 * 实现数据在链中的流转.</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * RuleEngine engine = new RuleEngine(chainManager, groovyScriptEngine, componentRegistry);
 *
 * RuleContext context = new RuleContext("riskChain");
 * context.setParam("userId", 1001);
 *
 * RuleResult result = engine.execute("riskChain", context);
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see ChainManager
 * @see ChainDefinition
 * @see NodeComponent
 * @see RuleContext
 * @see RuleResult
 */
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    /** 规则链管理器（获取链定义） */
    private final ChainManager chainManager;

    /** Groovy 脚本引擎（执行条件表达式） */
    private final GroovyScriptEngine groovyScriptEngine;

    /** 节点组件注册表：节点名称 -> NodeComponent 实例 */
    private final Map<String, NodeComponent> componentRegistry;

    /** 并行执行线程池（WHEN 节点使用） */
    private final ExecutorService parallelExecutor;

    // ======================== 构造方法 ========================

    /**
     * 创建规则引擎.
     *
     * @param chainManager       规则链管理器
     * @param groovyScriptEngine Groovy 脚本引擎
     * @param componentRegistry  节点组件注册表
     */
    public RuleEngine(ChainManager chainManager, GroovyScriptEngine groovyScriptEngine,
                      Map<String, NodeComponent> componentRegistry) {
        this.chainManager = chainManager;
        this.groovyScriptEngine = groovyScriptEngine;
        this.componentRegistry = componentRegistry != null ? componentRegistry : new ConcurrentHashMap<>();
        this.parallelExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "rule-pool-" + r.hashCode());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 创建规则引擎（无 Groovy 脚本支持）.
     *
     * @param chainManager      规则链管理器
     * @param componentRegistry 节点组件注册表
     */
    public RuleEngine(ChainManager chainManager, Map<String, NodeComponent> componentRegistry) {
        this(chainManager, null, componentRegistry);
    }

    // ======================== 链执行入口 ========================

    /**
     * 执行指定名称的规则链.
     *
     * <p>从链管理器获取链定义，按序执行每个根节点。执行过程中自动记录
     * 每个节点的执行轨迹（成功/失败、耗时、输入/输出）.</p>
     *
     * @param chainName 规则链名称
     * @param context   执行上下文（含输入参数）
     * @return 执行结果（成功/失败、轨迹、输出数据）
     */
    public RuleResult execute(String chainName, RuleContext context) {
        ChainDefinition chain = chainManager.get(chainName);
        if (chain == null) {
            log.error("规则链不存在: {}", chainName);
            return RuleResult.fail(chainName, context.getExecutionId(),
                    "规则链不存在: " + chainName, context.getTraces());
        }

        log.info("开始执行规则链 [{}] executionId={}", chainName, context.getExecutionId());
        long startTime = System.currentTimeMillis();

        try {
            // 按序执行链的根节点
            for (ChainDefinition.NodeDefinition node : chain.getNodes()) {
                executeNode(node, context);
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("规则链 [{}] 执行完成，耗时 {}ms，节点数 {}", chainName, duration, context.getTraces().size());

            RuleResult result = RuleResult.success(chainName, context.getExecutionId(), context.getTraces());
            result.setTotalDurationMs(duration);
            result.setOutputs(new HashMap<>(context.getResults()));
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("规则链 [{}] 执行失败，耗时 {}ms: {}", chainName, duration, e.getMessage(), e);

            RuleResult result = RuleResult.fail(chainName, context.getExecutionId(),
                    e.getMessage(), context.getTraces());
            result.setTotalDurationMs(duration);
            return result;
        }
    }

    // ======================== 节点执行逻辑 ========================

    /**
     * 执行单个节点（递归处理子节点）.
     *
     * <p>根据节点类型选择不同的执行策略.</p>
     *
     * @param nodeDef 节点定义
     * @param context 执行上下文
     */
    private void executeNode(ChainDefinition.NodeDefinition nodeDef, RuleContext context) {
        if (nodeDef == null) {
            return;
        }

        String type = nodeDef.getType() != null ? nodeDef.getType().toUpperCase() : "THEN";

        switch (type) {
            case "THEN":
                executeThen(nodeDef, context);
                break;
            case "WHEN":
                executeWhen(nodeDef, context);
                break;
            case "IF":
                executeIf(nodeDef, context);
                break;
            case "SWITCH":
                executeSwitch(nodeDef, context);
                break;
            case "FOR":
                executeFor(nodeDef, context);
                break;
            default:
                // 无类型的普通节点，直接执行组件
                executeComponent(nodeDef, context);
                break;
        }
    }

    /**
     * 顺序执行（THEN）—— 依次执行所有子节点.
     *
     * @param nodeDef 节点定义
     * @param context 执行上下文
     */
    private void executeThen(ChainDefinition.NodeDefinition nodeDef, RuleContext context) {
        // 如果有组件名称，先执行当前节点
        if (nodeDef.getName() != null && !nodeDef.getName().isEmpty()) {
            executeComponent(nodeDef, context);
        }
        // 再顺序执行子节点
        if (nodeDef.getChildren() != null) {
            for (ChainDefinition.NodeDefinition child : nodeDef.getChildren()) {
                executeNode(child, context);
            }
        }
    }

    /**
     * 并行执行（WHEN）—— 所有子节点并发执行.
     *
     * @param nodeDef 节点定义
     * @param context 执行上下文
     */
    private void executeWhen(ChainDefinition.NodeDefinition nodeDef, RuleContext context) {
        if (nodeDef.getChildren() == null || nodeDef.getChildren().isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ChainDefinition.NodeDefinition child : nodeDef.getChildren()) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                executeNode(child, context);
            }, parallelExecutor);
            futures.add(future);
        }

        // 等待所有并行任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("WHEN 节点并行执行失败: {}", e.getMessage(), e);
            throw new RuleException("EXEC_WHEN", "并行执行失败", nodeDef.getName(), e);
        }
    }

    /**
     * 条件分支执行（IF）—— 先判断条件再选择分支.
     *
     * @param nodeDef 节点定义
     * @param context 执行上下文
     */
    private void executeIf(ChainDefinition.NodeDefinition nodeDef, RuleContext context) {
        boolean conditionMet = evaluateCondition(nodeDef, context);

        if (conditionMet) {
            // 条件满足，执行子节点
            if (nodeDef.getChildren() != null) {
                for (ChainDefinition.NodeDefinition child : nodeDef.getChildren()) {
                    executeNode(child, context);
                }
            }
        } else {
            log.debug("IF 节点 [{}] 条件不满足，跳过", nodeDef.getName());
        }
    }

    /**
     * 多路分支执行（SWITCH）—— 根据条件值选择匹配的子节点.
     *
     * @param nodeDef 节点定义
     * @param context 执行上下文
     */
    private void executeSwitch(ChainDefinition.NodeDefinition nodeDef, RuleContext context) {
        String conditionResult = evaluateStringCondition(nodeDef, context);

        if (nodeDef.getChildren() == null) {
            return;
        }

        for (ChainDefinition.NodeDefinition child : nodeDef.getChildren()) {
            // 匹配与条件值同名的子节点
            if (conditionResult != null && conditionResult.equals(child.getName())) {
                executeNode(child, context);
                return;
            }
        }

        log.debug("SWITCH 节点 [{}] 无匹配分支，conditionResult={}", nodeDef.getName(), conditionResult);
    }

    /**
     * 循环执行（FOR）—— 遍历集合并对每个元素执行子节点.
     *
     * @param nodeDef 节点定义
     * @param context 执行上下文
     */
    @SuppressWarnings("unchecked")
    private void executeFor(ChainDefinition.NodeDefinition nodeDef, RuleContext context) {
        // 从上下文中获取循环集合
        String collectionKey = nodeDef.getCondition(); // condition 字段存储集合的 key
        Object collection = context.getParam(collectionKey);
        if (collection == null) {
            collection = context.getResult(collectionKey);
        }

        if (collection instanceof Iterable) {
            for (Object item : (Iterable<?>) collection) {
                context.setParam("_for_item", item); // 当前迭代元素
                if (nodeDef.getChildren() != null) {
                    for (ChainDefinition.NodeDefinition child : nodeDef.getChildren()) {
                        executeNode(child, context);
                    }
                }
            }
            context.setParam("_for_item", null); // 清理
        } else if (collection instanceof Object[]) {
            for (Object item : (Object[]) collection) {
                context.setParam("_for_item", item);
                if (nodeDef.getChildren() != null) {
                    for (ChainDefinition.NodeDefinition child : nodeDef.getChildren()) {
                        executeNode(child, context);
                    }
                }
            }
            context.setParam("_for_item", null);
        } else {
            log.warn("FOR 节点 [{}] 的集合数据不存在或类型不支持: {}", nodeDef.getName(), collectionKey);
        }
    }

    // ======================== 组件执行 ========================

    /**
     * 执行一个节点组件（有名称的叶子节点）.
     *
     * <p>从注册表中查找对应的 {@link NodeComponent}，执行其 process 方法，
     * 并记录执行轨迹.</p>
     *
     * @param nodeDef 节点定义
     * @param context 执行上下文
     */
    private void executeComponent(ChainDefinition.NodeDefinition nodeDef, RuleContext context) {
        String name = nodeDef.getName();
        NodeComponent component = componentRegistry.get(name);

        RuleContext.NodeTrace trace = new RuleContext.NodeTrace(name);

        // 记录输入快照
        trace.setInput(new HashMap<>(context.getParams()));

        if (component == null) {
            trace.fail("节点组件未注册: " + name);
            context.addTrace(trace);
            log.error("节点组件未注册: {}", name);
            throw new RuleException("EXEC_COMP", "节点组件未注册: " + name, name, null);
        }

        try {
            // 执行组件
            component.process(context);
            trace.complete();
            // 记录输出快照
            trace.setOutput(new HashMap<>(context.getResults()));
            context.addTrace(trace);
            log.debug("节点 [{}] 执行成功，耗时 {}ms", name, trace.getDurationMs());

        } catch (Exception e) {
            trace.fail(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            context.addTrace(trace);
            log.error("节点 [{}] 执行失败: {}", name, e.getMessage(), e);
            throw new RuleException("EXEC_NODE", "节点执行失败: " + name, name, e);
        }
    }

    // ======================== 条件评估 ========================

    /**
     * 评估节点条件（返回 boolean）.
     *
     * <p>优先通过 Groovy 脚本引擎评估条件表达式，如果 Groovy 不可用，
     * 则尝试调用组件的 {@link NodeComponent#condition(RuleContext)} 方法.</p>
     *
     * @param nodeDef 节点定义
     * @param context 执行上下文
     * @return true 表示条件满足
     */
    private boolean evaluateCondition(ChainDefinition.NodeDefinition nodeDef, RuleContext context) {
        String condition = nodeDef.getCondition();
        if (condition == null || condition.trim().isEmpty()) {
            // 没有条件表达式，尝试调用组件的 condition 方法
            NodeComponent component = componentRegistry.get(nodeDef.getName());
            if (component != null) {
                return component.condition(context);
            }
            return true;
        }

        // 通过 Groovy 脚本引擎评估
        if (groovyScriptEngine != null && groovyScriptEngine.isGroovyAvailable()) {
            String scriptName = "_condition_" + nodeDef.getName() + "_" + context.getExecutionId();
            groovyScriptEngine.registerScript(scriptName, condition);
            Object result = groovyScriptEngine.executeScript(scriptName, context);
            groovyScriptEngine.invalidateCache(scriptName);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
            return result != null;
        }

        return true;
    }

    /**
     * 评估节点条件（返回字符串，用于 SWITCH 匹配）.
     *
     * @param nodeDef 节点定义
     * @param context 执行上下文
     * @return 条件评估的字符串结果
     */
    private String evaluateStringCondition(ChainDefinition.NodeDefinition nodeDef, RuleContext context) {
        String condition = nodeDef.getCondition();
        if (condition == null || condition.trim().isEmpty()) {
            return null;
        }

        if (groovyScriptEngine != null && groovyScriptEngine.isGroovyAvailable()) {
            String scriptName = "_switch_" + nodeDef.getName() + "_" + context.getExecutionId();
            groovyScriptEngine.registerScript(scriptName, condition);
            Object result = groovyScriptEngine.executeScript(scriptName, context);
            groovyScriptEngine.invalidateCache(scriptName);
            return result != null ? result.toString() : null;
        }

        return condition;
    }

    // ======================== 生命周期管理 ========================

    /**
     * 注册一个节点组件.
     *
     * @param name      组件名称
     * @param component 组件实例
     */
    public void registerComponent(String name, NodeComponent component) {
        componentRegistry.put(name, component);
        component.init();
        log.info("节点组件已注册: {}", name);
    }

    /**
     * 关闭规则引擎，释放资源.
     *
     * <p>销毁所有注册的节点组件并关闭线程池.</p>
     */
    public void shutdown() {
        log.info("正在关闭规则引擎...");
        for (NodeComponent component : componentRegistry.values()) {
            try {
                component.destroy();
            } catch (Exception e) {
                log.warn("节点销毁失败: {}", component.getName(), e);
            }
        }
        parallelExecutor.shutdown();
        log.info("规则引擎已关闭");
    }
}
