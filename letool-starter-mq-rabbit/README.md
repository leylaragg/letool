# letool-starter-mq-rabbit

RabbitMQ 消息队列便利 Starter，传递引入 `letool-starter-mq` 与 Spring Cloud Stream Rabbit Binder。业务项目引用本模块即可，不需要再单独引用核心 MQ 模块。

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
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
  cloud:
    stream:
      output-bindings: order-out-0
      bindings:
        order-out-0:
          destination: order.events
```

模块注册的 Provider 名称为 `rabbit`。重试、死信、消费确认、并发和 RabbitMQ 专属能力请使用 Spring Cloud Stream Rabbit Binder 原生配置。完整发送、消费和扩展方式见 [核心 MQ 文档](../letool-starter-mq/README.md)。
