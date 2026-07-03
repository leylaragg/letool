# letool

[![CI](https://github.com/leyland-wang/letool/actions/workflows/maven.yml/badge.svg)](https://github.com/leyland-wang/letool/actions/workflows/maven.yml)
[![JDK](https://img.shields.io/badge/JDK-17%2B-orange)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.x-brightgreen)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

企业级 Java 工具包 —— 提供缓存、日志、安全、加密、网络通信、规则引擎、支付集成等开箱即用的 Spring Boot 3.x Starter 组件。按需引入，零耦合。

## 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+（推荐 21） |
| Spring Boot | 3.4.x |
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

## 模块总览

| 模块 | 说明 | 依赖 |
|------|------|------|
| **letool-starter-tool** | 核心工具 —— JSON、HTTP、Redis、ID 生成、字符串、集合、树工具、i18n | 无 |
| **letool-starter-sensitive** | 数据脱敏 —— 注解驱动，Jackson 序列化 + 日志输出自动脱敏 | tool |
| **letool-starter-log** | 日志封装 —— 链路追踪、审计日志、方法日志、动态日志级别 | tool, sensitive |
| **letool-starter-cache** | 二级缓存 —— L1 Caffeine + L2 Redis，读穿/写穿、自动降级 | tool |
| **letool-starter-cipher-suite** | 加密套件 —— AES/RSA/SM2/SM3/SM4、数字签名 | tool |
| **letool-starter-web** | Web 增强 —— 全局异常处理、响应包装、XSS/SQL 注入防御 | tool, log, sensitive |
| **letool-starter-security** | 安全认证 —— JWT、注解权限、多种认证模式 | tool, web, cache, sensitive |
| **letool-starter-data** | 数据库封装 —— Lambda 查询、分页、注解映射、字段加密 | tool, cache, sensitive |
| **letool-starter-thread** | 线程管理 —— 动态线程池、上下文传递、虚拟线程 | tool, log |
| **letool-starter-swagger** | API 文档 —— Knife4j + SpringDoc，自动配置、离线导出 | web |
| **letool-starter-file** | 文件操作 —— 上传下载、FTP/SFTP/MinIO/OSS、魔数检测 | tool, web |
| **letool-starter-excel** | Excel 操作 —— EasyExcel 封装，链式 API、批量处理 | tool, file |
| **letool-starter-mail** | 邮件发送 —— 模板邮件、异步发送、多账户 | tool |
| **letool-starter-distributed-lock** | 分布式锁 —— Redis/ZK/DB 悲观锁、乐观锁、幂等性 | tool, cache |
| **letool-starter-rule** | 规则引擎 —— LiteFlow 封装、规则链编排、Groovy 脚本 | tool, data |
| **letool-starter-net** | 网络通信 —— Netty TCP、ISO8583、HTTP 负载均衡、网关路由 | tool |
| **letool-starter-pay** | 支付抽象 —— 支付宝/微信支付当前为 stub/mock 实现 | tool, web |
| **letool-starter-mq** | 消息队列 —— 当前内置内存队列，RabbitMQ/RocketMQ/Kafka 配置为后续扩展入口 | tool, log |
| **letool-starter-ratelimiter** | 限流熔断 —— 令牌桶/滑动窗口、熔断器 | tool, cache |
| **letool-starter-oss** | 对象存储抽象 —— 阿里云 OSS/腾讯云 COS/MinIO 当前为 stub 实现 | tool, file |
| **letool-starter-sms** | 短信通知抽象 —— 阿里云/腾讯云当前为模拟调用，支持频率限制 | tool |
| **letool-starter-ai** | AI 集成 —— OpenAI 兼容 HTTP 调用，已具备超时、重试、错误脱敏、流式输出和可替换 HTTP 传输层 | tool |
| **letool-starter-data-structure** | 数据结构 —— 泛型树、决策链（消除 if-else）、链表 | 无 |
| **letool-starter-websocket** | WebSocket —— 消息路由、房间管理、分布式会话 | tool, web |
| **letool-starter-job** | 任务调度 —— 分布式定时任务、分片、重试 | tool, thread |
| **letool-starter-monitor** | 监控指标 —— Micrometer + Prometheus 可用，告警/清理仍有占位实现 | tool, log, thread |

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

// Controller 返回 JSON 自动脱敏，日志输出自动脱敏
@GetMapping("/user")
public R<User> getUser() {
    return R.ok(user);  // phone/email/idCard 自动脱敏
}
```

### 4. JWT 安全认证

```java
// 登录
@SkipAuth
@PostMapping("/auth/login")
public R<Map<String, String>> login(@RequestBody LoginRequest req) {
    LoginUser user = new LoginUser(1L, req.getUsername(),
            List.of("ROLE_ADMIN"), List.of("user:read", "user:write"));
    String accessToken = jwtTokenProvider.generateAccessToken(user);
    return R.ok(Map.of("accessToken", accessToken));
}

// 权限控制
@RequireRole("ROLE_ADMIN")
@DeleteMapping("/user/{id}")
public R<Void> deleteUser(@PathVariable Long id) { ... }

// 获取当前用户
LoginUser currentUser = SecurityUtil.getCurrentUser();
```

### 5. 加解密

```java
// AES 对称加密
String key = CipherUtil.generateAesKey(256);
String enc = CipherUtil.aesEncrypt("Hello", key);
String dec = CipherUtil.aesDecrypt(enc, key);

// 国密 SM2
Sm2Util.Sm2KeyPair pair = CipherUtil.generateSm2KeyPair();
String sm2Enc = CipherUtil.sm2Encrypt("data", pair.getPublicKey());

// 哈希
String sha256 = CipherUtil.sha256("hello");
String sm3 = CipherUtil.sm3("hello");
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

## 全局配置

```yaml
letool:
  tool:
    i18n:
      enabled: true
      default-locale: zh_CN
  sensitive:
    enabled: true
    jackson:
      enabled: true
    log:
      enabled: true
  log:
    trace:
      enabled: true
      header-name: X-Trace-Id
  cache:
    enabled: true
    redis-prefix: "letool:"
    instances:
      - name: userCache
        l1-max-size: 2000
        l1-ttl: 24h
        l2-ttl: 72h
  security:
    enabled: true
    auth-mode: jwt
    jwt:
      secret: "${JWT_SECRET}"
      access-token-expiration: 30m
      refresh-token-expiration: 7d
    exclude-paths:
      - /api/public/**
      - /api/auth/**
  cipher:
    provider: software
    aes:
      default-mode: GCM
```

## 示例项目

完整示例代码见 [letool-sample](letool-sample/) 模块，包含 5 个演示控制器：

| Controller | 演示内容 |
|------------|---------|
| `ToolController` | R 响应体、JsonUtil、StrUtil、IdUtil |
| `SensitiveController` | @Sensitive 数据脱敏 |
| `CipherController` | AES / SM2 加解密、哈希 |
| `AuthController` | JWT 登录、@SkipAuth、@RequireRole |
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
