# letool-starter-log

## 模块简介

`letool-starter-log` 是面向业务开发的轻量日志封装模块，提供**链路追踪 TraceId**、
**审计日志**和**方法执行日志**三大核心能力。通过注解驱动与编程式 API，
减少链路标识、关键操作审计和方法耗时记录的业务样板代码。

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
    // 自动记录入参、出参、耗时
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

引入模块后，`TraceFilter` 自动拦截所有 HTTP 请求：
- 从请求头 `X-Trace-Id` 读取 TraceId
- 请求头缺失时自动生成
- 响应头回写 `X-Trace-Id`，方便前端/网关串联

### 2. 方法日志 @MethodLog

**注解声明式**

```java
// 记录全部信息（入参、出参、耗时）
@MethodLog
public Order createOrder(OrderRequest req) { ... }

// 自定义标题
@MethodLog("创建订单")
public Order createOrder(OrderRequest req) { ... }

// 不记录入参出参（参数包含敏感数据）
@MethodLog(logArgs = false, logResult = false)
public void resetPassword(Long userId, String newPassword) { ... }

// 限制出参长度 + 不记录异常日志
@MethodLog(value = "查询报表", maxResultLength = 200, logException = false)
public Report queryReport(ReportQuery query) { ... }
```

**注解属性说明**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `value` | String | "" | 日志标题，为空时使用方法名 |
| `logArgs` | boolean | true | 是否记录入参 |
| `logResult` | boolean | true | 是否记录出参 |
| `maxResultLength` | int | 500 | 出参最大字符数，超长截断 |
| `logException` | boolean | true | 是否记录异常日志 |

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

// 敏感操作：不记录请求体
@AuditLog(operation = "修改密码", type = AuditType.AUTH, bizNo = "#userId", logRequestBody = false)
public void changePassword(Long userId, String newPassword) { ... }
```

**注解属性说明**

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `operation` | String | (必填) | 操作名称，人类可读 |
| `type` | AuditType | BUSINESS | AUTH=认证, ADMIN=管理, BUSINESS=业务 |
| `bizNo` | String | "" | 业务单号，支持 SpEL 表达式 (如 `#userId`) |
| `logRequestBody` | boolean | true | 是否记录请求体 |
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
    .clientIp("192.168.1.100")
    .build();
auditLogService.record(event);
```

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
      async: true                       # 异步写入（推荐，不阻塞业务）
      storage: file                     # 存储后端: memory / file / database
    web-log:
      enabled: true                     # Controller 请求日志开关
      include-headers: false            # 是否记录请求头（含 Authorization）
      include-body: false               # 是否记录请求/响应体
      max-body-length: 1024             # 体截断长度（字节）
      exclude-paths:                    # 不记录日志的路径
        - /actuator/**
        - /swagger-ui/**
```

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
