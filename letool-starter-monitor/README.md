# letool-starter-monitor

面向 Spring Boot 应用的监控便利层。Spring Boot Actuator 与 Micrometer 负责成熟的
JVM、HTTP、数据源指标及后端导出；Letool 提供业务指标便利门面、钉钉/企业微信
Webhook 告警，以及用户数据清理任务的安全调度。

## 能力边界

- 不重复实现指标注册表、JVM 轮询线程、HTTP 统计窗口或 Prometheus 协议。
- `MetricsCollector` 直接写入应用唯一的 `MeterRegistry`。
- Prometheus、OTLP 等后端由应用选择相应 Micrometer registry。
- `CleanupTask` 是用户扩展接口，Letool 不猜测表结构，也不提供空 SQL 伪实现。
- 清理调度默认关闭；显式启用却没有任务实现时快速失败。
- Webhook 当前提供真实投递，但重试、限流和投递审计不在本阶段范围内。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-monitor</artifactId>
    <version>${letool.version}</version>
</dependency>
```

该依赖会引入 `spring-boot-starter-actuator`，因此默认具备 Actuator 与 Micrometer
基础能力。

## 业务指标

`MetricsCollector` 是 `MeterRegistry` 的轻量便利门面，不维护第二份本地指标数据。

```java
@Service
public class OrderService {

    private final MetricsCollector metrics;

    public OrderService(MetricsCollector metrics) {
        this.metrics = metrics;
    }

    public Order create(OrderCommand command) {
        return metrics.record(
                "order.create.duration",
                () -> {
                    Order order = doCreate(command);
                    metrics.increment(
                            "order.created",
                            "channel", command.channel());
                    return order;
                },
                "channel", command.channel());
    }
}
```

已知耗时也可以直接记录：

```java
metricsCollector.recordTime(
        "batch.duration",
        Duration.ofMillis(120),
        "job", "settlement");

double count = metricsCollector.counterValue(
        "order.created",
        "channel", "web");

MetricsCollector.TimerSnapshot snapshot =
        metricsCollector.timerSnapshot(
                "order.create.duration",
                "channel", "web");
```

`counterValue(...)` 与 `timerSnapshot(...)` 会按 Micrometer 完整 meter 身份
获取或创建指标；查询未知身份会注册零值 meter，不会使用标签子集任取同名指标。
读取结果表示具体 registry 的当前视图，阶梯型 registry 不承诺进程级绝对累计值。

标签必须保持低基数。不要把用户编号、订单编号、完整原始 URL、异常消息等无限增长值
放入标签，否则任何 Micrometer 后端都可能产生严重的时间序列膨胀。

Spring Boot 已经提供 JVM、HTTP、连接池等标准指标，请直接使用对应 Actuator/Micrometer
能力，不再注入 Letool 旧版 `JvmMetrics` 或 API 统计类。

## Prometheus 导出

monitor starter 不强制所有应用引入 Prometheus。需要时由应用添加：

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

并按 Spring Boot 标准方式开放端点：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
```

之后访问 `/actuator/prometheus`。若使用 OTLP、Datadog 等后端，替换为对应 registry
依赖和 Spring Boot 配置即可。

## 用户数据清理

Letool 只负责真实 Cron 调度、防重入、单任务失败隔离、执行报告和优雅关闭。应用
必须根据自己的表结构实现 `CleanupTask`：

```java
@Component
public class AuditLogCleanupTask implements CleanupTask {

    private final AuditLogRepository repository;

    public AuditLogCleanupTask(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "audit-log";
    }

    @Override
    public Duration retention() {
        return Duration.ofDays(90);
    }

    @Override
    public long cleanup(CleanupContext context) {
        return repository.deleteCreatedBefore(context.cutoff());
    }
}
```

显式启用调度：

```yaml
letool:
  monitor:
    data-retention:
      enabled: true
      clean-cron: "0 0 3 * * ?"
      zone-id: Asia/Shanghai
      shutdown-timeout: 10s
```

每个任务自行声明保留时长。调度器在同一轮中提供统一的 `triggeredAt` 与计算后的
`cutoff`，不内置数据库事务边界。一次执行完成后可通过
`DataCleanupScheduler.lastReport()` 查看不可变报告，也可用 `runOnce()` 手动触发。

以下配置会直接阻止应用启动：

- 已启用清理但没有任何 `CleanupTask`。
- 任务名称为空或重复。
- 保留时长不是正数。
- Cron、时区或优雅关闭时间不合法。

## Webhook 告警

```yaml
letool:
  monitor:
    alert:
      enabled: true
      dingtalk:
        webhook-url: https://oapi.dingtalk.com/robot/send?access_token=xxx
        secret: SECxxx
      wechat:
        webhook-url: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
```

配置有效地址后，自动配置会注册对应渠道：

```java
alertNotifier.notify(rule, "订单失败率超过阈值");
```

用户还可以实现 `AlertNotifier.AlertChannel` 并通过
`AlertNotifier.registerChannel(...)` 注册自定义渠道。

## 配置属性

| 属性 | 默认值 | 说明 |
|---|---:|---|
| `letool.monitor.enabled` | `true` | monitor 模块总开关 |
| `letool.monitor.metrics.enabled` | `true` | 创建 `MetricsCollector` 便利门面 |
| `letool.monitor.alert.enabled` | `true` | 创建告警分发器 |
| `letool.monitor.alert.dingtalk.webhook-url` | - | 钉钉 Webhook |
| `letool.monitor.alert.dingtalk.secret` | - | 钉钉加签密钥 |
| `letool.monitor.alert.wechat.webhook-url` | - | 企业微信 Webhook |
| `letool.monitor.alert.mail.to` | - | 仅保存邮件接收人，monitor 不内置邮件发送渠道 |
| `letool.monitor.data-retention.enabled` | `false` | 调度用户 `CleanupTask` |
| `letool.monitor.data-retention.clean-cron` | `0 0 3 * * ?` | Spring 六字段 Cron |
| `letool.monitor.data-retention.zone-id` | JVM 系统时区 | Cron 时区 |
| `letool.monitor.data-retention.shutdown-timeout` | `10s` | 优雅关闭等待上限 |

Actuator 的端点开放、通用标签、直方图、SLO 和 registry 导出步长，请使用
`management.*` 下的 Spring Boot 标准配置。

## 破坏性迁移

本阶段删除以下自研 API：

- `JvmMetrics`、`JvmMetricsSnapshot`：改用 Actuator/Micrometer JVM 指标。
- `HttpMetrics`：改用 Spring Boot HTTP server metrics。
- `ApiStatsCollector`、`ApiStatsAggregator`、`ApiStatsSummary`：改用
  `MeterRegistry` 的 `Timer`、`Counter` 及监控后端查询。
- `ApiErrorCollector`：错误总量使用低基数 Counter；错误明细进入日志或追踪系统。

`MetricsCollector` 迁移映射如下：

| 旧 API | 新 API |
|---|---|
| `counter(String)` | `counter(String, String...)`，返回 Micrometer `Counter` |
| `long increment(String)` | `void increment(String, String...)` |
| `getCounterValue(String)` | `counterValue(String, String...)` |
| `recordTime(String, long)` | `recordTime(String, Duration, String...)` |
| `getTimerStats(String)` | `timerSnapshot(String, String...)` |
| `Timer` / `TimerStats` | Micrometer `Timer` / 不可变 `TimerSnapshot` |
| `getCounterNames()` / `getTimerNames()` / `getAllMetrics()` | 直接查询应用 `MeterRegistry` 或监控后端 |

旧占位清理类已改成必须由应用实现的 `CleanupTask` 接口。
