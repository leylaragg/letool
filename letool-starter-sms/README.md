# letool-starter-sms

短信公共契约和便捷门面。核心模块不携带云厂商 SDK，真实发送能力由独立 Provider 模块提供。

## 如何选择依赖

只使用阿里云时，只需要引入一个依赖：

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-sms-aliyun</artifactId>
</dependency>
```

只使用腾讯云时，也只需要引入一个依赖：

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-sms-tencent</artifactId>
</dependency>
```

Provider 模块会传递引入 `letool-starter-sms`。只有同时使用两家厂商时才需要同时引入两个 Provider 依赖，并配置 `letool.sms.default-provider`。

自定义 `SmsProvider` 或只使用 Mock 时，可以单独引入核心模块。

## 公共配置

```yaml
letool:
  sms:
    enabled: true
    default-provider: aliyun # 只有一个 Provider 时可以省略
    rate-limit:
      enabled: true
      max-per-minute: 10
      max-per-day: 100
      maximum-tracked-phones: 100000
```

默认限流统计发送尝试，使用有界且自动过期的单 JVM 缓存。多节点应用应注册自定义 `SmsRateLimiter` Bean，实现 Redis、网关或其他集群级限制。

## 发送短信

```java
SmsResult result = smsTemplate.builder()
        .to("13800138000")
        .template("SMS_001")
        .param("code", "1234")
        .send();
```

批量发送：

```java
SmsResult result = smsTemplate.builder()
        .to(List.of("13800138000", "13900139000"))
        .template("SMS_002")
        .param("code", "1234")
        .send();
```

同时引入多个 Provider 时，可以覆盖默认选择：

```java
SmsResult result = smsTemplate.builder()
        .provider("tencent")
        .to("+8613800138000")
        .template("123456")
        .param("code", "1234")
        .send();
```

`SmsResult#getRecipientResults()` 会返回逐手机号状态，批量请求出现部分失败时 `isSuccess()` 返回 `false`。

## Mock 模式

Mock 只用于开发和测试，必须显式启用：

```yaml
letool:
  sms:
    enabled: true
    mock:
      enabled: true
```

## 扩展点

- 注册 `SmsProvider`：接入其他短信服务商。
- 注册 `SmsRateLimiter`：替换本地限流。
- 注册 `SmsTemplate`：完全接管短信门面。
- 注册厂商官方客户端 Bean：接管动态凭证、代理、连接和超时配置。

框架不会自动重试发送请求，避免网络结果不确定时产生重复短信和重复费用。日志不会记录完整手机号、模板参数或访问密钥。
