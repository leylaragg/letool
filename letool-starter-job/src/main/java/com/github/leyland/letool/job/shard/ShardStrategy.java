package com.github.leyland.letool.job.shard;

/**
 * 任务分片策略——定义如何将任务分片分配给不同执行实例.
 *
 * <p>在分布式环境下，同一任务可能在多个实例上并发执行.
 * 不同实例通过实现此接口来决定各自负责处理哪些分片，
 * 从而避免数据重复处理并实现负载均衡.</p>
 *
 * <h3>典型分片场景</h3>
 * <pre>{@code
 * // 假设有100万条数据，分4片，3个实例
 * // 实例A 负责分片 0, 3
 * // 实例B 负责分片 1
 * // 实例C 负责分片 2
 *
 * ShardStrategy strategy = new ConsistentHashShard(150);
 * int[] myShards = strategy.getShardIndices("dataSyncJob", 4, "instance-A");
 * for (int shard : myShards) {
 *     int start = totalRecords / 4 * shard;
 *     int end = start + totalRecords / 4;
 *     processRecords(start, end);
 * }
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see ConsistentHashShard
 */
public interface ShardStrategy {

    // ======================== 核心方法 ========================

    /**
     * 获取当前实例应负责的分片索引数组.
     *
     * <p>根据任务名、总分片数和当前实例标识，
     * 计算出本实例应该处理的分片索引列表.</p>
     *
     * @param jobName      任务名称
     * @param totalShards  总分片数
     * @param instanceId   当前实例的唯一标识（如 IP:Port）
     * @return 本实例应处理的分片索引数组，不会为 {@code null}
     */
    int[] getShardIndices(String jobName, int totalShards, String instanceId);

    /**
     * 获取指定任务的总分片数.
     *
     * <p>不同任务可能使用不同的分片数，此方法允许策略根据任务特性动态分配分片数.</p>
     *
     * @param jobName 任务名称
     * @return 总分片数（&gt;=1）
     */
    int getShardTotal(String jobName);
}
