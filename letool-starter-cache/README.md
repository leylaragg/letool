# letool-starter-cache

`letool-starter-cache` 是 letool 提供的二级缓存 starter，核心目标是给业务项目提供一套轻量、可控、可降级的缓存能力。

- L1 使用 Caffeine，本地进程内高速读取。
- L2 使用 Redis，多 JVM 共享缓存数据。
- 支持注解式和编程式两种接入方式。
- 支持强一致版本校验、跨 JVM L1 失效广播、Redis 异常降级和恢复探测。
- 支持 null 值哨兵，减少不存在数据反复穿透数据库。
- 支持 KV 缓存和 Set 缓存，Set 缓存适合规则索引、标签索引、ID 集合等场景。

## 适用场景

推荐使用：

- 读多写少的数据，例如用户详情、字典、配置、规则元数据。
- 多实例部署下需要 L1 本地加速，同时又希望 Redis 作为共享缓存。
- Redis 短暂异常时，业务更希望继续走本地缓存或回源，而不是被缓存层拖死。
- 需要对 null 结果做短 TTL 缓存，防止缓存穿透。

谨慎使用：

- 金融扣款、库存扣减等强事务写路径。
- 值非常大或 key 数量不可控的缓存区域。
- 写入极高频且要求每次读取都绝对实时的场景。

## Maven 引入

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-cache</artifactId>
    <version>${letool.version}</version>
</dependency>
```

如果项目需要 L2 Redis，请确保业务工程已经配置 Spring Data Redis，并引入 `letool-starter-tool` 中的 Redis 能力。

## 快速开始

### 1. 通过配置预注册缓存

```yaml
letool:
  cache:
    enabled: true
    redis-prefix: "myapp:cache:"
    l1-enabled: true
    l2-enabled: true
    strong-consistency: true
    instances:
      - name: userCache
        l1-max-size: 2000
        l1-ttl: 30m
        l2-ttl: 2h
        strong-consistency: true
        null-value-cache: true
        null-value-ttl: 3m
    invalidation:
      enabled: true
      channel: "myapp:cache:invalidation"
    degradation:
      recovery-enabled: true
      recovery-interval: 30s
    annotation:
      enabled: true
    monitoring:
      enabled: true
```

### 2. 使用注解读取缓存

```java
@MultiLevelCacheable(name = "userCache", key = "#userId", ttl = 1800)
public User getUser(Long userId) {
    return userMapper.selectById(userId);
}
```

说明：

- `name` 必须对应一个已经注册的缓存实例。
- `key` 支持 SpEL，例如 `#userId`、`#request.userId`。
- `ttl` 单位是秒；不设置或设置为 `0` 时使用缓存实例默认 L2 TTL。
- 业务方法抛出的异常会原样抛回，不会因为接入缓存注解而改变异常类型。

### 3. 更新或清理缓存

```java
@MultiLevelCachePut(name = "userCache", key = "#user.id", ttl = 1800)
public User updateUser(User user) {
    userMapper.updateById(user);
    return user;
}

@MultiLevelCacheEvict(name = "userCache", key = "#userId")
public void deleteUser(Long userId) {
    userMapper.deleteById(userId);
}
```

### 4. 编程式使用

```java
@Configuration
public class CacheConfiguration {

    @Bean
    public MultiLevelCache<String, User> userCache(CacheManager cacheManager) {
        CacheConfig<String, User> config = CacheConfig.<String, User>builder("userCache")
                .l1MaxSize(2000)
                .l1Ttl(Duration.ofMinutes(30))
                .l2Ttl(Duration.ofHours(2))
                .strongConsistency(true)
                .nullValueCache(true)
                .nullValueTtl(Duration.ofMinutes(3))
                .redisKeyPrefix("myapp:cache:")
                .build();
        return cacheManager.getOrCreate(config);
    }
}
```

```java
User user = userCache.getOrLoad("user:1001", key -> userMapper.selectById(1001L));
userCache.put("user:1001", user, Duration.ofMinutes(20));
userCache.evict("user:1001");
CacheStats stats = userCache.stats();
```

## 读取流程

```text
getOrLoad(key)
  1. 尝试读取 L1。
  2. 强一致开启时，L1 命中前会校验 Redis 中的缓存区域版本。
  3. L1 未命中或版本过期时读取 Redis L2。
  4. L2 命中后回填 L1，L1 TTL 不超过 Redis 剩余 TTL。
  5. L1/L2 都未命中时，同一个 key 在当前 JVM 内只允许一个线程回源 loader。
  6. loader 返回值写入 L2 和 L1；loader 返回 null 时按配置写入 null 哨兵。
```

## 一致性说明

框架同时使用两种机制减少多 JVM L1 旧值问题：

- Redis Pub/Sub 失效广播：某个 JVM 执行 `put`、`evict`、`evictAll` 后，会通知其他 JVM 清理对应 L1。
- Redis 版本校验：强一致模式下，每个缓存区域维护一个 Redis 版本号，L1 条目只有在本地版本和 Redis 当前版本一致时才会返回。

生产建议：

- 多实例部署并且缓存数据会被多个实例写入时，建议保持 `strong-consistency=true`。
- Pub/Sub 是瞬时消息，实例离线期间可能错过失效广播；关键缓存不要只依赖广播，应开启强一致版本校验。
- 对极致性能敏感、且短时间旧值可以接受的缓存，可以将单个实例的 `strong-consistency` 设置为 `false`。

