# External Provider Boundaries

本文档说明 letool 中涉及外部服务的模块当前实现边界。目标是让使用者在引入 starter 前就能判断它是可生产集成、开发测试 mock，还是仍处于 stub 占位阶段。

## Status Legend

| Status | Meaning |
|---|---|
| real-http | 已经发起真实 HTTP/API 请求，但生产控制仍需补齐。 |
| official-sdk | 通过厂商官方 SDK 执行真实操作；仍需由应用针对账号、网络和服务策略完成外部契约验证。 |
| mock-only | 仅适合开发、测试或演示，不会访问真实外部服务。 |
| framework-adapter | Letool 提供成熟框架之上的便利门面，真实连接和可靠性语义由底层框架负责。 |

## Provider Matrix

| Module | Current Boundary | Production Risk | Next Production Step |
|---|---|---|---|
| `letool-starter-oss` 及三个 Provider 模块 | `official-sdk`。核心模块只保留统一契约、便利门面和稳定异常；`letool-starter-oss-minio`、`letool-starter-oss-aliyun`、`letool-starter-oss-tencent-cos` 分别调用官方 SDK 执行真实上传、下载、删除、存在性检查和 GET 预签名。不存在模拟成功或回退 Stub。 | 官方 SDK 可以真实访问外部服务，但账号权限、地域、Endpoint、Bucket 命名、网络、费用、超时和服务端策略仍由应用配置；单元测试不等同于真实账号契约验证。 | 为应用实际采用的 Provider 增加 profile-gated sandbox/受控账号测试，覆盖鉴权失败、限流、超时、重试、分片上传、预签名和服务端错误。 |
| `letool-starter-sms`、`letool-starter-sms-aliyun`、`letool-starter-sms-tencent` | 核心模块只提供契约、路由、显式 Mock 和可替换本地限流；两个独立模块分别使用阿里云短信 V2 与腾讯云 SMS 3.0 官方产品 SDK 真实发送。 | 单元测试验证请求映射和响应转换，但不证明账号鉴权、模板审核、厂商配额、送达回执或费用策略。 | 对实际采用的账号增加 profile-gated 沙箱契约测试；生产侧配置告警、回执、预算和厂商控制台配额。 |
| `letool-starter-pay`、`letool-starter-pay-alipay`、`letool-starter-pay-wechat` | `official-sdk`。核心模块只提供统一契约、路由、稳定异常和显式 Mock；两个 Provider 模块分别调用支付宝和微信支付官方 SDK，实现真实下单、查询、关单、退款、退款查询与回调验签。 | 单元测试验证请求映射、异常和验签调用，但不能替代商户进件、证书、网络、金额、账务和厂商沙箱验证；框架不会替业务维护支付订单事务。 | 使用实际采用平台的沙箱/受控商户执行 profile-gated 契约测试；业务实现回调 Controller、本地订单唯一约束、金额校验、事务幂等和审计。 |
| `letool-starter-mq` 及三个 Binder 模块 | `framework-adapter`。核心模块将请求交给 Spring Cloud Stream；RabbitMQ、Kafka、RocketMQ 模块分别传递成熟 Binder，不保留内存队列或自研消费者协议。 | `accepted=true` 只表示输出通道接受消息，不等同于 Broker 持久化或消费成功；重试、死信、事务、分区、顺序和确认语义取决于 Binder 与 Broker 配置。 | 在目标 Broker 上补 profile-gated 契约测试，并按业务可靠性要求配置 Binder 原生确认、重试、死信、事务和消费幂等。 |
| `letool-starter-monitor` | 指标由 Spring Boot Actuator/Micrometer 提供，应用自行选择 Prometheus、OTLP 等 registry；钉钉、企微告警会通过 Webhook POST 真实发送；清理调度只执行用户实现的 `CleanupTask`，不包含默认 SQL 或占位任务。 | Webhook 已能真实触达机器人，但本阶段没有重试、限流和投递审计；清理任务的事务、SQL 与存储安全由应用实现负责。 | 为 Webhook 增加可配置重试、限流、投递审计和外部契约测试；根据实际应用补充清理任务观测与数据库集成测试。 |
| `letool-starter-ai` | Letool 不包含具体 Provider 或自发 HTTP/SSE 实现。应用必须显式引入 Spring AI Provider Starter；Letool 只收集其 `ChatModel` / `EmbeddingModel` Bean，并提供名称路由和原生 `ChatClient` 便利门面。 | API Key、端点、HTTP 协议、连接池、重试、流式行为和供应商 SLA 均属于用户所选 Spring AI Provider 的集成边界，Letool 不拥有或持久化密钥。 | 对实际 Provider 使用 Testcontainers（适用时）、供应商 sandbox/受控账号和 profile-gated contract tests，验证超时、限流、鉴权失败、工具与流式契约。 |

## Documentation Rule

凡是模块存在 mock、stub、fallback 或 placeholder 行为，模块 README 必须在 Maven 坐标前明确写出限制。后续实现真实 provider 时，需要同步更新本文档、模块 README 和 `docs/module-production-readiness.md`。
