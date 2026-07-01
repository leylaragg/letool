# letool-starter-distributed-lock

## 模块简介

分布式锁模块，提供基于 Redis 的分布式锁和幂等性保证能力。支持注解声明式（`@Lock`、`@Idempotent`）和编程式（`LockTemplate`、`IdempotentService`）两种使用方式。内置自动续期（看门狗）、公平锁、SpEL 动态 key、幂等标记回滚等特性。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-distributed-lock</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

**Step 1** — 配置 Redis 连接和锁参数：

```yaml
letool:
  lock:
    enabled: true
    backend: redis
    pessimistic:
      lock-prefix: "myapp:lock:"
      default-lease-time: 30      # 默认持锁 30 秒
      default-wait-time: 3        # 默认等待 3 秒
      auto-renewal: true          # 看门狗自动续期
    idempotent:
      enabled: true
      key-prefix: "myapp:idempotent:"
      ttl: 86400
```

**Step 2** — 在业务方法上添加注解：

```java
// 注解式分布式锁 —— 同一 orderId 串行执行
@Lock(key = "order:#{#orderId}", waitTime = 5, leaseTime = 60)
public void processOrder(Long orderId) {
    // 业务逻辑
}

// 注解式幂等性 —— 同一 orderId 一小时内仅执行一次
@Idempotent(key = "pay:#{#orderId}", ttl = 3600)
public PaymentResult pay(Long orderId) {
    return doPay(orderId);
}
```

**Step 3** — 编程式加锁（无注解时）：

```java
@Autowired
private LockTemplate lockTemplate;

lockTemplate.execute("order:123", () -> {
    processOrder(123);  // 自动加锁、自动释放
});
```

## 配置属性

```yaml
letool:
  lock:
    enabled: true                   # 模块开关，默认 true
    backend: redis                  # 后端实现，当前仅 redis
    pessimistic:
      lock-prefix: "letool:lock:"  # Redis 锁 key 前缀
      default-lease-time: 30       # 默认持锁时间（秒），默认 30
      default-wait-time: 3         # 默认等待时间（秒），默认 3
      fair-lock: false             # 是否公平锁（排队获取），默认 false
      auto-renewal: true           # 看门狗自动续期，默认 true
      renewal-interval: 10         # 续期间隔（秒），默认 10
    idempotent:
      enabled: true                # 幂等检查开关，默认 true
      key-prefix: "letool:idempotent:"  # 幂等 key 前缀
      ttl: 86400                   # 幂等标记 TTL（秒），默认 86400（24h）
```

## 核心 API 示例

### 1. 注解声明式：@Lock 分布式锁

通过 AOP 在方法执行前自动获取锁，执行后自动释放。key 支持 SpEL 表达式动态拼接参数：

```java
@Lock(key = "inventory:#{#skuId}", waitTime = 3, leaseTime = 30, timeUnit = TimeUnit.SECONDS)
public void deductStock(Long skuId, int quantity) {
    // 同一 skuId 的扣减操作串行执行，防止超卖
    inventoryMapper.deduct(skuId, quantity);
}
```

注解属性：
- `key()` — 锁标识，支持 SpEL（如 `"#{#userId}"` 引用方法参数）
- `waitTime()` — 等待获取锁最长时间（默认 3 秒），超时抛 `LockException`
- `leaseTime()` — 持锁租约时间（默认 30 秒），到期自动释放防止死锁
- `timeUnit()` — 时间单位，默认秒

### 2. 注解声明式：@Idempotent 幂等性

基于 Redis Lua 脚本（SET NX EX）原子标记，首次请求执行，TTL 内重复请求直接返回 null：

```java
@Idempotent(key = "pay:#{#orderId}", ttl = 3600)
public PaymentResult pay(Long orderId) {
    // 一小时内同一 orderId 的支付请求仅执行一次
    // 重复请求返回 null，调用方需自行判空
    return paymentGateway.pay(orderId);
}

// 调用方判空处理
public void handlePay(Long orderId) {
    PaymentResult result = pay(orderId);
    if (result == null) {
        throw new BusinessException("订单已支付，请勿重复操作");
    }
}
```

### 3. 编程式：LockTemplate 函数式锁

提供多个重载方法，支持 Lambda 表达式和默认超时参数，自动 try-finally 释放锁：

```java
@Autowired
private LockTemplate lockTemplate;

// 有返回值 + 自定义超时
String result = lockTemplate.execute("order:123", 5, 60, TimeUnit.SECONDS, () -> {
    return processOrder(123);
});

// 无返回值 + 默认超时（3s 等待 / 30s 持锁）
lockTemplate.execute("order:123", () -> {
    processOrder(123);
});

// 有返回值 + 默认超时
String result = lockTemplate.execute("order:123", () -> processOrder(123));
```

### 4. 编程式：IdempotentService 幂等性

直接使用 `IdempotentService.execute()`，在编程式场景下实现幂等控制：

```java
@Autowired
private IdempotentService idempotentService;

public PaymentResult pay(Long orderId) {
    return idempotentService.execute("pay:" + orderId, 3600, () -> {
        return paymentGateway.pay(orderId);
    });
}
```

注意：如果 supplier 抛出异常，IdempotentService 会自动删除 Redis 中的幂等标记（回滚），允许后续请求重试。

### 5. 自动续期（看门狗）

配置 `auto-renewal: true` 后，持锁期间每隔 `renewal-interval` 秒自动续期，防止业务执行超时导致锁提前释放。适合处理时间不确定的批量任务。配置 `fair-lock: true` 可启用公平锁，按请求顺序排队获取。
