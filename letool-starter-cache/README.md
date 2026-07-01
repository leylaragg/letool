# letool-starter-cache

## 模块简介

`letool-starter-cache` 是企业级二级缓存框架，L1 层基于 **Caffeine** 本地缓存（极低延迟），L2 层基于 **Redis** 分布式缓存（跨节点共享），实现**读穿 (Read-Through)** 和**写穿 (Write-Through)** 策略。支持注解声明式和编程式两种使用方式，内置 L2 降级、null 值防穿透、命中率监控等生产级特性。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-cache</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-cache</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

### 2. 注解方式：缓存查询结果

```java
@MultiLevelCacheable(name = "userCache", key = "#userId", ttl = 3600)
public User getUser(Long userId) {
    return userMapper.selectById(userId);  // 缓存未命中时才查数据库
}
```

### 3. 注解方式：更新时清除缓存

```java
@MultiLevelCacheEvict(name = "userCache", key = "#user.id")
public void updateUser(User user) {
    userMapper.updateById(user);
}
```

## 核心 API 示例

### 1. 注解声明式

**读取缓存：@MultiLevelCacheable**

```java
// 基础用法：30 分钟过期
@MultiLevelCacheable(name = "userCache", key = "#userId", ttl = 1800)
public User getUser(Long userId) { ... }

// 使用默认 TTL（不指定 ttl）
@MultiLevelCacheable(name = "productCache", key = "#productId")
public Product getProduct(Long productId) { ... }

// SpEL 表达式引用嵌套属性
@MultiLevelCacheable(name = "orderCache", key = "#request.orderNo")
public Order getOrder(OrderQueryRequest request) { ... }
```

**清除缓存：@MultiLevelCacheEvict**

```java
@MultiLevelCacheEvict(name = "userCache", key = "#userId")
public void deleteUser(Long userId) { ... }

@MultiLevelCacheEvict(name = "userCache", key = "#user.id")
public void updateUser(User user) { ... }
```

### 2. 编程式：CacheManager + MultiLevelCache

**注册缓存实例**

```java
@Configuration
public class CacheConfiguration {

    @Bean
    public MultiLevelCache<String, User> userCache(CacheManager cacheManager) {
        CacheConfig<String, User> config = CacheConfig.<String, User>builder("userCache")
            .l1MaxSize(2000)
            .l1Ttl(Duration.ofHours(24))
            .l2Ttl(Duration.ofDays(3))
            .nullValueCache(true)
            .nullValueTtl(Duration.ofMinutes(5))
            .redisKeyPrefix("letool:cache:")
            .build();
        return cacheManager.getOrCreate(config);
    }
}
```

**使用缓存**

```java
@Autowired
@Qualifier("userCache")
private MultiLevelCache<String, User> userCache;

// 读穿：缓存有则返回，无则查库并回填
User user = userCache.getOrLoad("user:123", id -> userMapper.selectById(id));

// 写入缓存（默认 TTL）
userCache.put("user:123", user);

// 写入缓存（自定义 TTL）
userCache.put("user:123", user, Duration.ofMinutes(30));

// 删除缓存
userCache.evict("user:123");

// 获取统计
CacheStats stats = userCache.stats();
System.out.printf("L1命中率: %.2f%%, L2命中率: %.2f%%",
    stats.getL1HitRate() * 100, stats.getL2HitRate() * 100);
```

### 3. CacheConfig 配置详解

```java
CacheConfig<String, User> config = CacheConfig.<String, User>builder("userCache")
    .l1MaxSize(2000)              // L1 Caffeine 最大容量，默认 2000
    .l1Ttl(Duration.ofHours(24))  // L1 过期时间，默认 24 小时
    .l2Ttl(Duration.ofDays(3))    // L2 Redis 过期时间，默认 3 天
    .nullValueCache(true)         // 是否缓存 null 值（防穿透），默认 true
    .nullValueTtl(Duration.ofMinutes(5)) // null 值过期时间，默认 5 分钟
    .redisKeyPrefix("letool:cache:")     // Redis key 前缀
    .build();
```

### 4. CacheManager 管理 API

```java
@Autowired
private CacheManager cacheManager;

// 获取或创建缓存实例
MultiLevelCache<String, Order> cache = cacheManager.getOrCreate(config);

// 获取已注册的缓存实例（不存在抛 CacheException）
MultiLevelCache<String, Order> cache = cacheManager.get("orderCache");

// 获取所有缓存实例
Collection<MultiLevelCache<?, ?>> all = cacheManager.getAll();

// 移除缓存实例
cacheManager.remove("orderCache");
```

### 5. 缓存统计 CacheStats

```java
CacheStats stats = userCache.stats();

long l1Hits = stats.getL1HitCount();        // L1 命中次数
long l2Hits = stats.getL2HitCount();        // L2 命中次数
long misses = stats.getMissCount();          // 未命中次数
long loads = stats.getLoadCount();           // 加载次数
long loadFailures = stats.getLoadFailureCount(); // 加载失败次数
long evictions = stats.getEvictionCount();   // 淘汰次数

double l1Rate = stats.getL1HitRate();        // L1 命中率
double l2Rate = stats.getL2HitRate();        // L2 命中率
double totalRate = stats.getTotalHitRate();  // 总命中率
```

### 6. 缓存数据流（读穿策略）

```
请求 → L1(Caffeine) → 命中? → 返回
                        ↓ 未命中
                     L2(Redis) → 命中? → 回填 L1 → 返回
                                    ↓ 未命中
                                 Loader(查DB) → 回填 L2 → 回填 L1 → 返回
```

## 配置属性

```yaml
letool:
  cache:
    enabled: true                       # 总开关
    redis-prefix: "letool:cache:"       # Redis key 全局前缀
    instances:                          # 缓存实例列表（YAML 配置方式）
      - name: userCache
        l1-max-size: 2000
        l1-ttl: 24h
        l2-ttl: 3d
        null-value-cache: true
        null-value-ttl: 5m
      - name: productCache
        l1-max-size: 5000
        l1-ttl: 1h
        l2-ttl: 7d
    degradation:                        # L2 降级配置
      recovery-interval: 30s            # 健康检查间隔
      max-retry-count: 3                # 最大重试次数
    monitoring:
      enabled: true                     # 监控开关
```

> 缓存实例可通过 YAML 声明式配置（`letool.cache.instances`），也可通过编程式 `CacheManager.getOrCreate(config)` 动态注册。
