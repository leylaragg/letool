# letool-starter-tool

## 模块简介

`letool-starter-tool` 是 letool 的通用工具模块，为其他模块提供 JSON 序列化、HTTP 请求、
Redis 操作、分布式 ID、树形结构、字符串、集合、日期时间、Bean 拷贝、统一响应体、
分页模型及 Spring 容器辅助能力。统一异常和国际化由独立的
[`letool-starter-exception`](../letool-starter-exception/README.md) 模块提供；tool 模块依赖该基础模块，
但不再自行维护异常体系。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-tool</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-tool</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

### 2. 使用 JSON 序列化

```java
// 默认入口：保持现有 Fastjson2 紧凑输出策略
String json = JsonUtil.toJsonString(user);
String pretty = JsonUtil.toPrettyJson(user);

// JSON 转对象
User user = JsonUtil.parseObject(json, User.class);
List<User> users = JsonUtil.parseArray(jsonArray, User.class);

// 单次调用使用自定义策略，不修改全局状态
JsonCodec codec = Fastjson2JsonCodec.builder()
    .writerFeatures(JSONWriter.Feature.WriteNulls)
    .readerFeatures(JSONReader.Feature.SupportSmartMatch)
    .dateFormat("yyyy-MM-dd HH:mm:ss")
    .build();
String customJson = JsonUtil.toJsonString(user, codec);
```

### 3. 返回统一响应体

```java
@GetMapping("/user/{id}")
public R<User> getUser(@PathVariable Long id) {
    User user = userService.getById(id);
    return user != null ? R.ok(user) : R.fail("USER_404", "用户不存在");
}
```

### 4. 使用统一异常

业务异常、系统异常、错误码和国际化消息解析请参阅
[`letool-starter-exception`](../letool-starter-exception/README.md)。异常模块使用
`BusinessException.of(...)`、`BusinessException.custom(...)` 和
`SystemException.causedBy(...)` 等工厂方法，不再提供旧的字符串构造器。

## 核心 API 示例

### 1. 统一响应体 R\<T\>

**编程式（直接使用）**

```java
// 成功响应
R.ok();                          // code="00000", message="ok", data=null
R.ok(user);                      // code="00000", message="ok", data=user

// 失败响应
R.fail("USER_001", "用户名不能为空");
R.fail("VALID_001", "参数校验失败", errorFields);

// 判断响应
if (r.isSuccess()) { ... }
```

**声明式（配合 letool-starter-web）**

引入 `letool-starter-web` 后，Controller 直接返回业务对象即可，`ResponseBodyAdvice` 自动包装为 `R<T>`：

```java
@GetMapping("/user")
public User getUser() {
    return userService.getById(1L);  // 自动包装为 R.ok(user)
}
```

### 2. 异常能力边界

`R<T>` 仍由 tool 模块提供；错误码、异常类型、国际化解析以及日志/HTTP 消息边界由
[`letool-starter-exception`](../letool-starter-exception/README.md) 提供。这样工具类不会持有
`MessageSource` 等全局状态，非 Web 任务也可以稳定记录异常。

### 3. JSON 工具 JsonUtil

`JsonUtil` 是静态兼容门面，原有方法继续使用内置的不可变 Fastjson2 codec。需要不同策略时，
可以给单次调用显式传入 `JsonCodec`；Spring 应用则优先注入自动配置的 `JsonCodec` Bean。
Letool 不提供可变的全局 `setCodec`，避免测试之间和并发请求之间互相污染。

```java
// 序列化
String json = JsonUtil.toJsonString(obj);
String pretty = JsonUtil.toPrettyJson(obj);
byte[] bytes = JsonUtil.toJsonBytes(obj);

// 反序列化（普通类型）
User user = JsonUtil.parseObject(json, User.class);

// 反序列化（泛型类型）
List<User> users = JsonUtil.parseObject(json,
    new TypeReference<List<User>>() {}.getType());

List<User> users = JsonUtil.parseArray(jsonArray, User.class);

// 对象转换
Map<String, Object> map = JsonUtil.toMap(user);
User user = JsonUtil.toBean(map, User.class);
UserVO vo = JsonUtil.convert(userDO, UserVO.class);
```

**Spring 中替换默认策略**

```java
@Configuration
class JsonConfiguration {

    @Bean
    JsonCodec jsonCodec() {
        return Fastjson2JsonCodec.builder()
            .writerFeatures(
                JSONWriter.Feature.WriteNulls,
                JSONWriter.Feature.WriteEnumsUsingName
            )
            .readerFeatures(JSONReader.Feature.SupportSmartMatch)
            .dateFormat("iso8601")
            .build();
    }
}
```

