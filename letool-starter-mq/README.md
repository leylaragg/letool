# letool-starter-mq

> 消息队列模块，RabbitMQ/RocketMQ/Kafka 统一抽象，提供消息发送/消费、死信处理能力。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-mq</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 添加依赖并配置

```yaml
letool:
  mq:
    enabled: true
    default-type: rabbitmq
    rabbitmq:
      host: 127.0.0.1
      port: 5672
      username: guest
      password: guest
    consumer:
      concurrency: 3
      max-attempts: 5
```

### 2. 发送消息

```java
@Autowired
private MqTemplate mqTemplate;

// 发送消息
mqTemplate.send("order-topic", orderPayload);

// 异步发送
mqTemplate.sendAsync("order-topic", orderPayload)
        .thenAccept(v -> log.info("发送成功"));
```

### 3. 消费消息

```java
@Component
public class OrderConsumer {

    @MqListener(topic = "order-topic", tag = "create")
    public void handleOrderCreate(Message message) {
        Order order = JsonUtil.parseObject(message.getBody(), Order.class);
        processOrder(order);
    }
}
```

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `letool.mq.enabled` | boolean | true | 是否启用 MQ 模块 |
| `letool.mq.default-type` | String | rabbitmq | 默认 MQ 类型：rabbitmq / rocketmq / kafka / memory |
| `letool.mq.rabbitmq.host` | String | 127.0.0.1 | RabbitMQ 地址 |
| `letool.mq.rabbitmq.port` | int | 5672 | RabbitMQ 端口 |
| `letool.mq.rabbitmq.username` | String | guest | RabbitMQ 用户名 |
| `letool.mq.rabbitmq.password` | String | guest | RabbitMQ 密码 |
| `letool.mq.rabbitmq.virtual-host` | String | / | 虚拟主机 |
| `letool.mq.rocketmq.name-server` | String | 127.0.0.1:9876 | RocketMQ NameServer |
| `letool.mq.rocketmq.group` | String | letool-producer-group | RocketMQ 组名 |
| `letool.mq.kafka.bootstrap-servers` | String | 127.0.0.1:9092 | Kafka 地址 |
| `letool.mq.kafka.group-id` | String | letool-consumer-group | Kafka 消费者组 ID |
| `letool.mq.consumer.concurrency` | int | 1 | 并发消费线程数 |
| `letool.mq.consumer.max-attempts` | int | 3 | 最大重试次数 |
| `letool.mq.consumer.backoff-initial` | long | 1000 | 重试初始退避时间（毫秒） |
| `letool.mq.producer.retry-times` | int | 3 | 发送重试次数 |
| `letool.mq.producer.send-timeout` | long | 3000 | 发送超时（毫秒） |

## 核心 API

### 注解声明式——@MqListener 消息消费

```java
@Component
public class OrderConsumer {

    // 订阅指定 topic 和 tag
    @MqListener(topic = "order-topic", tag = "create")
    public void handleOrderCreate(Message message) {
        Order order = JsonUtil.parseObject(message.getBody(), Order.class);
        orderService.processCreate(order);
    }

    // 匹配全部 tag
    @MqListener(topic = "order-topic", tag = "*")
    public void handleAllOrderMessages(Message message) {
        log.info("收到订单消息: {}", message.getBody());
    }

    // 指定消费者组
    @MqListener(topic = "payment-topic", group = "payment-consumer-group")
    public void handlePayment(Message message) {
        paymentService.process(message);
    }
}
```

### 编程式——MqTemplate 消息发送

```java
@Autowired
private MqTemplate mqTemplate;

// 简单发送
mqTemplate.send("order-topic", orderPayload);

// 带标签发送
mqTemplate.send("order-topic", "create", orderPayload);

// 异步发送
mqTemplate.sendAsync("order-topic", orderPayload)
        .thenAccept(v -> log.info("发送成功"))
        .exceptionally(e -> {
            log.error("发送失败", e);
            return null;
        });

// 异步发送（带标签）
mqTemplate.sendAsync("order-topic", "delete", orderPayload);

// 延迟发送（5 秒后投递）
mqTemplate.sendDelay("order-topic", orderPayload, 5, TimeUnit.SECONDS);
```

### 编程式——MqTemplate 消息订阅

```java
// Lambda 编程式订阅
mqTemplate.subscribe("order-topic", msg -> {
    Order order = JsonUtil.parseObject(msg.getBody(), Order.class);
    processOrder(order);
});

// 取消订阅
Consumer<Message> consumer = msg -> processOrder(msg);
mqTemplate.subscribe("order-topic", consumer);
mqTemplate.unsubscribe("order-topic", consumer);
```

### 编程式——Builder 流式发送

```java
// 链式构建并同步发送
mqTemplate.builder()
    .topic("order")
    .tag("create")
    .body(orderPayload)
    .header("traceId", "abc123")
    .header("userId", "user-456")
    .send();

// 链式构建并异步发送
mqTemplate.builder()
    .topic("order")
    .tag("cancel")
    .body(cancelPayload)
    .sendAsync()
    .thenAccept(v -> log.info("取消订单消息已发送"));
```
