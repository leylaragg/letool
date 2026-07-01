# letool-starter-web

## 模块简介

`letool-starter-web` 是 Web 增强模块，基于 Spring Boot Web 提供**全局异常处理**、**响应统一包装**、**XSS 过滤**和**SQL 注入防御**四大能力。无需修改 Controller 代码，自动将返回值包装为 `R<T>` 格式，将异常统一转换为标准化错误响应，并拦截常见 Web 攻击。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-web</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-web</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

### 2. Controller 直接返回业务对象

```java
@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getById(id);
        // 自动包装为 R.ok(user) → {"code":"00000","message":"ok","data":{...}}
    }
}
```

### 3. 抛出异常自动处理

```java
@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    User user = userService.getById(id);
    if (user == null) {
        throw new BusinessException("USER_404", "用户不存在");
    }
    return user;
}
// 响应: {"code":"USER_404","message":"用户不存在","data":null}
```

## 核心 API 示例

### 1. 全局异常处理 GlobalExceptionHandler

**声明式：无需编码，自动生效**

引入模块后，`@RestControllerAdvice` 自动注册，所有异常统一转换为 `R<T>` 格式返回。

**异常映射表**

| 异常类型 | HTTP 状态码 | 响应示例 |
|---------|------------|---------|
| `BusinessException` | 400 | `{"code":"USER_001","message":"用户名已存在"}` |
| `IllegalArgumentException` | 400 | `{"code":"ARG_001","message":"参数不合法"}` |
| `MethodArgumentNotValidException` | 400 | `{"code":"VALID_001","message":"参数校验失败","data":{"phone":"手机号格式错误"}}` |
| `SystemException` | 500 | `{"code":"SYS_DB","message":"数据库连接超时"}` |
| `LetoolException` | 500 | `{"code":"E001","message":"配置加载失败"}` |
| `Exception`（兜底） | 500 | `{"code":"SYS_001","message":"系统内部错误，请稍后重试"}` |

**编程式：自定义异常处理**

如需扩展异常处理逻辑，继承或自定义 `@RestControllerAdvice`：

```java
@RestControllerAdvice
public class CustomExceptionHandler {

    @ExceptionHandler(MyCustomException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleCustom(MyCustomException e) {
        return R.fail(e.getCode(), e.getMessage());
    }
}
```

### 2. 响应统一包装

**声明式：自动包装，无需编码**

```java
// Controller 返回非 R 类型时自动包装
@GetMapping("/user")
public User getUser() {
    return userService.getById(1L);
}
// 实际返回: {"code":"00000","message":"ok","data":{"id":1,"name":"张三"}, "timestamp":1705300000000}

// Controller 返回 R 类型时不重复包装
@GetMapping("/user")
public R<User> getUser() {
    return R.ok(userService.getById(1L));
}
// 实际返回: {"code":"00000","message":"ok","data":{"id":1,"name":"张三"}, "timestamp":1705300000000}
```

**编程式：手动构建响应**

```java
// 直接返回 R 对象
@GetMapping("/user/{id}")
public R<User> getUser(@PathVariable Long id) {
    User user = userService.getById(id);
    return user != null ? R.ok(user) : R.fail("USER_404", "用户不存在");
}
```

### 3. XSS 过滤

**声明式：无需编码，自动生效**

引入模块后，所有请求参数自动过滤 XSS 脚本标签（如 `<script>`、`onerror=` 等）。

**编程式：配置排除路径和开关**

```yaml
letool:
  web:
    xss-filter:
      enabled: true
      exclude-paths:
        - /api/richtext/**     # 富文本编辑器接口不拦截
```

### 4. SQL 注入防御

**声明式：无需编码，自动生效**

请求参数中包含 SQL 关键字（`SELECT`、`DROP`、`UNION` 等）时自动拦截，返回 400 错误。

```yaml
letool:
  web:
    sql-injection-filter:
      enabled: true
```

### 5. 请求日志

引入模块后自动记录请求日志（路径、方法、耗时、状态码）：

```log
2025-01-15 14:30:00.123 [traceId=abc123] INFO  - GET /api/user/123 200 45ms
2025-01-15 14:30:00.456 [traceId=abc123] INFO  - POST /api/order 201 120ms
```

## 配置属性

```yaml
letool:
  web:
    enabled: true                       # 总开关
    response-wrapper:
      enabled: true                     # 响应统一包装开关
      exclude-paths:                    # 不包装的路径（如文件下载、SSE）
        - /api/export/**
        - /api/sse/**
    xss-filter:
      enabled: true                     # XSS 过滤开关
      exclude-paths:                    # 不过滤的路径（如富文本编辑器）
        - /api/richtext/**
    sql-injection-filter:
      enabled: true                     # SQL 注入防御开关
    request-log:
      enabled: true                     # 请求日志开关
      include-body: false               # 是否记录请求体
      max-body-size: 4096               # 请求体最大记录长度（字节）
```