自动配置使用 `@ConditionalOnMissingBean(JsonCodec.class)`，因此应用 Bean 会完整替换默认 Bean。
若使用 Jackson、Gson 或公司内部序列化组件，只需实现 provider-neutral 的 `JsonCodec` 接口。
实现类构造完成后必须线程安全，并遵守接口定义的空值和 UTF-8 语义。

通用 codec 会拒绝 Fastjson2 已废弃的 `JSONReader.Feature.SupportAutoType`。需要恢复 Redis
多态对象时，应使用下文带 `AutoTypeBeforeHandler` allow-list 的 Redis serializer，不要在
普通 JSON 入口全局开启 AutoType。

默认实现的技术失败统一抛出 `JsonCodecException`：

| 错误码 | 含义 |
|---|---|
| `TOOL_JSON_001` | JSON 序列化失败 |
| `TOOL_JSON_002` | JSON 反序列化失败 |

异常保留原始 cause，但不会把原始 JSON 放入消息，避免日志泄露敏感数据。

### 4. HTTP 工具 HttpUtil

HTTP 便利能力基于 JDK 17 `java.net.http.HttpClient`，连接复用、HTTP/2 和协议处理由 JDK 负责；
Letool 只封装链式请求、Multipart、拦截器、有界响应、受控重试和统一异常。模块不会再根据
classpath 假装切换 Apache HttpClient 或 OkHttp。

**静态便捷方法（适合固定默认值的简单调用）**

```java
String result = HttpUtil.get("https://api.example.com/user/123");
String result = HttpUtil.post("https://api.example.com/user", jsonBody);
String result = HttpUtil.put("https://api.example.com/user/1", jsonBody);
String result = HttpUtil.delete("https://api.example.com/user/123");
```

静态入口使用 5 秒连接超时、30 秒请求总超时、16 MiB 响应上限和不自动跟随重定向的不可变默认配置。
它不提供可变全局开关，因此并发请求和不同测试之间不会相互污染。实例配置可调整响应上限，但为了
避免把文件下载误用成内存响应，取值不能超过 256 MiB。

**链式请求**

```java
HttpResponse resp = HttpUtil.create()
    .url("https://api.example.com/order")
    .post()
    .header("Authorization", "Bearer " + token)
    .contentType("application/json")
    .body(jsonBody)
    .timeout(Duration.ofSeconds(20))
    .execute();

String body = resp.getBody();
byte[] bytes = resp.getBodyBytes();
int status = resp.getStatusCode();
String traceId = resp.header("X-Trace-Id");
Duration duration = resp.getDuration();
int attempts = resp.getAttempts();
```

HTTP 4xx 和 5xx 是服务端返回的正常协议响应，不会自动转换为异常；可以使用 `is2xx()`、`is4xx()`、
`is5xx()` 或状态码自行处理。URL 非法、连接失败、超时、中断、响应越界和拦截器失败才会抛出
`HttpException`。

**实例化配置与 Spring 注入**

```java
HttpConfig config = HttpConfig.builder()
    .connectTimeout(Duration.ofSeconds(3))
    .requestTimeout(Duration.ofSeconds(15))
    .maxResponseBytes(4 * 1024 * 1024L)
    .redirectPolicy(HttpClient.Redirect.NORMAL)
    .build();

HttpTemplate template = new HttpTemplate(config);
HttpResponse response = template.create("https://api.example.com/ping")
    .get()
    .execute();
```

Spring 应用默认可以直接注入 `HttpTemplate`。声明自定义 `HttpTemplate` Bean 后，Starter 默认 Bean 会退让。
需要代理、企业 TLS、认证器或自定义执行器时，应先构造 JDK `HttpClient` 再交给模板：

```java
@Bean
HttpTemplate httpTemplate() {
    HttpClient client = HttpClient.newBuilder()
        .proxy(ProxySelector.getDefault())
        .authenticator(companyAuthenticator())
        .build();
    return new HttpTemplate(HttpConfig.defaults(), client);
}
```

**Multipart 文件上传**

```java
HttpResponse response = httpTemplate.create("https://api.example.com/files")
    .post()
    .formField("folder", "reports")
    .formFile("file", Path.of("report.xlsx"))
    .execute();
```

