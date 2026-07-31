# letool-starter-ratelimiter

> 基于 Alibaba Sentinel Core 的轻量限流封装，提供命名策略、热点参数限流、编程式 API 与 `@RateLimit`。

## 设计边界

本模块不再维护自研令牌桶、滑动窗口和熔断状态机。Letool 只封装业务开发中常用的策略选择、
SpEL key 解析和统一异常；流量统计、并发安全及限流算法由 Sentinel 负责。

本模块默认不会引入 Spring Cloud Alibaba 的 Web 自动拦截链路，也不会自动保护所有 URL。
需要 Sentinel 控制台、Nacos 动态数据源或熔断降级时，请在业务项目中按其版本体系接入
Spring Cloud Alibaba Sentinel。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-ratelimiter</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 本地规则快速开始

```yaml
letool:
  rate-limiter:
    enabled: true
    default-policy: default
    local-rules-enabled: true
    policies:
      default:
        threshold: 10
      send-sms:
        threshold: 1
      create-order:
        threshold: 20
    annotation:
      enabled: true
```

每个策略会生成两类独立的 Sentinel 资源：

- `key` 为空时，对整个策略执行普通 QPS 限流。
- `key` 非空时，将 key 作为第 0 个热点参数，对每个 key 独立执行 QPS 限流。

本地规则模式会校验调用的策略名；策略不存在时直接抛出配置异常，避免 Sentinel 在无规则时
默认放行。关闭本地规则后，策略名称由外部动态数据源负责校验和管理。

## 声明式限流

```java
@RestController
public class SmsController {

    @RateLimit(
            policy = "send-sms",
            keyExpression = "#phone",
            fallbackMethod = "sendFallback"
    )
    @PostMapping("/sms/send")
    public String sendSms(String phone) {
        smsService.send(phone);
        return "ok";
    }

    private String sendFallback(String phone) {
        return "发送频率过高，请稍后重试";
    }
}
```

注解提供两种互斥的 key 配置：

- `key`：始终作为固定文本，可安全包含 `#` 等字符。
- `keyExpression`：使用方法上下文 SpEL，可访问参数名、`#p0`、`#a0`、
  `#target`、`#method` 和 `#args`。
- 两者均留空：策略级全局限流；两者不能同时配置。

SpEL 解析失败会抛出 `SpelException`，不会静默使用原始表达式作为 key；表达式结果为
`null` 或空白字符串时会抛出 `RateLimitConfigurationException`，避免意外退化为全局限流。
回退方法必须位于同一类中，参数列表与返回类型必须兼容。

## 编程式限流

```java
@Autowired
private RateLimitTemplate rateLimitTemplate;

// 使用默认策略，按手机号独立限流
boolean allowed = rateLimitTemplate.tryAcquire("13800138000");

// 显式指定策略、key 与许可数
boolean batchAllowed = rateLimitTemplate.tryAcquire(
        "create-order", "user:1001", 2);

// 被拒绝时执行回退逻辑
String result = rateLimitTemplate.executeOrFallback(
        "send-sms",
        "13800138000",
        () -> sendSms(),
        () -> "发送频率过高，请稍后重试"
);

// 被拒绝时抛出统一业务异常 RATE_LIMIT_001
Order order = rateLimitTemplate.executeOrThrow(
        "create-order",
        "user:1001",
        () -> orderService.create()
);

// Builder 模式
rateLimitTemplate.builder()
        .policy("send-sms")
        .key("13800138000")
        .permits(1)
        .executeOrFallback(this::sendSms, this::sendFallback);
```

`RateLimitResult` 只暴露可靠的 `allowed` 和 `blockReason`。Sentinel 不提供精确剩余许可或等待时间，
因此 Letool 不再伪造这两个数值。

## 外部动态规则

生产环境由 Sentinel 控制台、Nacos 或其他数据源管理规则时，应关闭本地规则注册：

```yaml
letool:
  rate-limiter:
    enabled: true
    local-rules-enabled: false
```

Letool 使用以下资源名称，外部规则需要采用相同约定：

```text
letool:rate-limit:{policy}:global
letool:rate-limit:{policy}:keyed
```

`keyed` 资源必须配置 Sentinel 热点参数规则，并将 `paramIdx` 设置为 `0`。

## 熔断能力迁移

旧版 `@CircuitBreak`、`CircuitBreaker`、`DefaultCircuitBreaker` 和自研状态机已删除。
熔断规则、异常统计、半开探测、控制台和动态数据源属于 Sentinel 的原生治理能力，不再由 Letool 重复实现。

业务项目可直接使用 Spring Cloud Alibaba Sentinel 的 `@SentinelResource` 与降级规则，或者选择
Resilience4j。Letool 不再包装这些成熟能力，以免隐藏框架原生配置并形成第二套不完整语义。

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `letool.rate-limiter.enabled` | boolean | `true` | 是否启用限流模块 |
| `letool.rate-limiter.default-policy` | String | `default` | 默认策略名称 |
| `letool.rate-limiter.local-rules-enabled` | boolean | `true` | 是否注册本地静态规则 |
| `letool.rate-limiter.policies.<name>.threshold` | double | `10` | 策略每秒许可阈值 |
| `letool.rate-limiter.annotation.enabled` | boolean | `true` | 是否启用 `@RateLimit` 切面 |

## 破坏性变更

| 旧能力 | 新方案 |
|--------|--------|
| 注解中的 `permitsPerSecond`、`algorithm` | 阈值迁移到 `policies.<name>.threshold` |
| 注解 `key` 中的 SpEL | 迁移到语义明确的 `keyExpression` |
| 自研令牌桶与滑动窗口实现 | Sentinel 普通流控与热点参数流控 |
| `RateLimiter#reset/getAvailablePermits` | 删除；规则与指标由 Sentinel 管理 |
| `RateLimitResult.availablePermits/waitTimeMs` | 删除不可靠数值，保留阻断类型 |
| `@CircuitBreak` 与自研熔断器 | 使用 Sentinel 原生熔断或 Resilience4j |
