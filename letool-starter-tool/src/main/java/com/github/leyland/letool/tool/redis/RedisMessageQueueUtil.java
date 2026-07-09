package com.github.leyland.letool.tool.redis;

import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.BoundListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 消息队列工具类。
 *
 * <p>本工具类基于应用配置的 {@link RedisTemplate}，不内置 JSON 序列化，也不把消息强制转成字符串。
 * List 队列的元素、Stream 队列的消息体都会交给 RedisTemplate 当前的 serializer 处理。</p>
 *
 * <p>第一版覆盖两类常用场景：</p>
 * <ul>
 *     <li>Redis List：轻量 FIFO 队列，适合简单生产/消费。</li>
 *     <li>Redis Stream：支持消费者组和 ACK 的可靠队列。</li>
 * </ul>
 */
public class RedisMessageQueueUtil {

    /** 应用侧配置好的对象 RedisTemplate。 */
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 创建 Redis 消息队列工具类。
     *
     * @param redisTemplate 应用侧对象 RedisTemplate
     */
    public RedisMessageQueueUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取底层 RedisTemplate，用于调用工具类未封装的原生 Redis 消息能力。
     *
     * @return RedisTemplate 实例
     */
    public RedisTemplate<String, Object> getTemplate() {
        return redisTemplate;
    }

    /**
     * 向 List 队列尾部写入消息。
     *
     * @param queue Redis List key
     * @param message 消息对象，序列化由 RedisTemplate 决定
     * @return 写入后的队列长度；Redis 返回 {@code null} 时返回 0
     */
    public long offer(String queue, Object message) {
        Long size = boundListOps(queue).rightPush(message);
        return size == null ? 0 : size;
    }

    /**
     * 向 List 队列头部写入消息。
     *
     * @param queue Redis List key
     * @param message 消息对象，序列化由 RedisTemplate 决定
     * @return 写入后的队列长度；Redis 返回 {@code null} 时返回 0
     */
    public long offerFirst(String queue, Object message) {
        Long size = boundListOps(queue).leftPush(message);
        return size == null ? 0 : size;
    }

    /**
     * 从 List 队列头部立即弹出消息。
     *
     * @param queue Redis List key
     * @param <T> 调用方期望的消息类型
     * @return RedisTemplate 反序列化后的消息；队列为空时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T poll(String queue) {
        return (T) boundListOps(queue).leftPop();
    }

    /**
     * 从 List 队列头部阻塞弹出消息。
     *
     * @param queue Redis List key
     * @param timeout 阻塞等待时长
     * @param unit 时间单位
     * @param <T> 调用方期望的消息类型
     * @return RedisTemplate 反序列化后的消息；超时或队列为空时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T poll(String queue, long timeout, TimeUnit unit) {
        return (T) boundListOps(queue).leftPop(timeout, unit);
    }

    /**
     * 从 List 队列头部阻塞弹出消息。
     *
     * @param queue Redis List key
     * @param timeout 阻塞等待时长
     * @param <T> 调用方期望的消息类型
     * @return RedisTemplate 反序列化后的消息；超时或队列为空时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T poll(String queue, Duration timeout) {
        return (T) boundListOps(queue).leftPop(timeout);
    }

    /**
     * 获取 List 队列长度。
     *
     * @param queue Redis List key
     * @return 队列长度；Redis 返回 {@code null} 时返回 0
     */
    public long size(String queue) {
        Long size = boundListOps(queue).size();
        return size == null ? 0 : size;
    }

    /**
     * 向 Redis Stream 追加对象消息。
     *
     * @param stream Redis Stream key
     * @param message 消息对象，映射和序列化由 RedisTemplate/StreamOperations 决定
     * @return 新消息 RecordId
     */
    public RecordId add(String stream, Object message) {
        return streamOps().add(ObjectRecord.create(stream, message));
    }

    /**
     * 从 Redis Stream 读取对象消息。
     *
     * @param stream Redis Stream key
     * @param targetType 消息体目标类型
     * @param offset 读取偏移，例如 {@code 0-0}、{@code $}、具体 RecordId
     * @param count 单次最多读取数量，小于等于 0 时不限制
     * @param <T> 消息体类型
     * @return Stream 记录列表；Redis 返回 {@code null} 时返回空列表
     */
    public <T> List<ObjectRecord<String, T>> read(String stream, Class<T> targetType, String offset, long count) {
        return read(stream, targetType, offset, count, null);
    }

