# letool-starter-security

## 模块简介

`letool-starter-security` 是 Spring Security 6.x 的 JWT Resource Server 薄封装：

- Spring Security OAuth2 Resource Server 负责 Bearer Token 提取、JWT 解码、时间/签发者校验和过滤链异常处理；
- JJWT 只负责应用登录接口需要的 AccessToken / RefreshToken 签发与刷新令牌解析；
- JWT 中的角色和权限会映射为 Spring Security authorities，同时保留 `LoginUser` / `SecurityUtil` 便捷 API；
- `JwtDecoder`、`SecurityFilterChain`、异常处理器和 JWT 认证转换器均可由业务替换。

模块不实现用户名密码校验、用户存储、Redis Token 吊销或 Session 认证。登录流程和刷新流程仍由业务系统负责。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-security</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始

### 1. 配置 JWT

```yaml
letool:
  security:
    enabled: true
    auth-mode: JWT
    jwt:
      # 必填，UTF-8 长度至少 32 字节；不要在仓库中提交生产密钥
      secret: ${JWT_SECRET}
      access-token-expiration: 1800
      refresh-token-expiration: 604800
      issuer: my-application
    exclude-paths:
      - /api/auth/login
      - /api/auth/refresh
      - /public/**
    cors:
      enabled: true
      allowed-origins: https://console.example.com
      allowed-methods: GET,POST,PUT,DELETE,OPTIONS
      allowed-headers: Authorization,Content-Type
      allow-credentials: true
      max-age: 3600
```

使用默认 HMAC 解码器或本地令牌签发器时，JWT 密钥为空、仍使用历史默认密钥、长度不足 32 字节，或签发者/有效期配置非法，应用会在启动阶段以 `SECURITY_001` 失败。`allow-credentials=true` 时不允许使用 `*` 来源。

### 2. 业务登录接口签发 Token

```java
LoginUser user = new LoginUser(
        userId,
        username,
        List.of("USER", "ADMIN"),
        List.of("user:read", "user:write")
);

String accessToken = jwtTokenProvider.generateAccessToken(user);
String refreshToken = jwtTokenProvider.generateRefreshToken(user);
```

角色使用不带 `ROLE_` 的业务名称。Resource Server 会把 `ADMIN` 映射为 `ROLE_ADMIN`，权限 `user:read` 则保持原值。

AccessToken 携带 `token_type=access`，RefreshToken 携带 `token_type=refresh`。Resource Server 只接受 AccessToken，RefreshToken 只能由业务刷新接口调用：

```java
LoginUser user = jwtTokenProvider.parseRefreshToken(refreshToken);
if (user == null) {
    // 刷新令牌无效、过期、签发者不匹配或类型错误
    throw new InvalidRefreshTokenException();
}
String newAccessToken = jwtTokenProvider.generateAccessToken(user);
```

### 3. 调用受保护资源

```http
GET /api/user/me
Authorization: Bearer <access-token>
```

模块只接受标准 `Authorization: Bearer` 请求头，不再从 URL 查询参数读取 Token，避免 Token 进入访问日志、浏览器历史或 Referrer。

## 权限控制

### Spring Security 原生注解

推荐优先使用原生 `@PreAuthorize`：

```java
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(Long userId) {
}

@PreAuthorize("hasAuthority('order:export')")
public void exportOrders() {
}
```

### Letool 便捷注解

对于简单的“满足任一角色/权限”场景，可继续使用：

```java
@RequireRole({"ADMIN", "OPERATOR"})
public void queryAuditLog() {
}

@RequirePermission({"order:export", "order:admin"})
public void exportOrders() {
}
```

`@RequireRole` / `@RequirePermission` 是轻量便捷 API。复杂表达式、参数级鉴权和返回值鉴权应使用 Spring Security 原生方法安全能力。

## 公开路径

控制器方法执行前，Servlet 安全过滤链尚未进入 HandlerMethod，因此模块不再提供实际无法可靠影响过滤链的 `@SkipAuth`。

公开接口应显式配置：

```yaml
letool:
  security:
    exclude-paths:
      - /api/auth/**
      - /public/**
      - /actuator/health
```

也可以由业务声明自己的 `SecurityFilterChain` 完整接管授权规则。模块不会额外硬编码登录、Swagger 或 Actuator 放行路径。

## SecurityUtil

Resource Server 会将 JWT 转换为以 `LoginUser` 为 Principal 的认证对象：

```java
LoginUser user = SecurityUtil.getCurrentUser();
Long userId = SecurityUtil.getCurrentUserId();
String username = SecurityUtil.getCurrentUsername();

boolean admin = SecurityUtil.hasRole("ADMIN");
boolean writable = SecurityUtil.hasPermission("user:write");
List<String> roles = SecurityUtil.getCurrentRoles();
List<String> permissions = SecurityUtil.getCurrentPermissions();
```

`LoginUser` 对角色和权限采用不可变快照，外部集合后续变化不会污染安全上下文。

## 扩展点

| 扩展点 | 用途 |
|---|---|
| `JwtDecoder` | 接入外部授权服务器、公私钥、JWK Set、密钥轮换或自定义校验 |
| `SecurityFilterChain` | 完整接管 URL 授权、CSRF、CORS 和 Resource Server 配置 |
| `SecurityExceptionHandler` | 自定义 401 响应 |
| `AccessDeniedExceptionHandler` | 自定义 403 响应 |
| `letoolJwtAuthenticationConverter` | 自定义 JWT Claims、Principal 和 authorities 映射 |
| `JwtTokenProvider` | 替换本地令牌签发逻辑 |

默认解码器使用 HMAC-SHA256。需要 RSA、EC、JWK Set、多租户签发者或远程授权服务器时，应提供自己的 `JwtDecoder`，不需要修改 Letool 内部实现。

仅作为外部授权服务器的资源端时，可以只注册 `JwtDecoder` 而不配置
`letool.security.jwt.secret`。此时模块不会创建本地 `JwtTokenProvider`；
如果业务仍需本地签发令牌，应同时显式提供自己的 `JwtTokenProvider`。

## 错误契约

| 错误码 | 含义 |
|---|---|
| `SECURITY_001` | JWT、公开路径或 CORS 配置不合法 |
| `SECURITY_002` | 未携带有效 AccessToken |
| `SECURITY_003` | 已认证但权限不足 |

401 和 403 响应仍使用 Letool `R` 结构。响应不会包含 Token、密钥、用户 ID 或底层 JWT 异常详情。

## 破坏性调整

- `JwtAuthenticationFilter` 已删除，Bearer Token 认证改由 Spring Security Resource Server 负责；
- `@SkipAuth` 已删除，公开路径改用 `exclude-paths` 或业务 `SecurityFilterChain`；
- 未实现的 `JWT_REDIS`、`SESSION` 枚举值已删除；
- JWT 密钥从不安全默认值改为必须显式配置；
- RefreshToken 不再能通过 AccessToken 验证；
- 查询参数 `token` 不再被接受；
- 401/403 错误码从 `AUTH_001` / `AUTH_002` 迁移为 `SECURITY_002` / `SECURITY_003`。
