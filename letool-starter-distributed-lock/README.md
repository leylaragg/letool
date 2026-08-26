# letool-starter-distributed-lock

## 模块定位

`letool-starter-distributed-lock` 提供后端无关的分布式锁与幂等契约：

- `DistributedLock`、`LockHandle`、`LockRequest`；
- 自动释放锁的 `LockTemplate`；
- 可替换的 `IdempotentStore` 与 `IdempotentService`；
- 声明式 `@Lock`、`@Idempotent` 及其 AOP；
- 支持方法参数、`#p0`、`#a0`、`#target`、`#method` 和 `#args` 的 SpEL Key。

本模块不依赖 Redis 或 Redisson。单独引入时，如果应用没有提供 `DistributedLock` 或
`IdempotentStore`，相关模板和切面不会创建。需要 Letool 默认 Redis 实现时，引入
[`letool-starter-redis`](../letool-starter-redis/README.md)。

## 引入依赖

只使用通用契约并自行提供后端：

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-distributed-lock</artifactId>
    <version>${letool.version}</version>
</dependency>
```

使用默认 Redisson 锁和 Redis 幂等存储：

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-redis</artifactId>
    <version>${letool.version}</version>
</dependency>
```

Redis Starter 会传递本模块，不需要重复声明。

## 声明式分布式锁

```java
@Lock(
    key = "order:#{#orderId}",
    waitTime = 3,
    leaseTime = -1
)
public OrderResult processOrder(long orderId) {
    return orderService.process(orderId);
}
```

`leaseTime = -1` 表示把租约交给后端维护。Redisson 后端会使用看门狗续期；设置正数则表示固定
租约秒数，到期后其他调用方可以接管。锁等待超时会抛出 `LockException`，不会继续执行业务方法。

Key 使用 Spring 模板表达式语法 `#{...}`：

```java
@Lock(key = "inventory:#{#request.skuId}")
public void reserve(ReserveRequest request) {
}
```

业务方法抛出的运行时异常和错误会保持原样传播，不会被切面包装成锁异常。

## 编程式分布式锁

```java
OrderResult result = lockTemplate.execute(
        LockRequest.watchdog("order:" + orderId, Duration.ofSeconds(3)),
        () -> orderService.process(orderId));
```

固定租约应显式表达：

```java
lockTemplate.execute(
        LockRequest.fixedLease(
                "short-job:" + jobId,
                Duration.ofSeconds(1),
                Duration.ofSeconds(10)),
        () -> shortJob.run(jobId));
```

`DistributedLock#tryAcquire` 成功时返回绑定本次所有权的 `LockHandle`。模板通过
try-with-resources 关闭这个句柄，避免按 Key 重新定位锁时误释放其他线程后来取得的所有权。

## 幂等保护

```java
@Idempotent(key = "payment:#{#request.requestNo}", ttl = 86400)
public PaymentResult pay(PaymentRequest request) {
    return paymentService.pay(request);
}
```

首次请求成功占用标记后执行业务方法，TTL 内的重复请求直接返回 `null`。如果业务方法抛出运行时
异常或错误，`IdempotentService` 会删除本次标记，允许后续请求重试；业务异常保持原样传播。

编程式入口：

```java
PaymentResult result = idempotentService.execute(
        "payment:" + request.requestNo(),
        Duration.ofHours(24),
        () -> paymentService.pay(request));
```

调用方应保证幂等 Key 能唯一标识一次业务意图，并根据业务最长重试窗口设置 TTL。返回 `null` 既可能
表示重复请求，也可能是业务回调的真实返回值；需要结构化区分时，应在业务层使用明确结果类型。

## 自定义后端

应用可以实现并注册自己的锁和幂等存储：

```java
@Bean
DistributedLock distributedLock() {
    return new DatabaseDistributedLock(...);
}

@Bean
IdempotentStore idempotentStore() {
    return new DatabaseIdempotentStore(...);
}
```

自动配置按类型退让。后端必须保证：

- 一次成功获取返回一个只代表该次所有权的句柄；
- `close()` 幂等，且不会释放当前调用方已不再拥有的锁；
- `IdempotentStore#putIfAbsent` 的写入与 TTL 设置具有原子语义；
- 线程中断、等待超时和后端异常具有可预测且可观测的处理方式。

## 配置

```yaml
letool:
  lock:
    enabled: true
    idempotent:
      enabled: true
```

锁 Key 前缀、公平性、幂等 Key 前缀等实现参数归具体后端模块管理。Redis 后端配置请参阅
[`letool-starter-redis`](../letool-starter-redis/README.md)，本模块不再声明 `backend`、
`fair-lock`、`auto-renewal` 或 `renewal-interval` 等 Redis 专用配置。
