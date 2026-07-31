# letool-starter-log

## 模块简介

`letool-starter-log` 是面向业务开发的轻量日志封装模块，提供**链路追踪 TraceId**、
**Web 请求日志**、**审计日志**和**方法执行日志**。通过 Servlet 标准过滤器、
注解驱动与编程式 API，减少链路标识、请求耗时、关键操作审计和方法日志样板代码。

方法日志和审计日志直接复用 Spring Boot 官方 `spring-boot-starter-aop`；
Web 请求日志复用 Spring `OncePerRequestFilter` 和 `PathPatternParser`，无需维护
Controller 切面、Ant 路径转换或请求体缓存等重复基础设施。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-log</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-log</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

### 2. 启用链路追踪

无需额外配置，请求进入时自动从 `X-Trace-Id` 请求头读取或生成 TraceId，并通过 SLF4J MDC 注入到所有日志中。

```log
2025-01-15 14:30:00.123 [http-nio-8080-exec-1] [traceId=abc123def456] INFO  c.e.controller.UserController - 查询用户信息
```

异步方法或线程池任务需要传播 TraceId 时，引入线程模块即可继续自动使用：

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-thread</artifactId>
    <version>${letool.version}</version>
</dependency>
```

日志模块负责在请求线程建立 TraceId，线程模块负责通过唯一的 `MdcTaskDecorator`
传播 MDC。两个 Starter 可以直接同时引入，不会注册重名 Bean。只引入日志模块时
不会额外创建线程基础设施；业务也可以直接声明 Spring `TaskDecorator` 接入自己的执行器。

### 3. 记录方法日志

```java
@MethodLog
public Order createOrder(OrderRequest req) {
    // 默认记录执行状态、耗时和异常，不记录可能包含敏感信息的入参与出参
    return orderService.create(req);
}
```

### 4. 记录审计日志

```java
@AuditLog(operation = "删除用户", type = AuditType.ADMIN, bizNo = "#userId")
public void deleteUser(Long userId) {
    userMapper.deleteById(userId);
}
```

切面会在方法结束后生成完整的 `AuditLogEvent`。默认实现将事件序列化为 JSON，
并写入名称为 `letool.audit` 的专用 SLF4J Logger。文件滚动、异步输出和集中采集
继续由应用使用的 Logback、Log4j2 或日志平台负责。

## 核心 API 示例

### 1. 链路追踪 TraceContext

**编程式：手动获取/设置 TraceId**

```java
// 获取当前 TraceId
String traceId = TraceContext.getTraceId();

// 从上游传入时设置 TraceId
TraceContext.setTraceId("upstream-trace-id-123");

// 获取或自动生成 TraceId
String traceId = TraceContext.getOrGenerate();

// 清理（通常由 Filter 自动管理，无需手动调用）
TraceContext.clear();
```

**声明式：无需代码，自动生效**

引入模块后，`TraceIdFilter` 自动拦截所有 HTTP 请求：

- 从请求头 `X-Trace-Id` 读取 TraceId
- 请求头缺失时自动生成
- 响应头回写 `X-Trace-Id`，方便前端/网关串联

### 2. 方法日志 @MethodLog

**注解声明式**

```java
// 默认只记录执行状态、耗时和完整异常堆栈
@MethodLog
public Order createOrder(OrderRequest req) { ... }

// 确认数据安全后，显式记录 JSON 入参与出参
@MethodLog(value = "创建订单", logArgs = true, logResult = true)
public Order createOrder(OrderRequest req) { ... }

// 分别限制入参与出参长度
@MethodLog(
    value = "查询报表",
    logArgs = true,
    logResult = true,
    maxArgsLength = 100,
    maxResultLength = 200)
public Report queryReport(ReportQuery query) { ... }
```

**注解属性说明**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | "" | 日志标题，为空时使用方法名 |
| `logArgs` | boolean | false | 是否使用 `JsonCodec` 记录入参 |
| `logResult` | boolean | false | 是否使用 `JsonCodec` 记录出参 |
| `maxArgsLength` | int | 500 | 入参最大字符数，超长截断 |
| `maxResultLength` | int | 500 | 出参最大字符数，超长截断 |
| `logException` | boolean | true | 是否记录异常日志 |

方法日志会自动使用 Spring 容器中的 `JsonCodec` Bean。应用可以复用工具模块的
Fastjson2 构建器，也可以提供 Jackson、Gson 等自定义实现：

```java
@Bean
public JsonCodec jsonCodec() {
    return Fastjson2JsonCodec.builder()
        .dateFormat("yyyy-MM-dd HH:mm:ss")
        .build();
}
```

编解码器失败只会输出不包含原始数据的警告和 `<序列化失败>` 占位文本，不会改变
业务方法的返回值或异常。非 Web 线程由方法切面临时创建的 TraceId 也会在调用结束后清理。

**编程式：SLF4J 原生 API + TraceId**

由于 TraceId 已自动注入 MDC，使用 SLF4J 即可获取带 TraceId 的日志：

```java
private static final Logger log = LoggerFactory.getLogger(MyService.class);

