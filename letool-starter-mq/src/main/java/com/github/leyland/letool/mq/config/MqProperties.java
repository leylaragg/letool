package com.github.leyland.letool.mq.config;

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

    /**
     * 判断 MQ 模块是否启用.
     *
     * @return {@code true} 表示启用 MQ 自动装配
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置 MQ 模块总开关.
     *
     * @param enabled 是否启用 MQ 自动装配
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取默认 MQ provider 类型.
     *
     * @return 默认 provider 类型，当前内置值为 {@code memory}
     */
    public String getDefaultType() {
        return defaultType;
    }

    /**
     * 设置默认 MQ provider 类型.
     *
     * @param defaultType provider 类型，如 {@code memory}、{@code rabbitmq}、{@code rocketmq}、{@code kafka}
     */
    public void setDefaultType(String defaultType) {
        this.defaultType = defaultType;
    }

    /**
     * 获取 RabbitMQ 预留连接配置.
     *
     * @return RabbitMQ 配置对象
     */
    public RabbitMQ getRabbitMQ() {
        return rabbitMQ;
    }

    /**
     * 设置 RabbitMQ 预留连接配置.
     *
     * @param rabbitMQ RabbitMQ 配置对象
     */
    public void setRabbitMQ(RabbitMQ rabbitMQ) {
        this.rabbitMQ = rabbitMQ;
    }

    /**
     * 获取 RocketMQ 预留连接配置.
     *
     * @return RocketMQ 配置对象
     */
    public RocketMQ getRocketMQ() {
        return rocketMQ;
    }

    /**
     * 设置 RocketMQ 预留连接配置.
     *
     * @param rocketMQ RocketMQ 配置对象
     */
    public void setRocketMQ(RocketMQ rocketMQ) {
        this.rocketMQ = rocketMQ;
    }

    /**
     * 获取 Kafka 预留连接配置.
     *
     * @return Kafka 配置对象
     */
    public Kafka getKafka() {
        return kafka;
    }

    /**
     * 设置 Kafka 预留连接配置.
     *
     * @param kafka Kafka 配置对象
     */
    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    /**
     * 获取消费者通用配置.
     *
     * @return 消费者配置对象
     */
    public Consumer getConsumer() {
        return consumer;
    }

    /**
     * 设置消费者通用配置.
     *
     * @param consumer 消费者配置对象
     */
    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

    /**
     * 获取生产者通用配置.
     *
     * @return 生产者配置对象
     */
    public Producer getProducer() {
        return producer;
    }

    /**
     * 设置生产者通用配置.
     *
     * @param producer 生产者配置对象
     */
    public void setProducer(Producer producer) {
        this.producer = producer;
    }

    // ======================== 内部类：RabbitMQ ========================

    /**
     * RabbitMQ 连接参数.
     */
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

        /**
         * 获取 RabbitMQ 服务地址.
         *
         * @return 服务地址
         */
        public String getHost() {
            return host;
        }

        /**
         * 设置 RabbitMQ 服务地址.
         *
         * @param host 服务地址
         */
        public void setHost(String host) {
            this.host = host;
        }

        /**
         * 获取 RabbitMQ 端口.
         *
         * @return 端口号
         */
        public int getPort() {
            return port;
        }

        /**
         * 设置 RabbitMQ 端口.
         *
         * @param port 端口号
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * 获取 RabbitMQ 用户名.
         *
         * @return 用户名
         */
        public String getUsername() {
            return username;
        }

        /**
         * 设置 RabbitMQ 用户名.
         *
         * @param username 用户名
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * 获取 RabbitMQ 密码.
         *
         * @return 密码
         */
        public String getPassword() {
            return password;
        }

        /**
         * 设置 RabbitMQ 密码.
         *
         * @param password 密码
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 获取 RabbitMQ 虚拟主机.
         *
         * @return 虚拟主机
         */
        public String getVirtualHost() {
            return virtualHost;
        }

        /**
         * 设置 RabbitMQ 虚拟主机.
         *
         * @param virtualHost 虚拟主机
         */
        public void setVirtualHost(String virtualHost) {
            this.virtualHost = virtualHost;
        }
    }

    // ======================== 内部类：RocketMQ ========================

    /**
     * RocketMQ 连接参数.
     */
    public static class RocketMQ {

        /** Name Server 地址，格式 {@code ip:port;ip:port} */
        private String nameServer = "127.0.0.1:9876";

        /** 生产者/消费者组名 */
        private String group = "letool-producer-group";

        /** 默认主题 */
        private String topic;

        /**
         * 获取 RocketMQ Name Server 地址.
         *
         * @return Name Server 地址
         */
        public String getNameServer() {
            return nameServer;
        }

        /**
         * 设置 RocketMQ Name Server 地址.
         *
         * @param nameServer Name Server 地址
         */
        public void setNameServer(String nameServer) {
            this.nameServer = nameServer;
        }

        /**
         * 获取 RocketMQ 生产者/消费者组名.
         *
         * @return 组名
         */
        public String getGroup() {
            return group;
        }

        /**
         * 设置 RocketMQ 生产者/消费者组名.
         *
         * @param group 组名
         */
        public void setGroup(String group) {
            this.group = group;
        }

        /**
         * 获取 RocketMQ 默认主题.
         *
         * @return 默认主题
         */
        public String getTopic() {
            return topic;
        }

        /**
         * 设置 RocketMQ 默认主题.
         *
         * @param topic 默认主题
         */
        public void setTopic(String topic) {
            this.topic = topic;
        }
    }

    // ======================== 内部类：Kafka ========================

    /**
     * Kafka 连接参数.
     */
    public static class Kafka {

        /** Bootstrap Server 地址，格式 {@code host1:port1,host2:port2} */
        private String bootstrapServers = "127.0.0.1:9092";

        /** 消费者组 ID */
        private String groupId = "letool-consumer-group";

        /**
         * 获取 Kafka Bootstrap Server 地址.
         *
         * @return Bootstrap Server 地址
         */
        public String getBootstrapServers() {
            return bootstrapServers;
        }

        /**
         * 设置 Kafka Bootstrap Server 地址.
         *
         * @param bootstrapServers Bootstrap Server 地址
         */
        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        /**
         * 获取 Kafka 消费者组 ID.
         *
         * @return 消费者组 ID
         */
        public String getGroupId() {
            return groupId;
        }

        /**
         * 设置 Kafka 消费者组 ID.
         *
         * @param groupId 消费者组 ID
         */
        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }
    }

    // ======================== 内部类：Consumer 消费者通用配置 ========================

    /**
     * 消费者通用配置，适用于所有 MQ 类型.
     */
    public static class Consumer {

        /** 并发消费线程数，默认 {@code 1} */
        private int concurrency = 1;

        /** 最大重试次数，默认 {@code 3} */
        private int maxAttempts = 3;

        /** 重试初始退避时间（毫秒），默认 {@code 1000} */
        private long backoffInitial = 1000L;

        /**
         * 获取并发消费线程数.
         *
         * @return 并发消费线程数
         */
        public int getConcurrency() {
            return concurrency;
        }

        /**
         * 设置并发消费线程数.
         *
         * @param concurrency 并发消费线程数
         */
        public void setConcurrency(int concurrency) {
            this.concurrency = concurrency;
        }

        /**
         * 获取最大重试次数.
         *
         * @return 最大重试次数
         */
        public int getMaxAttempts() {
            return maxAttempts;
        }

        /**
         * 设置最大重试次数.
         *
         * @param maxAttempts 最大重试次数
         */
        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        /**
         * 获取重试初始退避时间.
         *
         * @return 退避时间，单位毫秒
         */
        public long getBackoffInitial() {
            return backoffInitial;
        }

        /**
         * 设置重试初始退避时间.
         *
         * @param backoffInitial 退避时间，单位毫秒
         */
        public void setBackoffInitial(long backoffInitial) {
            this.backoffInitial = backoffInitial;
        }
    }

    // ======================== 内部类：Producer 生产者通用配置 ========================

    /**
     * 生产者通用配置，适用于所有 MQ 类型.
     */
    public static class Producer {

        /** 发送重试次数，默认 {@code 3} */
        private int retryTimes = 3;

        /** 发送超时时间（毫秒），默认 {@code 3000} */
        private long sendTimeout = 3000L;

        /**
         * 获取发送重试次数.
         *
         * @return 发送重试次数
         */
        public int getRetryTimes() {
            return retryTimes;
        }

        /**
         * 设置发送重试次数.
         *
         * @param retryTimes 发送重试次数
         */
        public void setRetryTimes(int retryTimes) {
            this.retryTimes = retryTimes;
        }

        /**
         * 获取发送超时时间.
         *
         * @return 超时时间，单位毫秒
         */
        public long getSendTimeout() {
            return sendTimeout;
        }

        /**
         * 设置发送超时时间.
         *
         * @param sendTimeout 超时时间，单位毫秒
         */
        public void setSendTimeout(long sendTimeout) {
            this.sendTimeout = sendTimeout;
        }
    }
}
