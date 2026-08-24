package io.github.leylaragg.letool.cache.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Set 缓存读取结果，显式区分权威数据与 Redis 故障降级结果。
 *
 * @param members 成员快照
 * @param state 结果状态
 * @param <V> Set 成员类型
 */
public record SetCacheReadResult<V>(Set<V> members, State state) {

    /**
     * 创建不可变读取结果，防止调用方修改缓存返回的快照。
     */
    public SetCacheReadResult {
        members = members == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(members));
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
    }

    /** Set 读取结果的可信状态。 */
    public enum State {
        /** 正常缓存路径返回的权威结果，包括 Redis 返回的空集合。 */
        AUTHORITATIVE,
        /** Redis 故障后返回已有 L1 快照，数据可能已经过期。 */
        STALE,
        /** Redis 故障且策略要求返回空集合。 */
        FAILURE_EMPTY
    }
}
