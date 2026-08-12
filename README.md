# letool

[![CI](https://github.com/leylaragg/letool/actions/workflows/maven.yml/badge.svg)](https://github.com/leylaragg/letool/actions/workflows/maven.yml)
[![JDK](https://img.shields.io/badge/JDK-17%2B-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

企业级 Java 工具包 —— 提供缓存、日志、安全、加密、网络通信、规则引擎、支付集成等开箱即用的 Spring Boot 3.x Starter 组件。按需引入，零耦合。

## 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+（推荐 21） |
| Spring Boot | 3.5.x |
| Maven | 3.9+ |

## 工程化文档

| 文档 | 说明 |
|------|------|
| [CHANGELOG](CHANGELOG.md) | 版本变更记录 |
| [Version Compatibility](docs/version-compatibility.md) | Java、Spring Boot、模块兼容边界 |
| [BOM Usage](docs/bom-usage.md) | dependency management / BOM 引入方式 |
| [Module Production Readiness](docs/module-production-readiness.md) | 各模块生产就绪度与 stub/mock 边界 |
| [External Provider Boundaries](docs/external-provider-boundaries.md) | 外部服务 provider 的真实、mock、stub 边界 |
| [Starter Auto-Configuration Rules](docs/starter-auto-configuration-rules.md) | starter 自动装配治理规则 |
| [Starter Dependency Scope Audit](docs/starter-dependency-scope-audit.md) | starter 依赖作用域审计计划 |
| [Verification Guide](docs/verification-guide.md) | 定向测试、模块测试、全仓验证和交付检查规范 |

## 模块总览

| 模块 | 说明 | 依赖 |
|------|------|------|
| **letool-starter-exception** | 统一异常 —— 错误码、业务/系统异常、MessageSource 国际化解析 | 无 letool 内部依赖 |
| **letool-starter-tool** | 核心工具 —— 可替换 JsonCodec、HTTP、ID 生成、字符串、集合、树工具；Spring/Redis helper 为可选适配器 | exception |
| [**letool-starter-sensitive**](letool-starter-sensitive/README.md) | 数据脱敏工具 —— 常用策略、字段注解、可扩展注册表与 Jackson 自动脱敏 | exception, Jackson |
| **letool-starter-log** | 日志封装 —— 请求链路追踪、审计日志、方法日志；异步 MDC 传播可直接搭配 thread | tool, sensitive |
| **letool-starter-cache** | 二级缓存 —— KV 与 Redis 原生 List/Hash/Set/ZSet，自动降级与恢复 | tool, exception |
| [**letool-starter-cipher-suite**](letool-starter-cipher-suite/README.md) | 加密工具 —— AES/SM4-GCM、RSA-OAEP/PSS、SM2/SM3、HMAC 与安全密钥生成 | exception, Bouncy Castle |
| **letool-starter-web** | Web 增强 —— 全局异常处理、响应包装、XSS/SQL 注入防御 | tool, exception |
| **letool-starter-security** | 安全认证 —— Resource Server、JWT 签发、角色与权限映射 | tool, exception |
| **letool-starter-thread** | 线程管理 —— 动态线程池、MDC 与自定义上下文传播、虚拟线程 | exception |
| **letool-starter-swagger** | API 文档 —— Springdoc OpenAPI 引擎、Knife4j 增强 UI 与开箱即用默认配置 | exception, Springdoc, Knife4j, Spring Boot Web |
| [**letool-starter-file**](letool-starter-file/README.md) | 文件操作便利门面 —— Local/FTP/FTPS 流式存储、进度、单 Range、断点续传与 ZIP 安全处理 | exception |
| **letool-starter-excel** | Excel 操作 —— EasyExcel 原生能力薄封装、批量读取与轻量校验 | exception |
| **letool-starter-mail** | 邮件发送 —— 显式启用的 Jakarta Mail、多账户、附件与同步/异步投递 | exception |
| **letool-starter-distributed-lock** | 分布式锁 —— Redis 后端、`LockTemplate`、`@Lock`/`@Idempotent`；可替换 `DistributedLock` | tool |
| **letool-starter-rule** | 规则执行 —— LiteFlow 原生能力薄封装、便捷执行与统一异常 | exception |
| **letool-starter-net** | 网络通信 —— Netty TCP、短连接/持久连接/有界连接池、可扩展分帧和载荷编解码 | exception |
| **letool-starter-pay / pay-alipay / pay-wechat** | 支付统一契约与官方 SDK Provider —— 支持下单、查询、关单、退款及强制回调验签 | exception、支付宝/微信官方 SDK |
| [**letool-starter-mq / mq-rabbit / mq-kafka / mq-rocketmq**](letool-starter-mq/README.md) | 消息队列便利门面 —— 不可变消息、Provider 路由与统一异常；RabbitMQ、Kafka、RocketMQ 由独立成熟 Binder Starter 按需提供 | exception、Spring Cloud Stream；按需选择一个 Provider 模块 |
| **letool-starter-ratelimiter** | 流量保护 —— Sentinel Core 薄封装、命名策略与热点参数限流 | tool, exception |
| [**letool-starter-oss**](letool-starter-oss/README.md) | 对象存储便利门面 —— 统一 API、默认 Bucket 与异常；MinIO、阿里云 OSS、腾讯云 COS 由独立官方 SDK Provider starter 提供 | exception；按需选择一个 Provider 模块 |
| **letool-starter-sms** | 短信公共契约、Provider 路由、结构化批量结果和可替换限流 | exception、Caffeine |
| **letool-starter-sms-aliyun** | 阿里云短信 V2 官方 SDK Provider，自动传递短信核心模块 | sms、tool、阿里云 SDK |
| **letool-starter-sms-tencent** | 腾讯云 SMS 3.0 产品级官方 SDK Provider，自动传递短信核心模块 | sms、腾讯云 SDK |
| [**letool-starter-ai**](letool-starter-ai/README.md) | AI 便利门面 —— Spring AI Provider 中立模型路由、默认选择与原生 `ChatClient` 缓存 | exception, Spring AI |
| **letool-starter-data-structure** | 数据结构 —— 泛型树、决策链（消除 if-else）、链表 | tool |
| [**letool-starter-websocket**](letool-starter-websocket/README.md) | 单节点 WebSocket 开发框架 —— 握手鉴权、消息路由、会话限流、房间管理、可靠发送与心跳清理 | exception, tool, Spring WebSocket |
| **letool-starter-job** | 任务调度 —— Spring Boot Quartz 便捷封装、分片、重试、日志扩展 | exception, Spring Boot Quartz |
| **letool-starter-monitor** | 监控增强 —— Actuator/Micrometer 指标门面、真实 Webhook 告警、用户清理任务生产级调度 | tool, exception |

### 数据访问技术选择

Letool 不再提供 `letool-starter-data`。数据库访问请根据项目模型直接选择
MyBatis-Plus、Spring Data JDBC/JPA，或 Spring Framework 原生
`JdbcClient` / `JdbcTemplate`。Letool 不在这些成熟方案之上维护额外的实体注解、
Lambda 查询 DSL、分页模型和数据库方言。

### 网络通信能力边界

`letool-starter-net` 当前提供基于 Netty 的生产级 TCP 客户端封装，支持短连接、
持久连接、有界固定连接池、长度字段/分隔符/定长分帧、自定义载荷编解码、应用层心跳
应答检测，以及受单一请求绝对期限约束的写出前有界建连重试。未提供请求关联标识时，
每条连接严格保持单请求独占，不会假设任意私有协议天然支持多路复用。

通用 HTTP 请求可以使用 `letool-starter-tool` 中基于 JDK `HttpClient` 的 `HttpUtil`；服务网关、
负载均衡和熔断不在 TCP 模块中重复实现，应直接选择 Spring Cloud Gateway、Spring Cloud
LoadBalancer、Sentinel 或 Resilience4j。具体用法和限制见
[`letool-starter-net/README.md`](letool-starter-net/README.md)。

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-tool</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

### 2. 使用工具

```java
// 统一响应体
@GetMapping("/user/{id}")
public R<User> getUser(@PathVariable Long id) {
    return R.ok(userService.getById(id));
}

// JSON 工具
String json = JsonUtil.toJsonString(user);
User user = JsonUtil.parseObject(json, User.class);

// 自定义策略可显式传入，Spring 应用也可替换 JsonCodec Bean
JsonCodec codec = Fastjson2JsonCodec.builder()
    .writerFeatures(JSONWriter.Feature.WriteNulls)
    .build();
String customJson = JsonUtil.toJsonString(user, codec);

// ID 生成器
long id = IdUtil.nextId();       // 雪花算法
String uuid = IdUtil.simpleUUID();

// 字符串工具
String result = StrUtil.format("Hello, {}!", "World");
String camel = StrUtil.toCamelCase("user_name");

// 树构建
List<Dept> tree = TreeBuilder.build(flatList);
```

### 3. 数据脱敏

```java
public class User {
    @Sensitive(type = SensitiveType.PHONE)
    private String phone;    // "138****5678"

    @Sensitive(type = SensitiveType.ID_CARD)
    private String idCard;   // "3201**********1234"
}

// Controller 返回 JSON 时自动脱敏注解字段
@GetMapping("/user")
public R<User> getUser() {
    return R.ok(user);  // phone 和 idCard 自动脱敏
}
```

### 4. JWT 安全认证

```java
// 登录路径通过 letool.security.exclude-paths 显式公开
@PostMapping("/auth/login")
public R<Map<String, String>> login(@RequestBody LoginRequest req) {
    LoginUser user = new LoginUser(1L, req.getUsername(),
            List.of("ADMIN"), List.of("user:read", "user:write"));
    String accessToken = jwtTokenProvider.generateAccessToken(user);
    String refreshToken = jwtTokenProvider.generateRefreshToken(user);
    return R.ok(Map.of(
            "accessToken", accessToken,
            "refreshToken", refreshToken
    ));
}

// 权限控制
@RequireRole("ADMIN")
@DeleteMapping("/user/{id}")
public R<Void> deleteUser(@PathVariable Long id) { ... }

// 获取当前用户
LoginUser currentUser = SecurityUtil.getCurrentUser();
```

### 5. 加解密

```java
// AES-GCM 认证加密；租户标识作为附加认证数据
String key = CipherUtil.generateAesKey(256);
String enc = CipherUtil.aesEncrypt("Hello", key, "tenant-1001");
String dec = CipherUtil.aesDecrypt(enc, key, "tenant-1001");

// 国密 SM2
Sm2Util.Sm2KeyPair pair = CipherUtil.generateSm2KeyPair();
String sm2Enc = CipherUtil.sm2Encrypt("data", pair.getPublicKey());

// 摘要与消息认证
String sha256 = CipherUtil.sha256("hello");
String sm3 = CipherUtil.sm3("hello");
String hmacKey = CipherUtil.generateHmacKey();
String mac = CipherUtil.hmacSha256("payload", hmacKey);
boolean valid = CipherUtil.verifyHmacSha256("payload", mac, hmacKey);
```

### 6. 二级缓存

```java
// 注册缓存
MultiLevelCache<String, User> cache = cacheManager.getOrCreate(
    CacheConfig.<String, User>builder("userCache")
        .l1MaxSize(100).l1Ttl(Duration.ofHours(1))
        .l2Ttl(Duration.ofHours(12)).build());

// 读穿
User user = cache.getOrLoad("user:123", key -> userMapper.selectById(123));
```

集合索引、列表、字段映射和排行榜可分别使用
`MultiLevelSetCache`、`MultiLevelListCache`、`MultiLevelHashCache`、`MultiLevelZSetCache`。
集合缓存只在拿到完整 Redis 结果时建立 L1 快照，Redis 恢复后会清理降级期间的本地副本。

### 7. 决策链（消除 if-else）

```java
DecisionChain<Integer, String> chain = DecisionChain.<Integer, String>builder()
    .when(a -> a > 50000, a -> "风控审核")
    .when(a -> a > 10000, a -> "主管审批")
    .when(a -> a > 1000,  a -> "经理审批")
    .otherwise(a -> "自动通过")
    .build();

String result = chain.execute(amount);
```

决策链按注册顺序执行首个命中的规则。建议业务链显式配置 `otherwise`；如果没有任何规则
命中且未配置 `otherwise`，`execute` 会抛出 `IllegalStateException`。

## 全局配置

```yaml
letool:
  tool:
    redis:
        # Redis 多态 value 的反序列化白名单；填写尽可能精确的业务包名
        auto-type-accept-prefixes:
          - org.springframework
          - com.example
  exception:
    enabled: true
    i18n:
      enabled: true
      default-locale: zh_CN
      fallback-to-system-locale: false
  sensitive:
    enabled: true
    jackson:
      enabled: true
  log:
    enabled: true
    trace:
      enabled: true
      header-name: X-Trace-Id
    web-log:
      enabled: true
    audit:
      enabled: true
      # 默认输出到 letool.audit Logger；数据库持久化通过自定义 AuditLogService 接入
  cache:
    enabled: true
    annotation:
      enabled: true
    redis-prefix: "letool:"
    instances:
      - name: userCache
        l1-max-size: 2000
        l1-ttl: 24h
        l2-ttl: 72h
  thread:
    enabled: true
    context-propagation:
      mdc: true
  rate-limiter:
    enabled: true
    default-policy: default
    policies:
      default:
        threshold: 10
    annotation:
      enabled: true
  security:
    enabled: true
    auth-mode: jwt
    jwt:
      # 必填，UTF-8 长度至少 32 字节
      secret: "${JWT_SECRET}"
      access-token-expiration: 1800
      refresh-token-expiration: 604800
      issuer: my-application
    exclude-paths:
      - /api/public/**
      - /api/auth/**
```

## 示例项目

完整示例代码见 [letool-sample](letool-sample/) 模块，包含 5 个演示控制器：

| Controller | 演示内容 |
|------------|---------|
| `ToolController` | R 响应体、JsonUtil、StrUtil、IdUtil |
| `SensitiveController` | @Sensitive 数据脱敏 |
| `CipherController` | AES / SM2 加解密、哈希 |
| `AuthController` | JWT 登录、Resource Server、@RequireRole |
| `DataStructureController` | TreeBuilder 树构建、DecisionChain 决策链 |

启动方式：

```bash
cd letool-sample
mvn spring-boot:run
```

访问示例：

```bash
# 公开接口
curl http://localhost:8080/api/public/tool/id
# 脱敏效果
curl http://localhost:8080/api/public/sensitive/user
# 登录
curl -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}'
# 管理接口（需 Bearer token）
curl http://localhost:8080/api/admin/dashboard -H 'Authorization: Bearer <token>'
```

## License

MIT License