文件部分使用 JDK `BodyPublisher` 流式发送，不会先把整个文件装入 byte 数组；中文文件名按照常见
`multipart/form-data` 实现直接以 UTF-8 写入 `filename`，不会写入该媒体类型禁止的 `filename*`。
大文件下载、断点续传和进度监听仍应使用 `letool-starter-file`，
不要把 HTTP 工具的内存响应上限调成无界。

**重试安全边界**

```java
HttpResponse response = httpTemplate.create("https://api.example.com/inventory")
    .get()
    .maxRetry(2)
    .retryOn(429, 502, 503, 504)
    .retryDelay(Duration.ofMillis(200))
    .execute();
```

默认不重试。配置重试后，GET、HEAD、PUT、DELETE 和 OPTIONS 可以自动重试传输失败或显式状态码；
POST、PATCH 不会被静默重放。只有调用方确认业务具有幂等保障时，才可以显式调用
`retryNonIdempotent(true)`。

稳定错误码如下：

| 错误码 | 含义 |
|---|---|
| `TOOL_HTTP_001` | 请求地址、请求头或请求体配置无效 |
| `TOOL_HTTP_002` | HTTP 连接或传输失败 |
| `TOOL_HTTP_003` | 请求总超时 |
| `TOOL_HTTP_004` | 请求线程被中断 |
| `TOOL_HTTP_005` | 响应体超过内存读取上限 |
| `TOOL_HTTP_006` | 用户拦截器执行失败 |

异常默认消息不会包含完整 URL、查询参数、请求体、响应体或请求头；底层原因仍保留在 cause 链中。

**2.0 迁移说明**

- 删除 `HttpUtil.getGlobalConfig()` 和 `setGlobalConfig()`，改为创建或注入 `HttpTemplate`。
- 请求级 `connectTimeout`、`readTimeout`、`writeTimeout` 收敛为语义明确的 `timeout(Duration)`；连接超时属于模板配置。
- 删除 `trustAllCerts`。测试环境也应使用受信任证书，特殊 TLS 由用户提供的 JDK `HttpClient` 管理。
- 删除未生效的连接池数量配置；连接池和 HTTP/2 连接复用由共享 JDK `HttpClient` 管理。
- `HttpResponse` 改为不可变对象，响应头类型改为 `Map<String, List<String>>`，不再提供 setter。
- 查询参数和请求头 getter 返回不可修改的多值快照，同名查询参数不会再被覆盖。

### 5. Redis 工具 RedisUtil

> 自动检测 classpath：仅在引入 `spring-boot-starter-data-redis` 后生效。

```java
@Autowired
private RedisUtil redisUtil;

// String
redisUtil.set("user:1", "张三", Duration.ofHours(1));
String name = redisUtil.get("user:1");

// 对象存取（JSON 序列化）
redisUtil.setObject("user:1", user, Duration.ofHours(1));
User user = redisUtil.getObject("user:1", User.class);

// Hash
redisUtil.hset("user:1", "name", "张三");
Map<String, String> all = redisUtil.hgetAll("user:1");

// Lua 脚本
String result = redisUtil.executeScript("return redis.call('GET', KEYS[1])",
    List.of("key1"));
```

### 6. ID 生成 IdUtil

```java
// Snowflake（趋势递增，19 位，适合数据库主键）
long id = IdUtil.nextId();
String idStr = IdUtil.nextIdStr();

// UUID（32 位无横线）
String uuid = IdUtil.simpleUUID();

// NanoId（默认 21 位，适合短 URL / 文件名）
String nano = IdUtil.nanoId();
String shortNano = IdUtil.nanoId(12);
```

### 7. 树形工具 TreeUtil

```java
// 递归构建
List<TreeNode<Dept>> tree = TreeUtil.buildTree(
    deptList,
    Dept::getId,
    Dept::getParentId,
    Dept::getName
);

// 迭代构建（深层嵌套 > 1000 层）
List<TreeNode<Dept>> tree = TreeUtil.buildTreeIterative(
    deptList, Dept::getId, Dept::getParentId
);

// 扁平化
List<TreeNode<Dept>> flat = TreeUtil.flatten(tree);
```

### 8. 字符串工具 StrUtil

```java
StrUtil.isBlank(str);
StrUtil.format("Hello, {}!", "World");     // "Hello, World!"
StrUtil.toCamelCase("user_name");           // "userName"
StrUtil.toSnakeCase("userName");            // "user_name"
StrUtil.truncate("long text...", 10);      // "long text..."
StrUtil.join(list, ", ");
```

### 9. 集合工具 CollUtil

