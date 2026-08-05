# letool-starter-mq-kafka

Kafka 消息队列便利 Starter，传递引入 `letool-starter-mq` 与 Spring Cloud Stream Kafka Binder。业务项目引用本模块即可，不需要再单独引用核心 MQ 模块。

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-mq-kafka</artifactId>
    <version>${letool.version}</version>
</dependency>
```

```yaml
letool:
  mq:
    enabled: true

spring:
  cloud:
    stream:
      kafka:
        binder:
          brokers: 127.0.0.1:9092
      output-bindings: order-out-0
      bindings:
        order-out-0:
          destination: order.events
```

模块注册的 Provider 名称为 `kafka`。分区、事务、重试、死信和 Kafka 专属能力请使用 Spring Cloud Stream Kafka Binder 原生配置。完整发送、消费和扩展方式见 [核心 MQ 文档](../letool-starter-mq/README.md)。