## Redis 降级和恢复

当 Redis 访问异常时，缓存实例会进入 L2 降级状态：

- 后续读写会跳过 Redis，避免每个请求都阻塞在 Redis 异常上。
- 已有 L1 数据仍可命中。
- 未命中时会继续执行 loader 回源。
- `CacheRecoveryScheduler` 会按 `recovery-interval` 定期尝试恢复 L2。

生产建议：

- 降级期间命中率可能下降，要关注 `l2DegradedCount` 和业务回源压力。
- 如果缓存回源会打到数据库，请确保数据库侧有保护措施，例如限流、超时和熔断。

## Set 缓存

Set 缓存适合“一个 key 对应多个成员”的索引场景，例如：

- 规则 code 到规则 ID 集合。
- 标签到实体 ID 集合。
- 分组到用户 ID 集合。

示例：

```java
CacheConfig<String, Long> config = CacheConfig.<String, Long>builder("ruleIndex")
        .l1Ttl(Duration.ofMinutes(10))
        .l2Ttl(Duration.ofHours(1))
        .build();

MultiLevelSetCache<String, Long> ruleIndex = cacheManager.getOrCreateSetCache(config);
ruleIndex.add("product:loan", 1001L);
Set<Long> ruleIds = ruleIndex.members("product:loan");
ruleIndex.remove("product:loan", 1001L);
```

如果成员不是 `Long`，请使用显式成员类型的重载，避免 Redis 读取后按默认 Long 转换。

## 监控

`CacheMonitor` 可以输出所有 KV 缓存实例的统计摘要：

```java
cacheMonitor.logStats();
```

日志示例：

```text
Cache [userCache] L1HitRate=92.50% L2HitRate=5.10% TotalRequests=20000 Loads=480 Evictions=35
```

也可以直接读取快照：

```java
Map<String, CacheStats> snapshot = cacheMonitor.snapshot();
```

## 配置项

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `letool.cache.enabled` | `true` | 是否启用缓存 starter |
| `letool.cache.redis-prefix` | `letool:cache:` | 全局 Redis key 前缀 |
| `letool.cache.l1-enabled` | `true` | 全局 L1 开关 |
| `letool.cache.l2-enabled` | `true` | 全局 L2 开关 |
| `letool.cache.strong-consistency` | `true` | 全局强一致版本校验开关 |
| `letool.cache.instances[].name` | 无 | 缓存实例名称 |
| `letool.cache.instances[].l1-enabled` | `true` | 当前实例 L1 开关 |
| `letool.cache.instances[].l1-max-size` | `2000` | 当前实例 L1 最大条目数 |
| `letool.cache.instances[].l1-ttl` | `24h` | 当前实例 L1 TTL |
| `letool.cache.instances[].l2-enabled` | `true` | 当前实例 L2 开关 |
| `letool.cache.instances[].l2-ttl` | `3d` | 当前实例 L2 TTL |
| `letool.cache.instances[].strong-consistency` | `true` | 当前实例强一致开关 |
| `letool.cache.instances[].null-value-cache` | `true` | 是否缓存 null 结果 |
| `letool.cache.instances[].null-value-ttl` | `5m` | null 哨兵 TTL |
| `letool.cache.invalidation.enabled` | `true` | 是否启用跨 JVM L1 失效广播 |
| `letool.cache.invalidation.channel` | `letool:cache:invalidation` | Redis Pub/Sub 频道 |
| `letool.cache.degradation.recovery-enabled` | `true` | 是否启用 L2 恢复探测 |
| `letool.cache.degradation.recovery-interval` | `30s` | L2 恢复探测间隔 |
| `letool.cache.annotation.enabled` | `true` | 是否启用缓存注解切面 |
| `letool.cache.monitoring.enabled` | `true` | 是否注册 CacheMonitor |

## 和常见开源方案的差异

| 方案 | 主要特点 | 和 letool-starter-cache 的差异 |
| --- | --- | --- |
| Spring Cache | 标准抽象，生态好 | Spring Cache 本身不提供完整的 L1+L2、一致性版本校验、Redis 降级恢复 |
| Caffeine | 极强的本地缓存 | Caffeine 不是分布式缓存，不解决跨 JVM 共享和失效 |
| Redisson | 分布式对象能力丰富 | Redisson 更重，能力范围更大；本 starter 更聚焦二级缓存和 letool 体系接入 |
| JetCache | 成熟二级缓存框架 | JetCache 社区成熟度更高；本 starter 代码更轻、更可控，更容易按内部业务演进 |

## 生产接入清单

接入其他项目之前，建议逐项确认：

- 缓存名称具有业务语义，避免多个业务复用同一个 cache name。
- L2 TTL 大于或等于 L1 TTL。
- 写多读少、强实时数据不要盲目加缓存。
- 多实例写入同一缓存时开启强一致。
- Redis 异常时 loader 回源不会压垮数据库。
- null 缓存 TTL 不要过长，避免不存在的数据创建后仍然短期不可见。
- Redis key 前缀按应用隔离，例如 `edc:cache:`、`crm:cache:`。
- 生产日志或监控中关注命中率、加载失败、降级次数和回源量。
