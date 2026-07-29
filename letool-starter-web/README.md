# letool-starter-web

## 模块简介

`letool-starter-web` 是 Web 增强模块，基于 Spring Boot Web 提供**全局异常处理**、**响应统一包装**、**XSS 过滤**和**SQL 注入防御**四大能力。无需修改 Controller 代码，即可将符合条件的返回值包装为 `R<T>` 格式，将异常统一转换为标准化错误响应，并拦截常见 Web 攻击。

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
import com.github.leyland.letool.exception.code.ErrorCode;
import com.github.leyland.letool.exception.core.BusinessException;

private static final ErrorCode USER_NOT_FOUND =
        ErrorCode.of("USER_404", "用户不存在：{0}");

@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    User user = userService.getById(id);
    if (user == null) {
        throw BusinessException.of(USER_NOT_FOUND, id);
    }
    return user;
}
// 默认中文响应: {"code":"USER_404","message":"用户不存在：123","data":null}
```

## 核心 API 示例

### 1. 全局异常处理 GlobalExceptionHandler

**声明式：无需编码，自动生效**

引入模块后，`@RestControllerAdvice` 自动注册，所有异常统一转换为 `R<T>` 格式返回。

**异常映射表**

| 异常类型 | HTTP 状态码 | 响应规则 |
|---------|------------|---------|
| `BusinessException` | 400 | 返回稳定错误码，消息按请求 Locale 解析 |
| `MethodArgumentNotValidException` | 400 | 返回 `VALID_001` 和字段校验结果 |
| `IllegalArgumentException` | 400 | 固定返回安全消息 `参数不合法`，不回显异常文本 |
| `SystemException` | 500 | 返回稳定错误码和本地化消息；调用方必须确保消息内容已脱敏 |
| 其他 `BaseException` 子类 | 500 | 返回稳定错误码和本地化消息；调用方必须确保消息内容已脱敏 |
| `Exception`（兜底） | 500 | 固定返回 `SYS_001` 和安全消息 `系统内部错误，请稍后重试` |

`BusinessException`、`SystemException` 和其他 `BaseException` 的 HTTP 文案由
`MessageResolver` 在响应边界解析。默认 handler 不会把异常 cause 或堆栈复制到 HTTP 响应。
`BusinessException` 属于预期业务拒绝，服务端仅以 `warn` 记录稳定 code/fallback，不自动打印其
stack/cause；`SystemException`、其他 `BaseException` 和兜底 `Exception` 分支会记录完整
Throwable，当前 `IllegalArgumentException` 分支也会以 `warn` 保留 Throwable。默认消息资源本身
应使用可公开的安全文案；
`custom(...)` 传入的文本和消息占位参数会原样参与响应解析，调用方必须先脱敏，禁止传入连接串、
密码、令牌或底层异常文本等敏感信息。

**编程式：自定义异常处理**

如需为应用专用异常扩展响应规则，可以定义一个更高优先级的 `@RestControllerAdvice`：

```java
import com.github.leyland.letool.exception.message.MessageResolver;
import com.github.leyland.letool.tool.model.R;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class CustomExceptionHandler {

    private final MessageResolver messageResolver;

    public CustomExceptionHandler(MessageResolver messageResolver) {
        this.messageResolver = messageResolver;
    }

    @ExceptionHandler(MyCustomException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleCustom(MyCustomException e) {
        return R.fail(e.getCode(), messageResolver.resolve(e));
    }
}
```

`MyCustomException` 应继承新的 `BaseException` 层次。旧
`com.github.leyland.letool.tool.exception` 包及其字符串构造器已删除，迁移方式参阅
[`letool-starter-exception`](../letool-starter-exception/README.md)。
自定义 advice 只处理应用自己的专用异常，未匹配的异常继续交给默认 handler；显式
`@Order` 可以避免多个 advice 同优先级时的处理顺序不确定。

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

返回值已经是 `R`、`String` 或 `Resource` 时不会包装。需要排除某个 Controller 或方法时，使用
`@ExcludeWrapper`，当前实现不支持按配置路径排除：

```java
import com.github.leyland.letool.web.annotation.ExcludeWrapper;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;

@ExcludeWrapper
@GetMapping("/export")
public Resource export() {
    return exportService.createResource();
}
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

**配置开关**

```yaml
letool:
  web:
    xss-filter:
      enabled: true
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

## 配置属性

```yaml
letool:
  web:
    enabled: true                       # 总开关
    xss-filter:
      enabled: true                     # XSS 过滤开关
    sql-injection-filter:
      enabled: true                     # SQL 注入防御开关
```
