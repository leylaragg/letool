package com.github.leyland.letool.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQ 消息队列配置属性 —— 统一管理内存队列与预留的 RabbitMQ / RocketMQ / Kafka 连接参数。
 *
 * <p>当前 starter 只内置 {@code memory} provider。RabbitMQ / RocketMQ / Kafka 配置字段
 * 保留给后续真实 provider 或用户自定义 {@link com.github.leyland.letool.mq.core.MqProvider} 使用。</p>
 *
 * <h3>配置前缀</h3>
 * <pre>{@code
 * letool.mq:
 *   enabled: true
 *   default-type: memory
 *   consumer:
 *     concurrency: 5
 *     max-attempts: 5
 * }</pre>
 *
 * <h3>default-type 可选值</h3>
 * <ul>
 *   <li>{@code memory} —— 内存队列（当前内置实现，无需外部依赖，适合开发/测试）</li>
 *   <li>{@code rabbitmq} —— 预留配置，当前无内置真实 RabbitMQ provider</li>
 *   <li>{@code rocketmq} —— 预留配置，当前无内置真实 RocketMQ provider</li>
 *   <li>{@code kafka} —— 预留配置，当前无内置真实 Kafka provider</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
@Data
@ConfigurationProperties(prefix = "letool.mq")
public class MqProperties {

    // ======================== 全局开关与默认类型 ========================

    /** 是否启用 MQ 模块，默认 {@code true} */
    private boolean enabled = true;

    /**
     * 默认消息队列类型，支持 rabbitmq / rocketmq / kafka / memory。
     *
     * <p>当前只有 memory 是内置实现；其他类型需要用户注册自定义 provider，否则启动失败。</p>
     */
    private String defaultType = "memory";

    // ======================== 各 MQ 配置 ========================

    /** RabbitMQ 连接配置 */
    private RabbitMQ rabbitMQ = new RabbitMQ();

    /** RocketMQ 连接配置 */
    private RocketMQ rocketMQ = new RocketMQ();

    /** Kafka 连接配置 */
    private Kafka kafka = new Kafka();

    /** 消费者通用配置 */
    private Consumer consumer = new Consumer();

    /** 生产者通用配置 */
    private Producer producer = new Producer();

    // ======================== 内部类：RabbitMQ ========================

    /**
     * RabbitMQ 连接参数.
     */
    @Data
    public static class RabbitMQ {

        /** RabbitMQ 服务地址，默认 {@code 127.0.0.1} */
        private String host = "127.0.0.1";

        /** RabbitMQ 端口，默认 {@code 5672} */
        private int port = 5672;

        /** 用户名，默认 {@code guest} */
        private String username = "guest";

        /** 密码，默认 {@code guest} */
        private String password = "guest";

        /** 虚拟主机，默认 {@code /} */
        private String virtualHost = "/";
    }

    // ======================== 内部类：RocketMQ ========================

    /**
     * RocketMQ 连接参数.
     */
    @Data
    public static class RocketMQ {

        /** Name Server 地址，格式 {@code ip:port;ip:port} */
        private String nameServer = "127.0.0.1:9876";

        /** 生产者/消费者组名 */
        private String group = "letool-producer-group";

        /** 默认主题 */
        private String topic;
    }

    // ======================== 内部类：Kafka ========================

    /**
     * Kafka 连接参数.
     */
    @Data
    public static class Kafka {

        /** Bootstrap Server 地址，格式 {@code host1:port1,host2:port2} */
        private String bootstrapServers = "127.0.0.1:9092";

        /** 消费者组 ID */
        private String groupId = "letool-consumer-group";
    }

    // ======================== 内部类：Consumer 消费者通用配置 ========================

    /**
     * 消费者通用配置，适用于所有 MQ 类型.
     */
    @Data
    public static class Consumer {

        /** 并发消费线程数，默认 {@code 1} */
        private int concurrency = 1;

        /** 最大重试次数，默认 {@code 3} */
        private int maxAttempts = 3;

        /** 重试初始退避时间（毫秒），默认 {@code 1000} */
        private long backoffInitial = 1000L;
    }

    // ======================== 内部类：Producer 生产者通用配置 ========================

    /**
     * 生产者通用配置，适用于所有 MQ 类型.
     */
    @Data
    public static class Producer {

        /** 发送重试次数，默认 {@code 3} */
        private int retryTimes = 3;

        /** 发送超时时间（毫秒），默认 {@code 3000} */
        private long sendTimeout = 3000L;
    }
}