public void doSomething() {
    log.info("处理业务逻辑, orderId={}", orderId);
    // 日志输出自动包含 TraceId： [traceId=abc123] 处理业务逻辑, orderId=456
}
```

### 3. 审计日志 @AuditLog

**注解声明式**

```java
// 认证操作
@AuditLog(operation = "用户登录", type = AuditType.AUTH, bizNo = "#username")
public LoginResult login(String username, String password) { ... }

// 管理操作
@AuditLog(operation = "删除用户", type = AuditType.ADMIN, bizNo = "#userId")
public void deleteUser(Long userId) { ... }

// 业务操作
@AuditLog(operation = "创建订单", type = AuditType.BUSINESS, bizNo = "#request.orderNo")
public Order createOrder(@RequestBody CreateOrderRequest request) { ... }

// 请求参数默认不记录，适合密码等敏感操作
@AuditLog(operation = "修改密码", type = AuditType.AUTH, bizNo = "#userId")
public void changePassword(Long userId, String newPassword) { ... }

// 只有确认参数可安全输出时，才显式开启请求参数记录
@AuditLog(
    operation = "创建公开报表",
    bizNo = "#request.reportNo",
    logRequestBody = true,
    maxBodyLength = 512)
public Report createReport(CreateReportRequest request) { ... }
```

**注解属性说明**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `operation` | String | (必填) | 操作名称，人类可读 |
| `type` | AuditType | BUSINESS | AUTH=认证, ADMIN=管理, BUSINESS=业务 |
| `bizNo` | String | "" | 业务单号，支持 SpEL 表达式 (如 `#userId`) |
| `logRequestBody` | boolean | false | 是否记录方法参数 JSON，需显式开启 |
| `maxBodyLength` | int | 1024 | 请求体最大字符数 |

**编程式**

```java
@Autowired
private AuditLogService auditLogService;

// 手动记录审计事件
AuditLogEvent event = AuditLogEvent.builder()
    .operator("admin")
    .operation("导出报表")
    .type(AuditType.BUSINESS)
    .bizNo("RPT-20250115-001")
    .result("SUCCESS")
    .ip("192.168.1.100")
    .build();
auditLogService.record(event);
```

### 4. 自定义审计上下文

Servlet 应用默认从标准 Principal、远端地址和 User-Agent 获取上下文，不会直接信任
`X-Forwarded-For`。接入 Spring Security、租户体系或可信网关时，可以替换扩展点：

```java
@Bean
public AuditContextProvider auditContextProvider(CurrentUser currentUser) {
    return () -> new AuditContext(
        currentUser.getUserId(),
        currentUser.getClientIp(),
        currentUser.getUserAgent());
}
```

### 5. 自定义数据库持久化

核心模块不依赖 JDBC、MyBatis-Plus 或 Spring Data，也不会自动创建数据库表。需要数据库
查询和统计时，业务应用只需提供自己的 `AuditLogService`：

```java
@Bean
public AuditLogService auditLogService(SysAuditLogService service) {
    return event -> service.save(SysAuditLog.from(event));
}
```

MySQL 8 参考脚本位于：

```text
META-INF/letool/log/mysql-audit-log.sql
```

该脚本覆盖 `AuditLogEvent` 的全部通用字段和常用查询索引，但不会被 Spring Boot
自动执行。业务可以增加租户、数据权限、逻辑删除或自定义主键字段，也可以改用 MQ、
Elasticsearch 或其他存储方案。

### 6. Web 请求日志

Servlet Web 应用会自动注册 `WebLogFilter`，并在 `TraceIdFilter` 之后执行。默认日志只包含
请求方法、URI、真实响应状态和完整处理耗时：

```log
[trace-web] POST /orders → 201，耗时: 36ms
```

