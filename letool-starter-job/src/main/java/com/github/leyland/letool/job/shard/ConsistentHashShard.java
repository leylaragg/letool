package com.github.leyland.letool.job.shard;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 一致性哈希分片策略——基于一致性哈希环将分片分配给不同实例.
 *
 * <p>使用一致性哈希算法将每个分片映射到哈希环上的某个虚拟节点位置，
 * 然后根据实例在环上的位置决定每个实例负责的分片.
 * 当实例增加或减少时，仅影响相邻节点的分片分配，最小化重分配范围.</p>
 *
 * <h3>设计原理</h3>
 * <ol>
 *   <li>为每个实例创建 {@code virtualNodes} 个虚拟节点，均匀分布在哈希环上</li>
 *   <li>使用 TreeMap 维护哈希环（key=hash值, value=实例ID）</li>
 *   <li>每个分片按顺时针方向找到最近的实例节点作为其所有者</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ConsistentHashShard shard = new ConsistentHashShard(150);
 * shard.addInstance("192.168.1.1:8080");
 * shard.addInstance("192.168.1.2:8080");
 * shard.addInstance("192.168.1.3:8080");
 *
 * int[] myShards = shard.getShardIndices("myJob", 8, "192.168.1.1:8080");
 * // 返回实例1负责的分片索引数组
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 * @see ShardStrategy
 */
public class ConsistentHashShard implements ShardStrategy {

    // ======================== 常量 ========================

    /**
     * 每个实例的虚拟节点数量.
     */
    private final int virtualNodes;

    // ======================== 成员变量 ========================

    /**
     * 哈希环：hash值 → 实例ID.
     */
    private final TreeMap<Integer, String> ring = new TreeMap<>();

    /**
     * 已注册的实例集合.
     */
    private final Set<String> instances = ConcurrentHashMap.newKeySet();

    /**
     * 任务到分片数的映射.
     */
    private final ConcurrentHashMap<String, Integer> shardTotalMap = new ConcurrentHashMap<>();

    // ======================== 构造方法 ========================

    /**
     * 创建一致性哈希分片策略.
     *
     * @param virtualNodes 每个实例的虚拟节点数（建议128~256，越大分布越均匀）
     */
    public ConsistentHashShard(int virtualNodes) {
        this.virtualNodes = Math.max(virtualNodes, 1);
    }

    /**
     * 创建使用默认150个虚拟节点的一致性哈希分片策略.
     */
    public ConsistentHashShard() {
        this(150);
    }

    // ======================== 实例管理 ========================

    /**
     * 向哈希环添加一个实例.
     *
     * <p>为该实例创建 {@code virtualNodes} 个虚拟节点并插入哈希环.
     * 如果实例已存在则忽略.</p>
     *
     * @param instanceId 实例标识（如 IP:Port）
     * @return {@code true} 如果成功添加，{@code false} 如果实例已存在
     */
    public boolean addInstance(String instanceId) {
        if (!instances.add(instanceId)) {
            return false;
        }
        synchronized (ring) {
            for (int i = 0; i < virtualNodes; i++) {
                String virtualNodeName = instanceId + "#VN" + i;
                int hash = hash(virtualNodeName);
                ring.put(hash, instanceId);
            }
        }
        return true;
    }

    /**
     * 从哈希环移除一个实例.
     *
     * <p>移除该实例的所有虚拟节点. 原该实例负责的分片
     * 将被顺时针方向的下一实例接管.</p>
     *
     * @param instanceId 实例标识
     * @return {@code true} 如果成功移除，{@code false} 如果实例不存在
     */
    public boolean removeInstance(String instanceId) {
        if (!instances.remove(instanceId)) {
            return false;
        }
        synchronized (ring) {
            ring.entrySet().removeIf(entry -> instanceId.equals(entry.getValue()));
        }
        return true;
    }

    /**
     * 获取当前已注册的实例列表.
     *
     * @return 实例ID列表
     */
    public List<String> getInstances() {
        return new ArrayList<>(instances);
    }

    // ======================== ShardStrategy 实现 ========================

    /**
     * 获取指定实例应负责的分片索引数组.
     *
     * <p>遍历所有分片，通过哈希环查找每个分片的所有者，
     * 收集属于 targetInstanceId 的分片索引.</p>
     *
     * @param jobName      任务名称
     * @param totalShards  总分片数
     * @param instanceId   目标实例标识
     * @return 分片索引数组
     */
    @Override
    public int[] getShardIndices(String jobName, int totalShards, String instanceId) {
        if (instances.isEmpty() || totalShards <= 0) {
            return new int[0];
        }
        List<Integer> ownedShards = new ArrayList<>();
        synchronized (ring) {
            for (int i = 0; i < totalShards; i++) {
                String shardKey = jobName + "#shard" + i;
                int hash = hash(shardKey);
                String owner = findOwner(hash);
                if (instanceId.equals(owner)) {
                    ownedShards.add(i);
                }
            }
        }
        return ownedShards.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * 获取指定任务的总分片数.
     *
     * <p>从内部映射中查找，未设置时返回默认值1.</p>
     *
     * @param jobName 任务名称
     * @return 总分片数
     */
    @Override
    public int getShardTotal(String jobName) {
        return shardTotalMap.getOrDefault(jobName, 1);
    }

    /**
     * 设置指定任务的总分片数.
     *
     * @param jobName     任务名称
     * @param shardTotal  总分片数
     */
    public void setShardTotal(String jobName, int shardTotal) {
        shardTotalMap.put(jobName, shardTotal);
    }

    // ======================== 内部方法 ========================

    /**
     * 根据哈希值在环上找到归属实例（顺时针方向最近的实例）.
     *
     * <p>使用 TreeMap 的 ceilingEntry 找到后继节点.
     * 如果当前哈希值超过环上所有节点，则回到环起点（尾环闭合）.</p>
     *
     * @param hash 目标哈希值
     * @return 归属实例ID，环为空时返回 {@code null}
     */
    private String findOwner(int hash) {
        if (ring.isEmpty()) {
            return null;
        }
        Map.Entry<Integer, String> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            // 超过最大值，回到环起点
            entry = ring.firstEntry();
        }
        return entry.getValue();
    }

    /**
     * 简单的哈希函数（FNV-1a 算法变体）.
     *
     * <p>对字符串进行哈希计算，返回非负整数.</p>
     *
     * @param key 待哈希的字符串
     * @return 哈希值
     */
    private int hash(String key) {
        int h = 0x811c9dc5;
        for (int i = 0; i < key.length(); i++) {
            h = (h * 16777619) ^ key.charAt(i);
            h += h << 13;
            h ^= h >> 7;
            h += h << 3;
            h ^= h >> 17;
            h += h << 5;
        }
        return h & 0x7fffffff;
    }
}
