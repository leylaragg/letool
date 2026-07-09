package com.github.leyland.letool.tool.redis;

import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisMessageQueueUtil}.
 */
class RedisMessageQueueUtilTest {

    /**
     * List queue helpers should use Redis List commands and preserve object values
     * for the RedisTemplate serializer.
     */
    @Test
    void shouldOfferAndPollMessagesWithRedisList() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        BoundListOperations<String, Object> listOperations = mock(BoundListOperations.class);
        QueueMessage message = new QueueMessage("m1", "created");
        when(redisTemplate.boundListOps("queue:orders")).thenReturn(listOperations);
        when(listOperations.rightPush(message)).thenReturn(1L);
        when(listOperations.leftPop()).thenReturn(message);
        when(listOperations.leftPop(2, TimeUnit.SECONDS)).thenReturn(message);
        when(listOperations.size()).thenReturn(3L);

        RedisMessageQueueUtil queueUtil = new RedisMessageQueueUtil(redisTemplate);

        assertThat(queueUtil.offer("queue:orders", message)).isEqualTo(1L);
        QueueMessage immediate = queueUtil.poll("queue:orders");
        QueueMessage blocking = queueUtil.poll("queue:orders", 2, TimeUnit.SECONDS);

        assertThat(immediate).isSameAs(message);
        assertThat(blocking).isSameAs(message);
        assertThat(queueUtil.size("queue:orders")).isEqualTo(3L);
        verify(listOperations).rightPush(message);
    }

    /**
     * Stream helpers should add, read, create consumer groups, read by group and
     * acknowledge messages through Redis Stream operations.
     */
    @Test
    void shouldOperateRedisStreamsWithObjectMessages() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        StreamOperations<String, Object, Object> streamOperations = mock(StreamOperations.class);
        QueueMessage message = new QueueMessage("m1", "created");
        RecordId recordId = RecordId.of("1-0");
        ObjectRecord<String, QueueMessage> record = ObjectRecord.create("stream:orders", message).withId(recordId);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.add(any(ObjectRecord.class))).thenReturn(recordId);
        when(streamOperations.read(eq(QueueMessage.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record));
        when(streamOperations.createGroup("stream:orders", ReadOffset.from("0-0"), "order-workers"))
                .thenReturn("order-workers");
        when(streamOperations.read(eq(QueueMessage.class), eq(Consumer.from("order-workers", "c1")),
                any(StreamReadOptions.class), any(StreamOffset.class))).thenReturn(List.of(record));
        when(streamOperations.acknowledge("stream:orders", "order-workers", "1-0")).thenReturn(1L);

        RedisMessageQueueUtil queueUtil = new RedisMessageQueueUtil(redisTemplate);

        assertThat(queueUtil.add("stream:orders", message)).isEqualTo(recordId);
        assertThat(queueUtil.read("stream:orders", QueueMessage.class, "0-0", 10))
                .containsExactly(record);
        assertThat(queueUtil.createGroup("stream:orders", "order-workers", "0-0")).isEqualTo("order-workers");
        assertThat(queueUtil.readGroup("stream:orders", "order-workers", "c1", QueueMessage.class, 5, Duration.ofSeconds(1)))
                .containsExactly(record);
        assertThat(queueUtil.ack("stream:orders", "order-workers", "1-0")).isEqualTo(1L);

        verify(streamOperations).add(any(ObjectRecord.class));
    }

    record QueueMessage(String id, String type) {
    }
}
