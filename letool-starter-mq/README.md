# letool-starter-mq

基于 Spring Cloud Stream 的消息队列便利门面。Letool 负责不可变消息模型、Provider 路由、统一异常和简洁发送 API；连接、序列化、重试、死信、事务、分区、顺序消息与消费确认继续由成熟 Binder 提供。

## 模块选择

业务项目只需要引用实际使用的一个 Provider Starter，它会传递引入核心模块和对应 Binder：

| 中间件 | Maven 模块 | 底层实现 | Provider 名称 |
|---|---|---|---|
| RabbitMQ | `letool-starter-mq-rabbit` | Spring Cloud Stream Rabbit Binder | `rabbit` |
| Kafka | `letool-starter-mq-kafka` | Spring Cloud Stream Kafka Binder | `kafka` |
| RocketMQ | `letool-starter-mq-rocketmq` | Spring Cloud Alibaba RocketMQ Binder | `rocketmq` |

仅在开发自定义 Provider 时，才需要直接引用 `letool-starter-mq`。

## 快速开始

下面以 RabbitMQ 为例；使用 Kafka 或 RocketMQ 时只需更换 Starter，并使用对应 Binder 的原生连接配置。

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-mq-rabbit</artifactId>
    <version>${letool.version}</version>
</dependency>
```

```yaml
letool:
  mq:
    enabled: true

spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: guest
    password: guest
  cloud:
    stream:
      output-bindings: order-out-0
      bindings:
        order-out-0:
          destination: order.events
          content-type: application/json
```

发送普通消息：

```java
@Service
public class OrderEventPublisher {

    private final MqTemplate mqTemplate;

    public OrderEventPublisher(MqTemplate mqTemplate) {
        this.mqTemplate = mqTemplate;
    }

    public MqSendResult publish(OrderCreatedEvent event) {
        return mqTemplate.send("order-out-0", event);
    }
}
```

需要传递 Header 或显式声明 Content-Type 时，使用完整请求：

```java
MqMessage<OrderCreatedEvent> message = new MqMessage<>(
        event,
        Map.of("traceId", traceId),
        "application/json");

MqSendResult result = mqTemplate.send(new MqSendRequest<>(
        null,
        "order-out-0",
        message));
```

`MqSendResult.accepted()` 为 `true` 只表示消息已被 Spring Cloud Stream 输出通道接受，不等价于 Broker 已持久化，也不代表消费者已经处理成功。可靠性语义应结合具体 Binder、Broker 确认机制和业务幂等方案判断。

## 消费消息

消费端直接使用 Spring Cloud Stream 函数模型，不再维护一套功能较弱的 `@MqListener`：

```java
@Configuration
public class OrderConsumerConfiguration {

    @Bean
    public Consumer<Message<OrderCreatedEvent>> orderCreatedConsumer(
            OrderService orderService) {
        return message -> orderService.handle(message.getPayload());
    }
}
```

```yaml
spring:
  cloud:
    function:
      definition: orderCreatedConsumer
    stream:
      bindings:
        orderCreatedConsumer-in-0:
          destination: order.events
          group: order-service
```

## 多 Provider 路由

单 Provider 会自动成为默认项。确实需要在同一应用中同时使用多个 Binder 时，配置默认 Provider：

```yaml
letool:
  mq:
    enabled: true
    default-provider: kafka
```

也可以在单次发送时显式选择：

```java
mqTemplate.send("rabbit", "audit-out-0", auditEvent);
mqTemplate.send("kafka", "metric-out-0", metricEvent);
```

Provider 名称不区分大小写，内部会统一转换为小写。配置了不存在的默认 Provider、出现重名 Provider 或存在多个 Provider 却没有默认项时，应用会在启动阶段失败，避免把路由歧义延迟到首条业务消息。

### 自定义 Binder 名称与多集群

Provider 名称表示中间件类型，Binding 的 `binder` 属性表示 Spring Cloud Stream Binder 配置名称。多集群场景可以继续使用原生 Binder 别名：

```yaml
spring:
  cloud:
    stream:
      binders:
        rabbit-primary:
          type: rabbit
          environment:
            spring:
              rabbitmq:
                host: 10.0.0.10
      bindings:
        audit-out-0:
          destination: audit.events
          binder: rabbit-primary
```

```java
mqTemplate.send("rabbit", "audit-out-0", auditEvent);
```

Letool 会把 `rabbit-primary` 原样传给 Spring Cloud Stream，同时校验该别名的 `type` 必须为 `rabbit`。如果 Binding 绑定到的中间件类型与所选 Provider 不一致，发送会在进入 Binder 前失败，避免发送结果中的 Provider 与实际中间件不一致。

Binder 选择优先级为：Binding 的 `binder` → `spring.cloud.stream.default-binder` → 当前 Provider 的默认类型名。这样既保留 Spring Cloud Stream 的原生多 Binder 路由，也能在没有额外配置时保持开箱即用。

## 配置边界

Letool 只新增两个配置项：

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `letool.mq.enabled` | `boolean` | `false` | 显式启用 MQ 便利门面和 Provider 自动配置 |
| `letool.mq.default-provider` | `String` | 无 | 多 Provider 场景的默认 Provider；单 Provider 可省略 |

以下能力必须使用 `spring.cloud.stream.*`、具体 Binder 或 Spring Boot 的原生配置，不在 Letool 中重复定义：

- Broker 地址、认证、TLS 与连接池；
- Binding、Destination、消费者组和 Content-Type；
- 重试、退避、死信、并发、分区和消费确认；
- Kafka/RocketMQ 的事务、顺序消息等中间件特性。

## 自定义 Provider

`MqProvider` 是真实扩展点，不是伪实现。接入其他消息平台时，实现并注册 Bean 即可：

```java
@Component
public class CustomMqProvider implements MqProvider {

    @Override
    public String name() {
        return "custom";
    }

    @Override
    public MqSendResult send(MqSendRequest<?> request) {
        // 调用目标平台的官方 SDK，并把结果归一化为 MqSendResult。
        return new MqSendResult(name(), request.bindingName(), true, Instant.now());
    }
}
```

扩展实现必须自行保证目标 SDK 的超时、重试、幂等与确认语义。发生错误时可以抛出平台原生运行时异常，`MqTemplate` 会将其转换为包含稳定错误码的 `MqException`；也可以主动抛出 `MqException` 保留更精确的错误码。

## 破坏性变更

本次生产化重构删除了没有真实 Broker 语义支撑的 API：

- 删除内存队列 `InMemoryMqProvider`；
- 删除 `Message`、`MessageBuilder` 和 `MessageListener`；
- 删除 `@MqListener`、`subscribe`、`unsubscribe`、`sendAsync` 和 `sendDelay`；
- 删除 Letool 自定义的 RabbitMQ、Kafka、RocketMQ 连接及消费者重试配置。

消费代码应迁移到 Spring Cloud Function 的 `Consumer`/`Function` 模型；高级生产能力应迁移到对应 Binder 原生配置。
