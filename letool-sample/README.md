# letool-sample

letool 示例项目，演示所有模块的集成使用方式。开箱即用，适合快速了解各模块的核心能力。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-sample</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始

**1. 启动应用：**

```bash
mvnw spring-boot:run -pl letool-sample
```

**2. 访问示例接口：**

```bash
# 安全模块 - 登录获取 Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 携带 Token 访问受保护接口
curl http://localhost:8080/api/auth/me \
  -H "Authorization: Bearer <token>"

# 加密解密
curl -X POST http://localhost:8080/api/cipher/encrypt \
  -H "Content-Type: application/json" \
  -d '{"plainText":"Hello World"}'

# 脱敏演示
curl -X POST http://localhost:8080/api/sensitive/test \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138000","idCard":"110101199001011234","email":"test@example.com"}'

# 数据结构 - 构建树
curl http://localhost:8080/api/datastructure/tree

# 数据结构 - 决策链
curl http://localhost:8080/api/datastructure/chain
```

## 项目结构

```
letool-sample/src/main/java/com/github/leyland/letool/sample/
├── SampleApplication.java          # 启动类
├── config/
│   └── SampleCacheConfig.java      # 缓存配置示例
├── controller/
│   ├── AuthController.java         # 安全认证接口
│   ├── CipherController.java       # 加密解密接口
│   ├── DataStructureController.java # 数据结构演示
│   ├── SensitiveController.java    # 敏感数据脱敏
│   └── ToolController.java         # 工具类演示
├── entity/
│   ├── TreeNode.java               # 树节点实体
│   └── User.java                   # 用户实体
└── model/
    └── LoginRequest.java           # 登录请求模型
```

## 集成的模块

| 模块 | 说明 | 演示内容 |
|------|------|----------|
| letool-starter-security | 安全认证 | JWT 登录、Token 刷新、权限校验 |
| letool-starter-sensitive | 敏感数据脱敏 | Jackson 序列化脱敏、日志脱敏 |
| letool-starter-cache | 多级缓存 | Caffeine + Redis 双级缓存配置 |
| letool-starter-cipher | 加解密 | AES/GCM 加密解密 |
| letool-starter-log | 全链路追踪 | TraceId 生成与传递 |
| letool-starter-data-structure | 数据结构 | 泛型树构建、决策链、链表 |

## 配置概览（application.yml）

```yaml
spring:
  application:
    name: letool-sample
  autoconfigure:
    exclude:
      - RedisAutoConfiguration      # 示例未依赖 Redis
      - DataSourceAutoConfiguration  # 示例未依赖数据库

letool:
  security:
    enabled: true
    auth-mode: jwt
    jwt:
      secret: sample-jwt-secret-key-for-demo-only-256bits!!
      access-token-expiration: 3600
      refresh-token-expiration: 86400
  sensitive:
    enabled: true
    jackson:
      enabled: true
    log:
      enabled: true
  cache:
    enabled: true
    redis-prefix: "letool:sample:"
    instances:
      - name: userCache
        l1-max-size: 100
        l1-ttl: 1h
        l2-ttl: 12h
  cipher:
    provider: software
    aes:
      default-mode: GCM
      default-key-size: 256
  log:
    trace:
      enabled: true
      header-name: X-Trace-Id
      generate-if-absent: true
```

## 注意事项

- 示例项目排除了 `RedisAutoConfiguration` 和 `DataSourceAutoConfiguration`，因此缓存模块仅使用 Caffeine L1 缓存（无法演示 L2 Redis 层）
- 安全模块使用固定用户名密码（admin / admin123），仅供演示
- JWT 密钥为示例用途，生产环境请务必更换为安全的随机密钥
