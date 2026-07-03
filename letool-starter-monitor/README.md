# letool-starter-monitor

监控指标模块，提供 Micrometer + Prometheus 集成、JVM / HTTP / SQL 指标采集、API 调用统计以及钉钉 / 企微 / 邮件多渠道告警。

> ⚠️ 当前 Micrometer/Prometheus 指标采集可用，钉钉、企业微信告警会通过 Webhook POST 真实发送；数据清理调度默认关闭。即使显式开启，内置清理任务也只记录日志，不执行真实 SQL 删除。生产清理需要接入真实执行策略。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-monitor</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始

**1. 在 `application.yml` 中配置：**

```yaml
letool:
  monitor:
    enabled: true
    metrics:
      export-prometheus: true
    alert:
      dingtalk:
        webhook-url: https://oapi.dingtalk.com/robot/send?access_token=xxx
      wechat:
        webhook-url: https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx
      mail:
        to:
          - admin@example.com
```

**2. 注入 `MetricsCollector`，在业务代码中埋点：**

```java
@Autowired
private MetricsCollector metricsCollector;

public void createOrder(Order order) {
    long start = System.currentTimeMillis();
    // 业务逻辑...
    long elapsed = System.currentTimeMillis() - start;

    metricsCollector.increment("order.created");
    metricsCollector.recordTime("order.process", elapsed);
}
```

指标自动暴露到 `/actuator/prometheus`。

## 配置属性

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `letool.monitor.enabled` | `true` | 监控总开关 |
| `letool.monitor.metrics.enabled` | `true` | 指标采集开关 |
| `letool.monitor.metrics.export-prometheus` | `true` | 导出 Prometheus 格式 |
| `letool.monitor.metrics.step` | `1m` | 采集步长 |
| `letool.monitor.jvm.enabled` | `true` | JVM 监控 |
| `letool.monitor.jvm.collect-interval` | `30s` | JVM 采集间隔 |
| `letool.monitor.http.enabled` | `true` | HTTP 监控 |
| `letool.monitor.http.include-paths` | `/**` | 监控路径模式 |
| `letool.monitor.api-stats.enabled` | `true` | API 统计 |
| `letool.monitor.api-stats.window-size` | `60` | 聚合窗口（分钟） |
| `letool.monitor.api-stats.retention-days` | `7` | 保留天数 |
| `letool.monitor.alert.enabled` | `true` | 告警开关 |
| `letool.monitor.alert.dingtalk.webhook-url` | - | 钉钉 Webhook |
| `letool.monitor.alert.dingtalk.secret` | - | 钉钉签名密钥 |
| `letool.monitor.alert.wechat.webhook-url` | - | 企微 Webhook |
| `letool.monitor.alert.mail.to` | - | 告警邮件接收人 |
| `letool.monitor.data-retention.enabled` | `false` | 是否启用内置数据清理调度；当前只记录日志，不执行 SQL 删除 |
| `letool.monitor.data-retention.clean-cron` | `0 0 3 * * ?` | 清理 Cron |

## 核心 API 示例

### 1. 指标采集（MetricsCollector）

**编程式埋点：**

```java
@Autowired
private MetricsCollector metricsCollector;

// 计数器
metricsCollector.increment("user.login");
long loginCount = metricsCollector.getCounterValue("user.login");
Set<String> counterNames = metricsCollector.getCounterNames();

// 计时器
metricsCollector.recordTime("api.query", 150);  // 毫秒
MetricsCollector.TimerStats stats = metricsCollector.getTimerStats("api.query");
System.out.printf("count=%d, avg=%.2fms, min=%d, max=%d%n",
    stats.getCount(), stats.getAvgMs(), stats.getMinMs(), stats.getMaxMs());

// 全量导出
Map<String, Object> allMetrics = metricsCollector.getAllMetrics();
```

### 2. JVM 指标采集

```java
@Autowired
private JvmMetrics jvmMetrics;

// 获取最新 JVM 快照
JvmMetricsSnapshot snapshot = jvmMetrics.getMetrics();
long heapUsed = snapshot.getHeapUsed();
long heapMax = snapshot.getHeapMax();
int threadCount = snapshot.getThreadCount();
double cpuLoad = snapshot.getCpuLoad();
long gcCount = snapshot.getGcCount();
long uptimeMs = snapshot.getUptimeMs();

// 即时采集一次
jvmMetrics.collectNow();
```

### 3. 告警通知

**编程式（构建告警规则并分发）：**

```java
@Autowired
private AlertNotifier alertNotifier;

// 注册通知渠道
alertNotifier.registerChannel(new DingTalkNotifier(properties));
alertNotifier.registerChannel(new WechatNotifier(properties));

// 构建告警规则
AlertRule rule = new AlertRule();
rule.setName("堆内存使用率过高");
rule.setMetric("heap.used.percent");
rule.setCondition(AlertCondition.GREATER_THAN);
rule.setThreshold(0.85);
rule.setDuration("5m");
rule.setLevel(AlertLevel.CRITICAL);
rule.setMessage("堆内存使用率已达 {value}%，请及时排查！");
rule.setNotifierTypes(List.of("dingtalk", "mail"));

// 发送告警
alertNotifier.notify(rule, "堆内存使用率超过 85%");

// 管理渠道
List<String> types = alertNotifier.getChannelTypes();
alertNotifier.removeChannel("wechat");
```

也可通过实现 `AlertChannel` 接口自定义通知渠道：

```java
@Component
public class SmsNotifier implements AlertChannel {
    @Override
    public String getType() { return "sms"; }

    @Override
    public void send(String title, String message) {
        // 发送短信逻辑
    }
}
```
