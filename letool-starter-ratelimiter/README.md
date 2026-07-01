# letool-starter-ratelimiter

> 限流熔断模块，支持令牌桶/滑动窗口/漏桶算法，熔断器（CLOSED/OPEN/HALF_OPEN 三态），降级处理。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-ratelimiter</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 添加依赖并配置

```yaml
letool:
  rate-limiter:
    enabled: true
    algorithm: token-bucket
    default-permits-per-second: 100
```

### 2. 注解式限流

```java
@RestController
public class SmsController {

    // 按手机号限流：每秒 1 次
    @RateLimit(key = "'sms:' + #phone", permitsPerSecond = 1)
    @PostMapping("/sms/send")
    public String sendSms(String phone) {
        smsService.send(phone);
        return "ok";
    }

    // 带降级方法
    @RateLimit(key = "'order:' + #orderId", fallbackMethod = "rateLimitFallback")
    public String createOrder(String orderId) {
        return orderService.create(orderId);
    }
    public String rateLimitFallback(String orderId) {
        return "系统繁忙，请稍后再试";
    }
}
```

### 3. 编程式限流与熔断

```java
@Autowired
private RateLimitTemplate rateLimitTemplate;
@Autowired
private CircuitBreaker circuitBreaker;

// 函数式限流
String result = rateLimitTemplate.executeOrFallback("sms:138xxxx",
    () -> sendSms(),
    () -> "发送频率过高，请稍后重试"
);

// 熔断保护
if (!circuitBreaker.isAllowed()) {
    return "服务暂时不可用";
}
try {
    Result r = externalService.call(req);
    circuitBreaker.recordSuccess();
} catch (Exception e) {
    circuitBreaker.recordFailure();
}
```

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `letool.rate-limiter.enabled` | boolean | true | 是否启用限流模块 |
| `letool.rate-limiter.algorithm` | String | token-bucket | 限流算法：token-bucket / sliding-window |
| `letool.rate-limiter.default-permits-per-second` | int | 100 | 默认每秒许可数 |
| `letool.rate-limiter.circuit-breaker.enabled` | boolean | true | 是否启用熔断 |
| `letool.rate-limiter.circuit-breaker.failure-threshold` | double | 0.5 | 失败率阈值 |
| `letool.rate-limiter.circuit-breaker.window-size` | int | 60 | 统计窗口（秒） |
| `letool.rate-limiter.circuit-breaker.recovery-timeout` | int | 60 | 熔断恢复超时（秒） |

## 核心 API

### 注解声明式——@RateLimit 限流

```java
// 固定 key 限流——每秒最多 10 次
@RateLimit(key = "send-sms", permitsPerSecond = 10)
public void sendSms() { ... }

// 动态 key（SpEL 表达式）——按手机号限流
@RateLimit(key = "'sms:' + #phone", permits = 1, permitsPerSecond = 1)
public void sendSms(String phone) { ... }

// 滑动窗口算法
@RateLimit(key = "'api:' + #apiKey", algorithm = "sliding-window", permitsPerSecond = 50)
public String callApi(String apiKey) { ... }

// 带降级方法——回退方法必须同参数签名
@RateLimit(key = "'order:' + #orderId", fallbackMethod = "rateLimitFallback")
public String createOrder(String orderId) { ... }

public String rateLimitFallback(String orderId) {
    return "系统繁忙，请稍后再试";
}
```

### 注解声明式——@CircuitBreak 熔断

```java
// 基础熔断保护
@CircuitBreak(name = "payment-service")
public PaymentResult pay(PaymentRequest req) { ... }

// 自定义阈值与降级
@CircuitBreak(name = "inventory", failureThreshold = 0.3,
              windowSize = 60, recoveryTimeout = 30,
              fallbackMethod = "inventoryFallback")
public InventoryResult checkInventory(String sku) { ... }

public InventoryResult inventoryFallback(String sku) {
    return InventoryResult.defaultValue();
}
```

### 编程式——RateLimitTemplate 函数式限流

```java
@Autowired
private RateLimitTemplate rateLimitTemplate;

// 简单许可检查
if (rateLimitTemplate.tryAcquire("sms:138xxxx")) {
    sendSms();
}

// 带降级的执行（有返回值）
String result = rateLimitTemplate.executeOrFallback("order:123",
    () -> createOrder(),
    () -> "系统繁忙，请稍后重试"
);

// 被限流时抛出异常
User user = rateLimitTemplate.executeOrThrow("user:" + userId,
    () -> userService.getUser(userId)
);

// 指定许可数
rateLimitTemplate.executeOrFallback("batch-import", 5,
    () -> batchImport(),
    () -> log.warn("导入被限流")
);

// Builder 模式
rateLimitTemplate.builder()
    .key("sms:138xxxx")
    .permits(1)
    .executeOrFallback(this::sendSms, this::sendSmsFallback);

// 获取详细结果
RateLimitResult result = rateLimitTemplate.tryAcquireWithResult("api:xxx", 1);
// result.isAllowed() / result.getWaitTimeMs()
```

### 编程式——CircuitBreaker 熔断器手动控制

```java
@Autowired
private CircuitBreaker circuitBreaker;

public Result callExternalService(Request req) {
    // 熔断状态检查
    if (!circuitBreaker.isAllowed()) {
        return Result.error("服务暂时不可用");  // 快速失败
    }
    try {
        Result result = externalService.call(req);
        circuitBreaker.recordSuccess();  // 记录成功
        return result;
    } catch (Exception e) {
        circuitBreaker.recordFailure();  // 记录失败
        throw e;
    }
}

// 查询熔断器状态
CircuitBreakerState state = circuitBreaker.getState();
// CLOSED / OPEN / HALF_OPEN

// 强制重置
circuitBreaker.reset();
```
