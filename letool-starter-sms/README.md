# letool-starter-sms

> 短信模块，阿里云/腾讯云短信统一 API，支持模板发送、频率限制、发送记录。

> ⚠️ 当前 Aliyun/Tencent provider 为 Stub/模拟实现，Mock provider 也不会发送真实短信。短信 starter 默认不启用；如需开发演示必须显式设置 `letool.sms.mock-enabled=true`，生产接入请在业务项目中注册真实 `SmsProvider`。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-sms</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（开发 Mock 模式）

### 1. 添加依赖并配置

```yaml
letool:
  sms:
    enabled: true
    mock-enabled: true
    default-provider: mock
    aliyun:
      access-key-id: your-access-key-id
      access-key-secret: your-access-key-secret
      sign-name: 您的应用
      region-id: cn-hangzhou
    rate-limit:
      enabled: true
      max-per-minute: 10
      max-per-day: 100
```

### 2. 发送短信

```java
@Autowired
private SmsTemplate smsTemplate;

// 链式发送验证码
SmsResult result = smsTemplate.builder()
        .to("13800138000")
        .template("SMS_001")
        .param("code", "1234")
        .send();

if (result.isSuccess()) {
    log.info("发送成功, requestId={}", result.getRequestId());
}
```

### 3. 批量发送

```java
SmsResult result = smsTemplate.batchSend(
    List.of("13800138000", "13900139000"),
    "SMS_003",
    Map.of("activity", "年中大促")
);
```

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `letool.sms.enabled` | boolean | false | 是否启用短信模块 |
| `letool.sms.mock-enabled` | boolean | false | 是否允许创建内置 Mock/Stub provider；生产环境建议关闭并注册真实 SmsProvider |
| `letool.sms.default-provider` | String | mock | 默认模拟服务商：aliyun / tencent / mock |
| `letool.sms.aliyun.access-key-id` | String | - | 阿里云 AccessKey ID |
| `letool.sms.aliyun.access-key-secret` | String | - | 阿里云 AccessKey Secret |
| `letool.sms.aliyun.sign-name` | String | - | 短信签名（需审核通过） |
| `letool.sms.aliyun.region-id` | String | cn-hangzhou | 短信服务地域 |
| `letool.sms.tencent.secret-id` | String | - | 腾讯云 SecretId |
| `letool.sms.tencent.secret-key` | String | - | 腾讯云 SecretKey |
| `letool.sms.tencent.app-id` | String | - | 短信应用 SDK AppID |
| `letool.sms.tencent.sign-name` | String | - | 短信签名内容 |
| `letool.sms.rate-limit.enabled` | boolean | true | 是否启用频率限制 |
| `letool.sms.rate-limit.max-per-minute` | int | 10 | 每分钟最大发送次数 |
| `letool.sms.rate-limit.max-per-day` | int | 100 | 每天最大发送次数 |

## 核心 API

### 编程式——SmsTemplate Builder 链式发送

```java
@Autowired
private SmsTemplate smsTemplate;

// 基本验证码发送
SmsResult result = smsTemplate.builder()
        .to("13800138000")
        .template("SMS_VERIFY_CODE")
        .param("code", "1234")
        .send();

// 多个模板参数
SmsResult result = smsTemplate.builder()
        .to("13800138000")
        .template("SMS_WELCOME")
        .param("user", "张三")
        .param("product", "Ailind 企业工具包")
        .param("expireTime", "2024-12-31")
        .send();

// 批量添加参数
Map<String, String> paramMap = Map.of("code", "5678", "product", "Ailind");
SmsResult result = smsTemplate.builder()
        .to("13800138000")
        .template("SMS_PROMOTION")
        .params(paramMap)
        .send();
```

### 编程式——直接发送（非 Builder）

```java
// 单条发送
SmsResult result = smsTemplate.send(
    "13800138000",
    "SMS_NOTIFY",
    Map.of("title", "订单已发货", "orderNo", "20240101001")
);

// 批量发送
SmsResult result = smsTemplate.batchSend(
    List.of("13800138000", "13900139000", "13700137000"),
    "SMS_BATCH",
    Map.of("activity", "双11大促", "discount", "5折")
);
```

### 编程式——频率限制与监控

```java
// 内置频率限制（基于 ConcurrentHashMap，自动检查分钟级和天级限制）
// 超限时抛出 SmsException
try {
    smsTemplate.builder()
        .to("13800138000")
        .template("SMS_TEST")
        .send();
} catch (SmsException e) {
    // 频率超限："短信发送频率超限，每分钟最多发送 10 条"
    log.warn("发送失败: {}", e.getMessage());
}

// 获取频率计数快照（用于监控面板）
Map<String, Integer> snapshot = smsTemplate.getRateLimitSnapshot("13800138000");
// 返回: {"minute:202601011430": 5, "day:20260101": 42}
```

### 开发 Mock——通过配置切换模拟服务商

```yaml
letool:
  sms:
    enabled: true
    mock-enabled: true
    default-provider: tencent    # 切换到腾讯云模拟 provider，无需修改业务代码
    tencent:
      secret-id: your-secret-id
      secret-key: your-secret-key
      app-id: 1400006666
      sign-name: 您的应用
    rate-limit:
      enabled: true
      max-per-minute: 5           # 收紧频率限制
      max-per-day: 50
```

模块仅在 `mock-enabled=true` 时根据 `default-provider` 自动创建对应的模拟 `SmsProvider` 实现。业务代码注入的 `SmsTemplate` 无需任何改动即可切换模拟服务商。生产环境请注册自己的真实 `SmsProvider` Bean；自动配置会检测到该 Bean 并退让。频率限制基于内存中 `ConcurrentHashMap` + `AtomicInteger` 实现，保证并发安全，重启后计数清零。
