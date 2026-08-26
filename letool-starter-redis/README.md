# letool-starter-redis

## 模块定位

`letool-starter-redis` 是 Letool 的 Redis 业务基础设施模块。它统一提供：

- 基于 `RedisTemplate<String, Object>` 的 KV、Hash、List、Set、ZSet、Lua 和 Pipeline 便利 API；
- Redisson 原生 `RLock` 与自动释放的 `executeWithLock`；
- 带分布式锁、锁内双检、空值哨兵和 TTL 抖动的 `getOrLoad`；
- `letool-starter-distributed-lock` 所需的 Redisson 锁后端和 Redis 幂等存储；
- Redis 消息队列便利门面与受白名单保护的 Fastjson2 对象序列化器。

它不替代 `letool-starter-cache`。后者继续负责 Caffeine L1、多级缓存、跨节点失效广播和
数据库一致性围栏；本模块只处理通用 Redis 访问、分布式锁和单值缓存回源保护。

## 引入依赖

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-redis</artifactId>
    <version>${letool.version}</version>
</dependency>
```

应用仍通过 Spring Boot 的 `spring.data.redis.*` 配置连接。Starter 检测到唯一
`RedisConnectionFactory` 且业务没有提供名为 `redisTemplate` 的对象模板时，会创建兼容的
Fastjson2 模板；业务自定义模板始终优先，Letool 不会改写其序列化器。

## 直接读写 Redis

```java
@Service
class UserRedisService {

    private final RedisUtil redisUtil;

    UserRedisService(RedisUtil redisUtil) {
        this.redisUtil = redisUtil;
    }

    void save(User user) {
        redisUtil.set("user:" + user.id(), user, Duration.ofMinutes(30));
        redisUtil.hset("user:profile:" + user.id(), "name", user.name());
    }

    User find(long userId) {
        return redisUtil.get("user:" + userId, User.class);
    }
}
```

原有 `setObject/getObject`、集合操作、Lua 和 Pipeline API 已迁入本模块，方法语义保持兼容。

## 分布式锁

普通业务优先使用自动释放入口：

```java
redisUtil.executeWithLock("order:" + orderId, () -> {
    orderService.process(orderId);
});
```

需要 Redisson 原生高级能力时，可以直接取得已应用统一前缀和公平性配置的锁：

```java
RLock lock = redisUtil.getLock("order:" + orderId);
```

`executeWithLock` 使用 Redisson 看门狗模式。业务明确需要固定租约时，应注入 `LockTemplate`，
并提交 `LockRequest.fixedLease(...)`；不要通过猜测业务耗时设置过短租约。

## 缓存回源保护

使用默认策略读取缓存，未命中时才调用数据库：

```java
User user = redisUtil.getOrLoad(
        "user:" + userId,
        User.class,
        Duration.ofMinutes(30),
        () -> userMapper.selectById(userId));
```

需要按业务控制空值、抖动和写入条件时，传入不可变策略：

```java
User user = redisUtil.getOrLoad(
        "user:" + userId,
        User.class,
        RedisCachePolicy.<User>builder(Duration.ofMinutes(30))
                .cacheNull(Duration.ofMinutes(2))
                .ttlJitter(Duration.ofMinutes(5))
                .cacheable(User::isActive)
                .build(),
        () -> userMapper.selectById(userId));
```

三类保护解决的问题不同：

- 分布式锁加锁后再次检查缓存，只允许一个调用方回源，解决热点 Key 缓存击穿；
- 数据库返回空值时写入短 TTL 哨兵，避免不存在的 Key 反复访问数据库，缓解缓存穿透；
- 正常缓存 TTL 追加有界随机抖动，分散大量 Key 同时失效的时间，缓解缓存雪崩。

锁等待超时后不会绕过互斥直接查询数据库；若最后一次缓存读取仍未命中，会抛出
`RedisOperationException`。数据源回调抛出的运行时异常保持原样传播，也不会被误写成空值。

## 声明式锁与幂等

本模块会为 `letool-starter-distributed-lock` 提供默认的 `DistributedLock` 和
`IdempotentStore`：

```java
@Lock(key = "order:#{#orderId}")
public void process(long orderId) {
    // 同一订单在分布式锁内串行处理
}

@Idempotent(key = "payment:#{#request.requestNo}", ttl = 86400)
public PaymentResult pay(PaymentRequest request) {
    // TTL 内重复请求不会再次执行业务方法
}
```

单独引入 `letool-starter-distributed-lock` 时只包含后端无关的契约、模板和 AOP，不会引入 Redis。
需要默认 Redisson/Redis 实现时，应引入本模块。

## 配置

```yaml
spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379

letool:
  redis:
    serialization:
      # Redis 多态值白名单应尽量收窄到实际业务包。
      auto-type-accept-prefixes:
        - org.springframework
        - io.github.leylaragg
        - com.example.order
    lock:
      key-prefix: "letool:lock:"
      fair: false
    idempotent:
      key-prefix: "letool:idempotent:"
    cache:
      cache-null: true
      null-ttl: 2m
      ttl-jitter: 30s
      lock-wait: 3s
      lock-key-prefix: "cache:"

  lock:
    enabled: true
    idempotent:
      enabled: true
```

序列化白名单是独立的安全边界，不会复用通用 `JsonCodec` 配置。业务自行提供对象模板时，
序列化格式、类型白名单和历史数据兼容由该模板的所有者负责。

## 从 Tool 迁移

Redis 能力已从通用工具模块迁出：

```text
io.github.leylaragg.letool.tool.redis.RedisUtil
    -> io.github.leylaragg.letool.redis.RedisUtil

io.github.leylaragg.letool.tool.redis.queue.RedisMessageQueueUtil
    -> io.github.leylaragg.letool.redis.queue.RedisMessageQueueUtil

io.github.leylaragg.letool.tool.redis.serializer.FastJson2JsonRedisSerializer
    -> io.github.leylaragg.letool.redis.serializer.FastJson2JsonRedisSerializer
```

原先只引入 `letool-starter-tool` 并依靠其可选 Redis 能力的应用，现在必须显式引入
`letool-starter-redis`。Tool 不再传递 Spring Data Redis 或 Redisson。

## 真实 Redis 验证

默认测试不会启动外部服务。显式运行以下 Profile 时，测试优先使用
`LETOOL_TEST_REDIS_HOST`、`LETOOL_TEST_REDIS_PORT`、`LETOOL_TEST_REDIS_PASSWORD`；未提供 Host
时启动 `redis:7.2-alpine` Testcontainers：

```powershell
.\mvnw.cmd -pl letool-starter-redis -am -Predis-integration test
```
