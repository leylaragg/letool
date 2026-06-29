package com.github.leyland.letool.rule.context;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 规则引擎执行上下文 —— 承载单次规则链执行过程中的所有输入参数、中间结果和链路追踪信息.
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>线程安全的数据容器：使用 {@link ConcurrentHashMap} 存储参数和结果</li>
 *   <li>完整的执行追踪：通过 {@link NodeTrace} 记录每个节点的执行轨迹</li>
 *   <li>隔离性：每次执行创建独立的上下文实例，互不干扰</li>
 * </ul>
 *
 * <h3>生命周期</h3>
 * <ol>
 *   <li>创建：在 {@link com.github.leyland.letool.rule.engine.RuleEngine#execute RuleEngine.execute()}
 *       调用时创建，自动生成唯一的 {@code executionId}</li>
 *   <li>使用：在规则链的每个节点中读写参数和结果</li>
 *   <li>回收：执行完成后可作为 {@link RuleResult} 的一部分返回，或随 GC 回收</li>
 * </ol>
 *
 * <h3>数据分区</h3>
 * <ul>
 *   <li>{@code params} —— 调用方传入的输入参数，节点从中读取</li>
 *   <li>{@code results} —— 节点产生的中间结果，供后续节点使用</li>
 *   <li>{@code traces} —— 自动记录的执行轨迹，用于调试和监控</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 * @see RuleResult
 * @see com.github.leyland.letool.rule.engine.RuleEngine
 */
public class RuleContext {

    /** 当前执行的规则链名称 */
    private final String chainName;

    /** 本次执行的唯一标识（UUID） */
    private final String executionId;

    /** 输入参数（线程安全） */
    private final Map<String, Object> params;

    /** 节点产生的中间结果（线程安全） */
    private final Map<String, Object> results;

    /** 执行轨迹列表（线程安全） */
    private final List<NodeTrace> traces;

    // ======================== 构造方法 ========================

    /**
     * 创建规则执行上下文.
     *
     * @param chainName 规则链名称
     */
    public RuleContext(String chainName) {
        this.chainName = chainName;
        this.executionId = UUID.randomUUID().toString();
        this.params = new ConcurrentHashMap<>();
        this.results = new ConcurrentHashMap<>();
        this.traces = Collections.synchronizedList(new ArrayList<>());
    }

    /**
     * 创建带初始参数的规则执行上下文.
     *
     * @param chainName    规则链名称
     * @param initialParams 初始输入参数
     */
    public RuleContext(String chainName, Map<String, Object> initialParams) {
        this(chainName);
        if (initialParams != null) {
            this.params.putAll(initialParams);
        }
    }

    // ======================== 参数操作 ========================

    /**
     * 设置输入参数.
     *
     * @param key   参数名
     * @param value 参数值
     */
    public void setParam(String key, Object value) {
        this.params.put(key, value);
    }

    /**
     * 获取输入参数.
     *
     * @param key 参数名
     * @return 参数值，不存在时返回 null
     */
    public Object getParam(String key) {
        return this.params.get(key);
    }

    /**
     * 获取输入参数并转换为指定类型.
     *
     * @param key   参数名
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 转换后的值，不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, Class<T> clazz) {
        Object value = this.params.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 获取所有输入参数.
     *
     * @return 参数的不可变副本
     */
    public Map<String, Object> getParams() {
        return Collections.unmodifiableMap(this.params);
    }

    // ======================== 结果操作 ========================

    /**
     * 设置中间结果，供后续节点使用.
     *
     * @param key   结果键名
     * @param value 结果值
     */
    public void setResult(String key, Object value) {
        this.results.put(key, value);
    }

    /**
     * 获取中间结果.
     *
     * @param key 结果键名
     * @return 结果值，不存在时返回 null
     */
    public Object getResult(String key) {
        return this.results.get(key);
    }

    /**
     * 获取中间结果并转换为指定类型.
     *
     * @param key   结果键名
     * @param clazz 目标类型
     * @param <T>   目标泛型
     * @return 转换后的值，不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getResult(String key, Class<T> clazz) {
        Object value = this.results.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 获取所有中间结果.
     *
     * @return 结果的不可变副本
     */
    public Map<String, Object> getResults() {
        return Collections.unmodifiableMap(this.results);
    }

    // ======================== 执行追踪 ========================

    /**
     * 添加一条执行轨迹记录.
     *
     * @param trace 节点执行轨迹
     */
    public void addTrace(NodeTrace trace) {
        this.traces.add(trace);
    }

    /**
     * 获取所有执行轨迹.
     *
     * @return 轨迹列表的不可变副本
     */
    public List<NodeTrace> getTraces() {
        return Collections.unmodifiableList(this.traces);
    }

    // ======================== getter ========================

    /**
     * 获取规则链名称.
     *
     * @return 规则链名称
     */
    public String getChainName() {
        return chainName;
    }

    /**
     * 获取执行 ID.
     *
     * @return UUID 格式的执行唯一标识
     */
    public String getExecutionId() {
        return executionId;
    }

    // ======================== 内部类：节点执行轨迹 ========================

    /**
     * 单个规则节点的执行轨迹 —— 记录节点名称、开始/结束时间、输入/输出、成功状态等.
     *
     * <p>每次节点执行都会生成一条轨迹，用于调试规则链和性能分析.</p>
     */
    public static class NodeTrace {

        /** 节点名称 */
        private final String nodeName;

        /** 开始时间（epoch 毫秒） */
        private final long startTime;

        /** 结束时间（epoch 毫秒），失败时为异常发生时间 */
        private long endTime;

        /** 节点输入数据快照 */
        private Map<String, Object> input;

        /** 节点输出数据快照 */
        private Map<String, Object> output;

        /** 执行是否成功 */
        private boolean success;

        /** 失败时的错误信息 */
        private String errorMessage;

        /**
         * 创建执行轨迹.
         *
         * @param nodeName 节点名称
         */
        public NodeTrace(String nodeName) {
            this.nodeName = nodeName;
            this.startTime = Instant.now().toEpochMilli();
            this.success = true;
        }

        /**
         * 标记执行完成.
         */
        public void complete() {
            this.endTime = Instant.now().toEpochMilli();
        }

        /**
         * 标记执行失败.
         *
         * @param errorMessage 错误信息
         */
        public void fail(String errorMessage) {
            this.endTime = Instant.now().toEpochMilli();
            this.success = false;
            this.errorMessage = errorMessage;
        }

        /**
         * 计算节点耗时.
         *
         * @return 耗时毫秒数
         */
        public long getDurationMs() {
            return endTime - startTime;
        }

        // ======================== getter / setter ========================

        public String getNodeName() { return nodeName; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public Map<String, Object> getInput() { return input; }
        public void setInput(Map<String, Object> input) { this.input = input; }
        public Map<String, Object> getOutput() { return output; }
        public void setOutput(Map<String, Object> output) { this.output = output; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
