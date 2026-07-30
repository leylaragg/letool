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

**编程式（便捷方法，适合简单场景）**

```java
String result = HttpUtil.get("https://api.example.com/user/123");
String result = HttpUtil.post("https://api.example.com/user", jsonBody);
String result = HttpUtil.put("https://api.example.com/user/1", jsonBody);
String result = HttpUtil.delete("https://api.example.com/user/123");
```

**编程式（链式 API，适合复杂场景）**

```java
HttpResponse resp = HttpUtil.create()
    .url("https://api.example.com/order")
    .post()
    .header("Authorization", "Bearer " + token)
    .contentType("application/json")
    .body(jsonBody)
    .connectTimeout(Duration.ofSeconds(5))
    .readTimeout(Duration.ofSeconds(30))
    .maxRetry(3)
    .execute();

String body = resp.getBody();
int status = resp.getStatusCode();
```

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

### 13. 分页结果 PageResult

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