```java
CollUtil.isEmpty(list);
List<String> list = CollUtil.newArrayList("a", "b", "c");
List<Integer> lengths = CollUtil.extract(list, String::length);
Map<Long, User> map = CollUtil.toMap(users, User::getId);
List<User> common = CollUtil.intersection(listA, listB);
List<List<User>> chunks = CollUtil.partition(users, 100);
```

### 10. 日期工具 DateUtil

```java
// 格式化
DateUtil.formatDate(LocalDate.now());         // "2025-01-15"
DateUtil.formatDateTime(LocalDateTime.now()); // "2025-01-15 14:30:00"

// 解析
LocalDate date = DateUtil.parseDate("2025-01-15");
LocalDateTime dt = DateUtil.parseDateTime("2025-01-15 14:30:00");

// 差值
long days = DateUtil.betweenDays(start, end);

// 时间边界
LocalDateTime start = DateUtil.startOfDay(LocalDate.now());
LocalDateTime end = DateUtil.endOfDay(LocalDate.now());
```

### 11. Bean 拷贝 BeanUtil

```java
// 单次拷贝（反射）
UserVO vo = BeanUtil.copy(user, UserVO.class);

// 高性能批量拷贝（CGLIB BeanCopier，字节码生成，避免反射）
List<UserVO> vos = BeanUtil.copyListFast(users, UserVO.class);

// 原地拷贝
BeanUtil.copyProperties(source, target);

// Map 互转
Map<String, Object> map = BeanUtil.toMap(user);
User user = BeanUtil.toBean(map, User.class);
```

### 12. Spring 容器工具 SpringUtil

```java
UserService service = SpringUtil.getBean(UserService.class);
UserService service = SpringUtil.getBean("userService", UserService.class);
String appName = SpringUtil.getProperty("spring.application.name");
int port = SpringUtil.getProperty("server.port", Integer.class);
Map<String, DataHandler> handlers = SpringUtil.getBeansOfType(DataHandler.class);
boolean exists = SpringUtil.containsBean("dataSource");
String profile = SpringUtil.getActiveProfile();  // "dev" / "prod"
```

### 13. Spring 表达式工具 SpelUtil

```java
// 普通表达式与显式返回类型
Integer total = SpelUtil.eval(
        "#price * #quantity",
        null,
        Map.of("price", 20, "quantity", 3),
        Integer.class
);

// Spring 原生模板表达式
String message = SpelUtil.evalTemplate(
        "订单 #{#orderNo} 已创建",
        Map.of("orderNo", "A1001")
);

// 方法上下文支持参数名、#p0、#a0、#target、#method 和 #args
String key = SpelUtil.evalMethod(
        "#userId",
        target,
        method,
        arguments,
        String.class
);

// 只读安全模式不允许类型引用、构造器、Bean 引用和任意实例方法
String name = SpelUtil.evalSafe("name", user, String.class);
```

表达式使用有界 LRU 缓存。解析失败与求值失败分别抛出带稳定错误码的
`SpelException`，异常消息不会回显表达式或上下文数据。
普通 `eval` 使用 Spring 完整表达式能力，只适合可信表达式；不可信输入必须使用 `evalSafe`。
模板现在遵循 Spring 原生 `#{...}` 语法；旧版 Map 变量占位符 `#{name}` 需要迁移为
`#{#name}`。

### 14. 分页结果 PageResult

```java
PageResult<User> page = PageResult.of(users, totalCount, 1, 20);
PageResult<User> empty = PageResult.empty(1, 20);

// 类型转换（DO → VO）
PageResult<UserVO> vos = page.map(UserVO::from);

int totalPages = page.getTotalPages();
```

## 配置属性

通用 `JsonCodec` 不依赖 YAML；通过应用 Bean 或
`Fastjson2JsonCodec.builder()` 配置。Starter 创建默认对象 `RedisTemplate` 时，可以收紧
Fastjson2 多态反序列化允许的包名：

```yaml
letool:
  tool:
    redis:
      auto-type-accept-prefixes:
        - org.springframework
        - com.example.order
```

Redis value serializer 会写入类型信息，因此其 allow-list 是独立的安全边界，不会复用通用
`JsonCodec` 的普通序列化策略。包名应尽量精确，不应配置为空字符串或过宽的公共包。
序列化器会自动补齐包分隔符，例如 `com.example` 只允许 `com.example.*`，不会放行
`com.exampleevil.*`；遇到未授权的类型信息会直接拒绝反序列化。

传递依赖的异常模块配置参阅
[`letool-starter-exception`](../letool-starter-exception/README.md)。