    /**
     * 从 Redis Stream 阻塞读取对象消息。
     *
     * @param stream Redis Stream key
     * @param targetType 消息体目标类型
     * @param offset 读取偏移，例如 {@code 0-0}、{@code $}、具体 RecordId
     * @param count 单次最多读取数量，小于等于 0 时不限制
     * @param block 阻塞等待时长；为 null 时不阻塞
     * @param <T> 消息体类型
     * @return Stream 记录列表；Redis 返回 {@code null} 时返回空列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<ObjectRecord<String, T>> read(String stream,
                                                  Class<T> targetType,
                                                  String offset,
                                                  long count,
                                                  Duration block) {
        List<ObjectRecord<String, T>> records = streamOps().read(
                targetType,
                readOptions(count, block),
                StreamOffset.create(stream, ReadOffset.from(offset)));
        return records == null ? Collections.emptyList() : records;
    }

    /**
     * 创建 Redis Stream 消费者组。
     *
     * @param stream Redis Stream key
     * @param group 消费者组名称
     * @param offset 建组起始偏移，例如 {@code 0-0} 表示从头消费，{@code $} 表示从新消息开始
     * @return Redis 返回的消费者组名称
     */
    public String createGroup(String stream, String group, String offset) {
        return streamOps().createGroup(stream, ReadOffset.from(offset), group);
    }

    /**
     * 从消费者组读取 Redis Stream 对象消息。
     *
     * @param stream Redis Stream key
     * @param group 消费者组名称
     * @param consumer 消费者名称
     * @param targetType 消息体目标类型
     * @param count 单次最多读取数量，小于等于 0 时不限制
     * @param <T> 消息体类型
     * @return Stream 记录列表；Redis 返回 {@code null} 时返回空列表
     */
    public <T> List<ObjectRecord<String, T>> readGroup(String stream,
                                                       String group,
                                                       String consumer,
                                                       Class<T> targetType,
                                                       long count) {
        return readGroup(stream, group, consumer, targetType, count, null);
    }

    /**
     * 从消费者组阻塞读取 Redis Stream 对象消息。
     *
     * @param stream Redis Stream key
     * @param group 消费者组名称
     * @param consumer 消费者名称
     * @param targetType 消息体目标类型
     * @param count 单次最多读取数量，小于等于 0 时不限制
     * @param block 阻塞等待时长；为 null 时不阻塞
     * @param <T> 消息体类型
     * @return Stream 记录列表；Redis 返回 {@code null} 时返回空列表
     */
    @SuppressWarnings("unchecked")
    public <T> List<ObjectRecord<String, T>> readGroup(String stream,
                                                       String group,
                                                       String consumer,
                                                       Class<T> targetType,
                                                       long count,
                                                       Duration block) {
        List<ObjectRecord<String, T>> records = streamOps().read(
                targetType,
                Consumer.from(group, consumer),
                readOptions(count, block),
                StreamOffset.create(stream, ReadOffset.lastConsumed()));
        return records == null ? Collections.emptyList() : records;
    }

    /**
     * 确认消费者组消息已处理。
     *
     * @param stream Redis Stream key
     * @param group 消费者组名称
     * @param recordIds 待 ACK 的 RecordId 字符串
     * @return 成功 ACK 的消息数量；Redis 返回 {@code null} 时返回 0
     */
    public long ack(String stream, String group, String... recordIds) {
        Long count = streamOps().acknowledge(stream, group, recordIds);
        return count == null ? 0 : count;
    }

    /**
     * 获取 Redis Stream 长度。
     *
     * @param stream Redis Stream key
     * @return Stream 消息数量；Redis 返回 {@code null} 时返回 0
     */
    public long streamSize(String stream) {
        Long size = streamOps().size(stream);
        return size == null ? 0 : size;
    }

    private BoundListOperations<String, Object> boundListOps(String queue) {
        return redisTemplate.boundListOps(queue);
    }

    private StreamReadOptions readOptions(long count, Duration block) {
        StreamReadOptions options = StreamReadOptions.empty();
        if (count > 0) {
            options = options.count(count);
        }
        if (block != null && !block.isNegative() && !block.isZero()) {
            options = options.block(block);
        }
        return options;
    }

    @SuppressWarnings("unchecked")
    private StreamOperations<String, Object, Object> streamOps() {
        return redisTemplate.opsForStream();
    }
}
