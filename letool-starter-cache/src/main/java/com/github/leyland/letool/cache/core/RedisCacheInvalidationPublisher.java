package com.github.leyland.letool.cache.core;

import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基于 Redis pub/sub 的 L1 失效消息发布器。
 *
 * <p>缓存写入或删除成功后，会通过本类把失效消息发布到统一频道。其它 JVM 收到消息后只清理自己的 L1，
 * 不会修改 Redis L2。</p>
 *
 * <p>发布失败不会中断业务写入流程。原因是 Redis 中的主数据和版本号已经更新，强一致模式下其它 JVM
 * 下一次读取 L1 时也会通过版本校验发现旧值不可用；广播只是让最终一致模式和本地副本更快收敛。</p>
 */
public class RedisCacheInvalidationPublisher implements CacheInvalidationPublisher {

    /** 默认失效广播频道。业务可以通过 letool.cache.invalidation.channel 覆盖。 */
    public static final String DEFAULT_CHANNEL = "letool:cache:invalidation";

    private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidationPublisher.class);

    /** Redis 操作入口，内部使用 StringRedisTemplate 的 convertAndSend。 */
    private final RedisUtil redisUtil;
    /** 当前发布器使用的 Redis pub/sub 频道。 */
    private final String channel;

    public RedisCacheInvalidationPublisher(RedisUtil redisUtil) {
        this(redisUtil, DEFAULT_CHANNEL);
    }

    public RedisCacheInvalidationPublisher(RedisUtil redisUtil, String channel) {
        this.redisUtil = redisUtil;
        this.channel = channel;
    }

    @Override
    public void publish(CacheInvalidationMessage message) {
        if (redisUtil == null || message == null) {
            return;
        }
        try {
            // payload 由 CacheInvalidationMessage 自己负责序列化，发布器只负责投递。
            redisUtil.getTemplate().convertAndSend(channel, message.toPayload());
        } catch (Exception e) {
            log.warn("Failed to publish cache invalidation message for cache [{}]", message.getCacheName(), e);
        }
    }

    public String getChannel() {
        return channel;
    }
}
