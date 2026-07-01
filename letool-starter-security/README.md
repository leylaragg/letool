# letool-starter-security

## 模块简介

`letool-starter-security` 是企业级安全认证模块，基于 **Spring Security 6.x** 封装，提供 **JWT 无状态认证**、**注解式权限控制**、**编程式用户信息获取**和**跨域 CORS 配置**。通过 `@RequirePermission` / `@RequireRole` 注解实现方法级权限控制，通过 `SecurityUtil` 提供编程式用户信息和权限判断。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-security</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-security</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

### 2. 配置 JWT 密钥

```yaml
letool:
  security:
    jwt:
      secret: ${JWT_SECRET:my-production-secret-key}
```

### 3. 注解式权限控制

```java
@RestController
@RequestMapping("/admin")
public class AdminController {

    @RequireRole("ADMIN")
    @DeleteMapping("/user/{id}")
    public R<Void> deleteUser(@PathVariable Long id) { ... }

    @RequirePermission("order:export")
    @GetMapping("/order/export")
    public void exportOrders(HttpServletResponse response) { ... }
}
```

### 4. 编程式获取当前用户

```java
LoginUser user = SecurityUtil.getCurrentUser();
Long userId = SecurityUtil.getCurrentUserId();
boolean isAdmin = SecurityUtil.hasRole("ADMIN");
```

## 核心 API 示例

### 1. 注解声明式：权限控制

**跳过认证：@SkipAuth**

```java
@SkipAuth
@GetMapping("/public/health")
public R<String> health() {
    return R.ok("UP");
}

// 类级注解：该类所有方法无需登录
@SkipAuth
@RestController
@RequestMapping("/public")
public class PublicController { ... }
```

**角色控制：@RequireRole**

```java
// 单一角色
@RequireRole("ADMIN")
@DeleteMapping("/user/{id}")
public R<Void> deleteUser(@PathVariable Long id) { ... }

// 多角色（满足任一即可）
@RequireRole({"ADMIN", "SUPER_ADMIN"})
@GetMapping("/admin/dashboard")
public R<Dashboard> dashboard() { ... }

// 类级注解 + 方法级覆盖
@RequireRole("ADMIN")
@RestController
@RequestMapping("/admin")
public class AdminController {

    @RequireRole({"ADMIN", "OPERATOR"})  // 方法级覆盖类级
    @GetMapping("/logs")
    public R<List<Log>> queryLogs() { ... }
}
```

**权限控制：@RequirePermission**

```java
// 单一权限
@RequirePermission("user:write")
@PostMapping("/user")
public R<User> createUser(@RequestBody User user) { ... }

// 多权限（满足任一即可）
@RequirePermission({"order:export", "order:admin"})
@GetMapping("/order/export")
public void exportOrders(HttpServletResponse response) { ... }
```

### 2. 编程式：SecurityUtil

```java
// 获取当前登录用户
LoginUser user = SecurityUtil.getCurrentUser();
// 未登录返回 null

// 获取用户 ID / 用户名
Long userId = SecurityUtil.getCurrentUserId();
String username = SecurityUtil.getCurrentUsername();

// 角色判断
boolean isAdmin = SecurityUtil.hasRole("ADMIN");
boolean hasAnyRole = SecurityUtil.hasAnyRole("ADMIN", "OPERATOR");
List<String> roles = SecurityUtil.getCurrentRoles();

// 权限判断
boolean canWrite = SecurityUtil.hasPermission("user:write");
boolean hasAnyPerm = SecurityUtil.hasAnyPermission("order:view", "order:export");
List<String> permissions = SecurityUtil.getCurrentPermissions();

// 是否已认证
boolean authenticated = SecurityUtil.isAuthenticated();
```

### 3. LoginUser 用户模型

```java
LoginUser user = SecurityUtil.getCurrentUser();

Long userId = user.getUserId();          // 用户 ID
String username = user.getUsername();    // 登录名
String nickname = user.getNickname();    // 显示名
List<String> roles = user.getRoles();    // 角色列表
List<String> permissions = user.getPermissions(); // 权限标识列表
Object extra = user.getExtra();          // 业务扩展数据

// 内置判断方法
user.hasRole("ADMIN");
user.hasPermission("user:write");

// 构造（通常在登录认证成功时）
LoginUser loginUser = new LoginUser(1L, "admin",
    List.of("ADMIN"), List.of("user:read", "user:write"));
loginUser.setNickname("管理员");
```

### 4. SecurityProperties 配置属性

```java
// 编程式读取配置
@Autowired
private SecurityProperties securityProperties;

AuthMode authMode = securityProperties.getAuthMode();           // JWT
String secret = securityProperties.getJwt().getSecret();
long accessExp = securityProperties.getJwt().getAccessTokenExpiration();   // 1800s
long refreshExp = securityProperties.getJwt().getRefreshTokenExpiration(); // 604800s
List<String> excludePaths = securityProperties.getExcludePaths();
```

## 配置属性

```yaml
letool:
  security:
    enabled: true                                     # 总开关
    auth-mode: JWT                                    # 认证模式（当前仅 JWT）
    jwt:
      secret: ${JWT_SECRET:letool-default-secret}     # 签名密钥（生产环境必须通过环境变量覆盖）
      access-token-expiration: 1800                   # AccessToken 有效期（秒），默认 30 分钟
      refresh-token-expiration: 604800                # RefreshToken 有效期（秒），默认 7 天
      issuer: letool                                  # JWT 签发者标识
    exclude-paths:                                    # 不经过安全过滤的路径
      - /public/**
      - /api/auth/login
      - /api/auth/register
      - /swagger-ui/**
      - /actuator/health
    cors:
      enabled: true                                   # 跨域支持开关
      allowed-origins: "*"                            # 允许的源
      allowed-methods: "GET,POST,PUT,DELETE,OPTIONS"  # 允许的 HTTP 方法
      allowed-headers: "*"                            # 允许的请求头
      max-age: 3600                                   # 预检请求缓存时间（秒）
```

### 排除路径说明

`exclude-paths` 支持 Ant 风格通配符：

```yaml
letool:
  security:
    exclude-paths:
      - /api/auth/**          # 所有认证接口
      - /public/**            # 所有公开接口
      - /actuator/health      # 健康检查
      - /static/**            # 静态资源
```

### JWT 认证流程

```
客户端                        服务端
  │                             │
  ├─ POST /api/auth/login ─────>│  (exclude-paths 放行)
  │                             ├─ 验证用户名密码
  │                             ├─ 生成 AccessToken + RefreshToken
  │<───── R.ok(tokenPair) ─────┤
  │                             │
  ├─ GET /api/user/123 ────────>│  (携带 Authorization: Bearer <token>)
  │  Authorization: Bearer xxx  ├─ JWT Filter 解析 token
  │                             ├─ 写入 SecurityContext
  │                             ├─ @RequirePermission 检查
  │<───── R.ok(user) ──────────┤
```
