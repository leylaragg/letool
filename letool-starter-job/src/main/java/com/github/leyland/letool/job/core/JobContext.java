package com.github.leyland.letool.job.core;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 任务执行上下文——封装一次任务执行的所有运行时信息.
 *
 * <p>每次任务触发时，调度器会创建一个新的 {@code JobContext} 实例，
 * 包含此次执行的唯一标识、分片信息、自定义参数以及开始时间.
 * 该上下文对象被传递给 {@link JobHandler#execute(JobContext)} 方法.</p>
 *
 * <h3>上下文字段说明</h3>
 * <ul>
 *   <li>{@link #jobName} — 任务名称，标识当前执行属于哪个任务</li>
 *   <li>{@link #executionId} — 本次执行的唯一标识（UUID），用于日志追踪</li>
 *   <li>{@link #shardIndex} — 分片索引，当前实例处理的分片编号（从0开始）</li>
 *   <li>{@link #shardTotal} — 总分片数，表示整个任务被拆分为几个子任务</li>
 *   <li>{@link #params} — 自定义参数，任务注册时设置的键值对</li>
 *   <li>{@link #startTime} — 执行开始时间</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public void execute(JobContext context) {
 *     String userId = (String) context.getParam("userId");
 *     log.info("开始执行任务 {}, 执行ID: {}, 分片: {}/{}",
 *             context.getJobName(), context.getExecutionId(),
 *             context.getShardIndex() + 1, context.getShardTotal());
 *     // 使用分片信息进行数据分区处理
 *     int totalRecords = 10000;
 *     int batchSize = totalRecords / context.getShardTotal();
 *     int start = context.getShardIndex() * batchSize;
 *     int end = start + batchSize;
 *     processRecords(start, end);
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see JobHandler
 * @see JobResult
 */
public class JobContext {

    // ======================== 成员变量 ========================

    /**
     * 任务名称.
     */
    private final String jobName;

    /**
     * 本次执行的唯一标识.
     */
    private final String executionId;

    /**
     * 分片索引（从0开始）.
     */
    private final int shardIndex;

    /**
     * 总分片数.
     */
    private final int shardTotal;

    /**
     * 自定义参数（不可变）.
     */
    private final Map<String, Object> params;

    /**
     * 执行开始时间.
     */
    private final LocalDateTime startTime;

    // ======================== 构造方法 ========================

    /**
     * 创建任务执行上下文.
     *
     * @param jobName    任务名称
     * @param shardIndex 分片索引（从0开始）
     * @param shardTotal 总分片数
     * @param params     自定义参数，可为 {@code null}
     */
    public JobContext(String jobName, int shardIndex, int shardTotal, Map<String, Object> params) {
        this.jobName = jobName;
        this.executionId = UUID.randomUUID().toString().replace("-", "");
        this.shardIndex = shardIndex;
        this.shardTotal = shardTotal > 0 ? shardTotal : 1;
        this.params = params != null ? Collections.unmodifiableMap(params) : Collections.emptyMap();
        this.startTime = LocalDateTime.now();
    }

    // ======================== 公共方法 ========================

    /**
     * 获取任务名称.
     *
     * @return 任务名称
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * 获取本次执行的唯一标识.
     *
     * @return 执行ID（去连字符的UUID）
     */
    public String getExecutionId() {
        return executionId;
    }

    /**
     * 获取分片索引（从0开始）.
     *
     * @return 分片索引
     */
    public int getShardIndex() {
        return shardIndex;
    }

    /**
     * 获取总分片数.
     *
     * @return 总分片数，最小为1
     */
    public int getShardTotal() {
        return shardTotal;
    }

    /**
     * 获取所有自定义参数（不可变Map）.
     *
     * @return 自定义参数Map，不会为 {@code null}
     */
    public Map<String, Object> getParams() {
        return params;
    }

    /**
     * 获取执行开始时间.
     *
     * @return 开始时间
     */
    public LocalDateTime getStartTime() {
        return startTime;
    }

    /**
     * 根据键获取单个参数值.
     *
     * <p>如果指定键不存在，返回 {@code null}. 建议调用方自行做好空值判断.</p>
     *
     * @param key 参数键
     * @return 参数值，不存在返回 {@code null}
     */
    public Object getParam(String key) {
        return params.get(key);
    }

    /**
     * 根据键获取参数值并转换为指定类型.
     *
     * <p>如果键不存在或类型不匹配则返回 {@link Optional#empty()}. 注意该转换使用强制类型转换，
     * 不进行自动类型映射.</p>
     *
     * @param key   参数键
     * @param clazz 期望的类型
     * @param <T>   泛型类型
     * @return 包含转换后值的 Optional
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getParam(String key, Class<T> clazz) {
        Object value = params.get(key);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of((T) value);
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }

    // ======================== 标准方法 ========================

    @Override
    public String toString() {
        return "JobContext{" +
                "jobName='" + jobName + '\'' +
                ", executionId='" + executionId + '\'' +
                ", shardIndex=" + shardIndex +
                ", shardTotal=" + shardTotal +
                ", startTime=" + startTime +
                '}';
    }
}
