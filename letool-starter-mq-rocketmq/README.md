# letool-starter-mq-rocketmq

RocketMQ 消息队列便利 Starter，传递引入 `letool-starter-mq` 与 Spring Cloud Alibaba RocketMQ Binder。业务项目引用本模块即可，不需要再单独引用核心 MQ 模块。

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-mq-rocketmq</artifactId>
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
      rocketmq:
        binder:
          name-server: 127.0.0.1:9876
      output-bindings: order-out-0
      bindings:
        order-out-0:
          destination: order.events
```

模块注册的 Provider 名称为 `rocketmq`。顺序消息、事务消息、重试和 RocketMQ 专属能力请使用 Spring Cloud Alibaba RocketMQ Binder 原生配置。完整发送、消费和扩展方式见 [核心 MQ 文档](../letool-starter-mq/README.md)。
