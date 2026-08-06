# letool-starter-web

`letool-starter-web` 是基于 Spring MVC 的 Web 开发便利模块。它不替代 Spring MVC，而是把多数业务项目都会重复编写的统一响应、异常协议、API 主版本路由和受限请求体重复读取提前封装好，让 Controller 更专注于业务逻辑。

## 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-web</artifactId>
    <version>${letool.version}</version>
</dependency>
```

模块基于 Spring Boot 自动配置。`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 已登记 `WebAutoConfiguration`，业务项目无需手动 `@Import`。

## 默认行为

引入依赖后默认启用：

- 普通 JSON 返回值统一包装为 `R<T>`。
- `BusinessException`、`SystemException` 及常见 Spring MVC 异常统一转换为稳定错误码和正确 HTTP 状态。
- `@ApiVersion` 主版本路由。

可重复读请求体默认关闭，避免每个请求无条件占用额外堆内存。模块不会自动注册 Controller 或暴露 HTTP 接口。

## 完整配置

```yaml
letool:
  web:
    enabled: true
    response-wrapper:
      enabled: true
      exclude-paths:
        - /actuator/**
    api-version:
      enabled: true
      header-name: X-API-Version
      parameter-name: apiVersion
    repeatable-request:
      enabled: false
      max-body-size: 1MB
      exclude-paths: []
      include-media-types:
        - application/json
        - application/*+json
        - application/xml
        - application/*+xml
        - text/*
```

`exclude-paths` 使用 Spring `PathPattern` 语义，并匹配去除 context path 后的应用内路径。非法路径、媒体类型、空白版本来源名称及超过 16 MiB 的请求体缓存配置会在启动阶段失败。

## 统一响应

Controller 可以直接返回业务对象：

```java
@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getRequired(id);
    }
}
```

JSON 响应会自动变为：

```json
{
  "code": "00000",
  "message": "ok",
  "data": {
    "id": 1,
    "name": "张三"
  },
  "timestamp": 1785981600000
}
```

以下场景保持原始响应，不进行包装：

- 返回值已经是 `R`。
- 方法或 Controller 标注 `@ExcludeWrapper`。
- 请求路径命中 `response-wrapper.exclude-paths`。
- `String`、`byte[]`、`Resource`、文件下载和原始字符响应。
- `StreamingResponseBody`、`ResponseBodyEmitter`、`SseEmitter` 等流式响应。
- `ProblemDetail`、非 JSON 内容、`HEAD` 请求和 HTTP 204。

`ResponseEntity<T>` 的状态和响应头始终由 Spring 保持；只有符合条件的 JSON 响应体会被包装。

需要排除单个接口时：

```java
@ExcludeWrapper
@GetMapping("/export")
public Resource export() {
    return exportService.createResource();
}
```

## 统一异常协议

业务异常继续使用 `letool-starter-exception` 的结构化错误码：

```java
private static final ErrorCode USER_NOT_FOUND =
        ErrorCode.of("USER_404", "用户不存在：{0}");

@GetMapping("/{id}")
public User getUser(@PathVariable Long id) {
    return userRepository.findById(id)
            .orElseThrow(() -> BusinessException.of(USER_NOT_FOUND, id));
}
```

`MessageResolver` 会在 HTTP 边界按请求语言环境解析消息。错误响应不会复制 cause、堆栈、被拒绝的原始值或底层异常文本。

| 场景 | HTTP 状态 | 默认错误码 |
| --- | ---: | --- |
| 参数或对象校验失败 | 400 | `WEB_400_001` |
| 缺少请求参数、请求头或请求部分 | 400 | `WEB_400_002` |
| 参数类型不匹配 | 400 | `WEB_400_003` |
| 请求体格式错误 | 400 | `WEB_400_004` |
| 非法参数 | 400 | `WEB_400_005` |
| 请求资源不存在 | 404 | `WEB_404_001` |
| 请求方法不支持 | 405 | `WEB_405_001` |
| 无法生成客户端接受的响应格式 | 406 | `WEB_406_001` |
| 请求体或上传内容过大 | 413 | `WEB_413_001` |
| 请求媒体类型不支持 | 415 | `WEB_415_001` |
| 其他 Spring 4xx | 保留原状态 | `WEB_4XX_001` |
| 未分类服务端错误 | 500 | `WEB_500_001` |

