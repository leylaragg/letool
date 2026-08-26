package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.redis.RedisFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Objects;

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

    /** 只使用字符串序列化器的发布模板，避免业务对象序列化器改变协议字节。 */
    private final StringRedisTemplate redisTemplate;
    /** 当前发布器使用的 Redis pub/sub 频道。 */
    private final String channel;

    /**
     * 使用默认频道创建 Redis 失效消息发布器。
     *
     * @param redisFacade Redis 操作入口
     */
    public RedisCacheInvalidationPublisher(RedisFacade redisFacade) {
        this(redisFacade, DEFAULT_CHANNEL);
    }

    /**
     * 使用指定频道创建 Redis 失效消息发布器。
     *
     * @param redisFacade Redis 操作入口
     * @param channel Redis Pub/Sub 频道
     */
    public RedisCacheInvalidationPublisher(RedisFacade redisFacade, String channel) {
        this(createStringTemplate(redisFacade), channel);
    }

    /**
     * 使用专用字符串模板创建失效消息发布器。
     *
     * @param redisTemplate 只按 UTF-8 字符串编码频道和消息体的 Redis 模板
     * @param channel Redis Pub/Sub 频道
     */
    public RedisCacheInvalidationPublisher(StringRedisTemplate redisTemplate, String channel) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "Redis 字符串模板不能为空");
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Redis Pub/Sub 频道不能为空");
        }
        this.channel = channel;
    }

    /**
     * 发布缓存失效消息；发布失败只记录安全日志，不中断业务写入。
     *
     * @param message 待发布的缓存失效消息
     */
    @Override
    public void publish(CacheInvalidationMessage message) {
        if (message == null) {
            return;
        }
        try {
            // 专用字符串模板保证消息体不再经过 Fastjson2、Jackson 等业务值序列化器。
            redisTemplate.convertAndSend(channel, message.toPayload());
        } catch (Exception e) {
            log.warn(
                    "Failed to publish cache invalidation message for cache [{}], causeType={}",
                    message.getCacheName(),
                    e.getClass().getSimpleName()
            );
            log.debug("Cache invalidation message publishing detail", e);
        }
    }

    /** @return 当前 Redis Pub/Sub 频道 */
    public String getChannel() {
        return channel;
    }

    private static StringRedisTemplate createStringTemplate(RedisFacade redisFacade) {
        Objects.requireNonNull(redisFacade, "Redis 操作入口不能为空");
        RedisConnectionFactory connectionFactory = redisFacade.getTemplate().getConnectionFactory();
        if (connectionFactory == null) {
            throw new IllegalArgumentException("RedisTemplate 必须配置连接工厂");
        }
        return new StringRedisTemplate(connectionFactory);
    }
}