- 正常响应使用 INFO，4xx 使用 WARN，5xx 和未处理异常使用 ERROR。
- 未处理异常记录完整堆栈并继续抛给 Servlet 容器。
- Spring MVC 异步请求会等待 `AsyncContext` 完成后再记录最终状态和总耗时。
- 排除路径使用 Spring `PathPatternParser`，支持 `/actuator/**` 等标准路径模式。
- 核心模块不采集 Header、请求体或响应体，避免 Token、密码、文件和大对象进入日志。
- 应用声明自己的 `WebLogFilter` Bean 后，Starter 会自动退让默认实现并继续负责 Servlet 注册。

需要完整 HTTP 报文日志时，建议由业务项目接入 Zalando Logbook 等专用框架，并关闭
`letool.log.web-log.enabled`，而不是在轻量访问日志中重复维护请求/响应包装器。

## 配置属性

```yaml
letool:
  log:
    trace:
      enabled: true                     # 链路追踪开关
      header-name: X-Trace-Id           # 请求头中 TraceId 的键名
      generate-if-absent: true          # 无 TraceId 时自动生成
    audit:
      enabled: true                     # 审计日志开关
    web-log:
      enabled: true                     # Servlet 请求日志开关
      exclude-paths:                    # 不记录日志的 Spring 路径模式
        - /actuator/**
        - /swagger-ui/**
```

## 从旧版方法与 Web 日志迁移

本次调整是破坏性变更。旧版 `WebLogAspect` 无法获得请求完成后的可靠状态，并且
`include-headers`、`include-body`、`max-body-length` 从未参与日志记录，因此均已移除。

| 旧用法 | 迁移方式 |
|---|---|
| 依赖 `@MethodLog` 默认记录入参与出参 | 确认数据安全后显式设置 `logArgs = true`、`logResult = true` |
| 依赖对象 `toString()` 输出 | 声明统一的 `JsonCodec` Bean，方法日志和审计日志会自动复用 |
| `include-headers` / `include-body` / `max-body-length` | 删除旧配置；完整 HTTP 报文日志请接入专用框架 |
| 自定义 `WebLogAspect` | 声明自己的 `WebLogFilter` Bean；需要完全接管注册时替换 `webLogFilterRegistration` |
| Ant 风格排除路径 | 保留原路径配置，由 Spring `PathPatternParser` 解析 |

配置采用严格绑定。继续保留已经删除的三项 Web 日志属性会使应用启动失败，
避免用户误以为 Header 或请求体正在被记录。

## 从旧版审计存储迁移

旧版 `async`、`storage` 配置以及 `LogRecordStore`、`FileLogStore`、`MemoryLogStore`
已移除。它们分别重复实现了日志后端已有的异步、滚动文件能力，并且不存在真正的
`database` 实现。

| 旧用法 | 迁移方式 |
|---|---|
| 使用旧版 `@AuditLog` | 新版注解切面会真正生成事件，请在升级前复核现有注解和敏感参数 |
| 依赖请求参数默认记录 | 新版默认关闭；确认数据安全后显式设置 `logRequestBody = true` |
| `storage: file` | 配置 `letool.audit` Logger 的 Logback/Log4j2 Appender |
| `storage: memory` | 测试中自定义收集事件的 `AuditLogService` |
| `storage: database` | 按参考 SQL 建表，并声明自己的 `AuditLogService` |
| 实现 `LogRecordStore` | 改为实现更高层的 `AuditLogService` |
| `async: true` | 使用日志后端的 AsyncAppender，或在自定义服务中接入业务线程设施 |

配置属性采用严格绑定。升级后继续保留 `letool.log.audit.storage` 或
`letool.log.audit.async` 会使应用启动失败，避免持久化方式被静默改变。

## 从旧版 MDC 装饰器迁移

本次调整是破坏性变更。日志模块中的
`com.github.leyland.letool.log.trace.MdcTaskDecorator` 及其自动配置 Bean
将在下一个 2.0 预发布版本移除，因为它与线程模块重复，并且会在两个 Starter
同时启用时造成 `mdcTaskDecorator` Bean 重名。

| 旧用法 | 迁移方式 |
|---|---|
| 仅引入日志模块并依赖自动创建的 `mdcTaskDecorator` | 增加 `letool-starter-thread`，默认自动传播 MDC |
| 手动创建日志模块 `MdcTaskDecorator` | 改用 `com.github.leyland.letool.thread.propagation.MdcTaskDecorator` |
| 自定义上下文传播 | 继续声明 Spring `TaskDecorator`；线程模块会按 Spring 顺序规则组合用户装饰器 |

该删除项是重复的具体实现，不是要求用户实现的预留接口。Spring `TaskDecorator`
仍然是公开扩展点，业务可以按租户、安全上下文或其他需求自由组合。