业务可以定义优先级更高的 `@RestControllerAdvice` 处理应用专用异常；提供自定义 `GlobalExceptionHandler` Bean 时，默认 Bean 会退让。

## API 主版本路由

相同路径可以通过 `@ApiVersion` 提供多个主版本：

```java
@ApiVersion(1)
@GetMapping("/profile")
public UserProfileV1 profileV1() {
    return profileService.getV1();
}

@ApiVersion(2)
@GetMapping("/profile")
public UserProfileV2 profileV2() {
    return profileService.getV2();
}
```

客户端可以使用请求头：

```http
GET /profile
X-API-Version: 2
```

也可以使用查询参数：

```http
GET /profile?apiVersion=2
```

版本支持 `1`、`1.0`、`1.2.3` 等数字点分格式，并使用第一段作为主版本。`v1`、`1-beta`、`1.` 等格式不会匹配。

非空请求头拥有绝对优先级。请求头存在但格式非法时不会回退到查询参数，避免两个来源冲突时产生不确定路由。方法上的 `@ApiVersion` 覆盖类上的声明，版本值必须大于 0。

如果业务已经提供 `WebMvcRegistrations`，默认 API 版本映射会退让；此时业务需要自行整合 `ApiVersionRequestMappingHandlerMapping`，否则 `@ApiVersion` 不会生效。

## 可重复读请求体

签名校验、防重放或特定过滤器需要在 Controller 之前读取请求体时，可以显式开启：

```yaml
letool:
  web:
    repeatable-request:
      enabled: true
      max-body-size: 1MB
      exclude-paths:
        - /uploads/**
```

开启后，符合条件的下游过滤器和 Controller 可以分别读取完整请求体。模块同时执行以下保护：

- 同时校验 `Content-Length` 和实际读取字节数，未知长度请求也不能绕过限制。
- 默认上限为 1 MiB，可配置范围为 1 字节到 16 MiB。
- 只缓存配置允许的 JSON、XML 和文本媒体类型。
- multipart、`application/x-www-form-urlencoded` 和 `application/octet-stream` 始终绕过缓存。
- `getReader()` 使用请求声明字符集，未声明时遵循 Servlet 默认 ISO-8859-1。
- 超限通过统一异常协议返回 HTTP 413 和 `WEB_413_001`。

大文件上传、断点续传和流式传输应使用 `letool-starter-file` 的流式能力，不能通过提高本模块缓存上限来实现。

## 自动配置退让

所有默认组件都允许业务接管：

| 默认组件 | 退让条件 |
| --- | --- |
| `GlobalExceptionHandler` | 存在业务同类型 Bean，或不存在 `MessageResolver` |
| `ResponseWrapperAdvice` | 存在业务同类型 Bean，或响应包装关闭 |
| API 版本 `WebMvcRegistrations` | 存在业务 `WebMvcRegistrations`，或 API 版本关闭 |
| `repeatableRequestFilterRegistration` | 存在同名 Bean，或重复读能力未开启 |

`letool.web.enabled=false` 会关闭整个模块。

## 安全职责边界

本模块不再提供 XSS 输入改写和 SQL 关键字拦截。这两类通用过滤器会误报合法数据，也无法形成可靠安全边界：

- SQL 注入应使用 MyBatis、MyBatis-Plus、Spring Data、`JdbcTemplate` 占位符或其他参数化查询。表名、排序字段等无法参数化的结构必须使用业务白名单。
- 普通文本保持原始业务语义，在 HTML、属性、JavaScript、URL 等实际输出上下文编码。
- 允许富文本的字段由业务显式选择成熟 HTML Sanitizer，并按业务允许标签配置白名单。
- CSP、安全响应头、Cookie 策略和 CORS 使用 Spring Security、Spring MVC 或网关原生能力。
- 请求日志、traceId、审计和耗时统计使用 `letool-starter-log`、监控模块或网关，不因日志目的默认缓存所有请求体。

## 破坏性变化

本次生产化调整删除以下旧配置和类型，不提供无效兼容层：

- `letool.web.xss-filter.*`
- `letool.web.sql-injection-filter.*`
- `letool.web.request-log.*`
- `XssFilter`、`SqlInjectionFilter`、`XssRequestWrapper`、`XssCleaner`

同时，可重复读请求体由默认开启改为默认关闭；API 版本改为严格格式校验；响应排除路径开始真实生效。升级时请删除旧配置，并按本说明迁移安全策略。
