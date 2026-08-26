# letool-starter-tool

## 模块简介

`letool-starter-tool` 是 letool 的通用工具模块，为其他模块提供 JSON 序列化、HTTP 请求、
分布式 ID、文件与流、摘要、字符串、集合、业务枚举、常用校验、日期时间、Bean 拷贝、反射访问、类扫描、
Lambda 属性解析、统一响应体、分页模型及 Spring 容器辅助能力。统一异常和国际化由独立的
[`letool-starter-exception`](../letool-starter-exception/README.md) 模块提供；tool 模块依赖该基础模块，
但不再自行维护异常体系。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-tool</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
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

### 树能力迁移到 Data Structure

Tool 模块不再维护重复的 `tool.model.TreeNode` 和 `tool.util.TreeUtil`。需要从平列表构建树、
执行非递归遍历或检测重复 ID、孤儿节点和环时，请单独引入生产化的数据结构模块：

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-data-structure</artifactId>
    <version>${letool.version}</version>
</dependency>
```

业务实体可以直接实现 `io.github.leylaragg.letool.datastructure.tree.TreeNode<T>`：

```java
List<Dept> tree = TreeBuilder.build(deptList);
```

不方便修改业务实体时，使用映射函数创建包装节点：

```java
List<SimpleTreeNode<Dept>> tree = TreeBuilder.buildSimple(
    deptList,
    Dept::getId,
    Dept::getParentId
);
```

旧 Tool API 会静默覆盖重复 ID，并把父节点缺失的数据当作根节点；新 API 默认快速失败。业务确实
允许把孤儿节点提升为根时，应显式传入 `TreeOrphanPolicy.AS_ROOT`。完整用法和迁移契约参见
[`letool-starter-data-structure`](../letool-starter-data-structure/README.md)。

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

通用 codec 会拒绝 Fastjson2 已废弃的 `JSONReader.Feature.SupportAutoType`。Redis 多态值应使用
[`letool-starter-redis`](../letool-starter-redis/README.md) 中带类型白名单的专用序列化器，不要在普通
JSON 入口全局开启 AutoType。

默认实现的技术失败统一抛出 `JsonCodecException`：

| 错误码 | 含义 |
|---|---|
| `TOOL_JSON_001` | JSON 序列化失败 |
| `TOOL_JSON_002` | JSON 反序列化失败 |

异常保留原始 cause，但不会把原始 JSON 放入消息，避免日志泄露敏感数据。

### 4. 文件、流与摘要工具

通用 I/O 能力位于 tool 模块，不绑定 Spring、Servlet 或具体存储协议。`IoUtil` 提供流复制、
有界读取和显式字符集解码；这些方法都不会关闭调用方传入的流，资源生命周期仍由调用方管理：

```java
long copied = IoUtil.copy(input, output);
byte[] content = IoUtil.readBytes(input, 8 * 1024 * 1024L);
String text = IoUtil.readString(input, StandardCharsets.UTF_8, 1024 * 1024L);
```

使用带上限的复制或读取时，超过限制会抛出 `IOException`，不会继续把超限内容写入输出流。
摘要计算和“复制同时计算 SHA-256”由 `DigestUtil` 提供，可避免业务代码重复维护缓冲区循环：

```java
String sha256 = DigestUtil.sha256(path);
DigestCopyResult result = DigestUtil.copyAndSha256(input, output);
boolean matched = DigestUtil.matchesSha256(expectedSha256, result.sha256());
```

`FileUtil.resolveUnderRoot(...)` 用于约束相对路径并拒绝当前已存在的符号链接路径段；
`FileUtil.writeAtomically(...)` 通过同目录临时文件写入；允许替换时优先执行原子移动，禁止替换时
使用非覆盖移动来保证已有目标不被替换，失败时清理临时文件。
前者不能消除并发修改文件系统带来的竞态，涉及不可信目录时仍应配合操作系统权限与隔离策略。
原子移动只保证可见性切换，不等同于将文件和目录强制持久化到磁盘；需要崩溃一致性的状态仓库
应继续显式调用 `FileChannel.force(...)`。

### 5. HTTP 工具 HttpUtil

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

### 6. ID、随机数与编码工具组

#### 6.1 ID 生成 IdUtil

```java
// 默认 Snowflake：适合单实例或已经配置唯一节点号的应用
long id = IdUtil.nextId();
String idStr = IdUtil.nextIdStr();

// 多实例生产环境：显式创建唯一节点组合
IdUtil.Snowflake snowflake = new IdUtil.Snowflake(
    3,
    12,
    Duration.ofMillis(5)
);
long configuredId = snowflake.nextId();

// UUID（32 位无横线）
String uuid = IdUtil.simpleUUID();

// NanoId（默认 21 位，适合短 URL / 文件名）
String nano = IdUtil.nanoId();
String shortNano = IdUtil.nanoId(12);
```

默认静态 Snowflake 会优先读取以下 JVM 参数：

```text
-Dletool.id.worker-id=3 -Dletool.id.datacenter-id=12
```

> 两个参数必须同时配置，范围均为 0 到 31。
> 未配置时工具会根据 PID 和网卡尽力推导，但该模式无法承诺多实例节点号绝不冲突，不能替代生产节点分配。

Snowflake 默认容忍 5 毫秒时钟回拨：容忍范围内使用逻辑时间继续递增，超过范围以
`TOOL_ID_003` 快速失败。NanoId 长度必须位于 1 到 1024 之间。

#### 6.2 安全随机 RandomUtil

```java
int codeNumber = RandomUtil.nextInt(100000, 999999); // 闭区间
long sequence = RandomUtil.nextLong(1L, Long.MAX_VALUE);
String code = RandomUtil.randomCode(6);
String tokenPart = RandomUtil.randomString(32);
String custom = RandomUtil.randomString("ABCDEFGHJKLMNPQRSTUVWXYZ23456789", 8);
```

整数和长整数使用闭区间，浮点数使用左闭右开区间。反向范围、非有限浮点边界、负长度或空字符表
都会抛出稳定的 `RandomOperationException`；零长度字符串返回空字符串。

#### 6.3 Base64 与 Hex 编解码

```java
String standard = Base64Util.encode("Letool");
String text = Base64Util.decode(standard);

// 兼容旧入口：URL 安全编码保留填充字符
String padded = Base64Util.encodeUrlSafe("Letool");
String unpadded = Base64Util.encodeUrlSafeWithoutPadding("Letool");
byte[] bytes = Base64Util.decodeUrlSafeToBytes(unpadded);

String hex = HexUtil.encodeHex("Letool");
String original = HexUtil.decodeHexToStr(hex);
```

Base64 和 Hex 非法文本统一抛出 `EncodingOperationException`，公开消息不会携带原始输入。
`HexUtil` 继续保留 `null -> null` 兼容契约，非空文本必须为偶数长度且只包含十六进制字符。

| 错误码 | 含义 |
|---|---|
| `TOOL_ENCODING_001` | 编码必填参数无效 |
| `TOOL_ENCODING_002` | 标准或 URL 安全 Base64 解码失败 |
| `TOOL_ENCODING_003` | 十六进制文本解码失败 |
| `TOOL_RANDOM_001` | 随机数范围无效 |
| `TOOL_RANDOM_002` | 随机字符串长度无效 |
| `TOOL_RANDOM_003` | 随机字符表无效 |
| `TOOL_ID_001` | ID 参数无效 |
| `TOOL_ID_002` | Snowflake 节点配置无效 |
| `TOOL_ID_003` | Snowflake 时钟回拨超过容忍范围 |
| `TOOL_ID_004` | Snowflake 时间戳超出可用范围 |
| `TOOL_ID_005` | Snowflake 等待下一毫秒时被中断 |

**2.0 迁移说明**

- `Base64Util.encodeUrlSafe(...)` 明确保留填充字符；需要无填充结果时改用 `encodeUrlSafeWithoutPadding(...)`。
- Base64、Hex 非法文本不再泄漏 JDK 异常，统一转换为 `EncodingOperationException`。
- 随机工具不再接受负长度、反向范围、非有限浮点边界和空字符表。
- Snowflake 节点越界、时钟回拨和时间戳越界统一转换为 `IdGenerationException`。
- 多实例生产环境必须显式分配 Snowflake 节点号，不能依赖 PID/MAC 自动推导保证唯一性。

### 7. 字符串工具 StrUtil

```java
StrUtil.isBlank(str);
StrUtil.format("Hello, {}!", "World");     // "Hello, World!"
StrUtil.toCamelCase("user_name");           // "userName"
StrUtil.toSnakeCase("URLValue");            // "url_value"
StrUtil.truncate("long text...", 10);      // "long text..."
StrUtil.join(list, ", ");

// 截取长度按 Unicode 码点计算，不会截断 Emoji 的代理字符
StrUtil.left("A😀B", 2);                    // "A😀"
```

命名转换固定使用 `Locale.ROOT`，不会受土耳其语等系统默认语言环境影响。`truncate` 的负长度、
`join` 和 `split` 的空分隔符会统一抛出 `ValueOperationException`，避免错误配置
被静默转换成不可靠结果。

### 8. 集合工具 CollUtil

```java
CollUtil.isEmpty(list);
List<String> list = CollUtil.newArrayList("a", "b", "c");
List<Integer> lengths = CollUtil.extract(list, String::length);
Map<Long, User> map = CollUtil.toMap(users, User::getId);
Map<Long, String> names = CollUtil.toMap(users, User::getId, User::getName);
List<User> common = CollUtil.intersection(listA, listB);
List<List<User>> chunks = CollUtil.partition(users, 100);
```

`extract`、`toMap`、交集、并集、差集和分片均返回独立的可变快照，空输入也不例外。集合运算
使用首次出现顺序并执行集合语义去重；`toMap` 使用 `LinkedHashMap`，键重复时保留第一个值。
分片大小始终必须大于零，即使源列表为空也会先校验配置。

#### 8.1 业务枚举 EnumUtil

新业务枚举推荐实现轻量的 `CodeEnum<C>` 和 `DescribedEnum` 契约：

```java
enum OrderStatus implements CodeEnum<Integer>, DescribedEnum {
    CREATED(1, "已创建"),
    PAID(2, "已支付");

    private final Integer code;
    private final String description;

    // 构造器与 Getter 省略
}

Optional<OrderStatus> status = EnumUtil.findByCode(OrderStatus.class, 1);
OrderStatus required = EnumUtil.requireByCode(OrderStatus.class, 1);
Map<String, Object> options = EnumUtil.toMap(OrderStatus.class);
// {"已创建": 1, "已支付": 2}
```

历史枚举无需立即改造：`getByCode` 和 `getBy` 仍支持 JavaBean Getter 或同名私有字段，并在未命中时
返回 `null`。属性元数据使用 `ClassValue` 按枚举类型缓存，不保存枚举实例；属性不存在或读取失败
不再被静默吞掉。新代码优先使用返回 `Optional` 的 `findByName`、`findByCode` 和 `findBy`，必须命中
编码时使用 `requireByCode`。`toMap` 保持枚举声明顺序，重复描述会直接拒绝，避免选项被覆盖。

#### 8.2 常用校验 ValidatorUtil

```java
ValidatorUtil.isPhone("13812345678");
ValidatorUtil.isEmail("user.name+tag@example.com");
ValidatorUtil.isUrl("https://example.com:8443/orders/1");
ValidatorUtil.isIdCard("11010519491231002X");
ValidatorUtil.isIpV4("192.168.1.1");
```

身份证校验包含严格出生日期和 GB 11643 校验位；URL 仅接受具有合法主机、端口的 HTTP/HTTPS
地址；邮箱校验覆盖常见 ASCII 地址、长度和连续点边界；IPv4 只接受无多余前导零的规范十进制分段。
这些方法负责结构校验，不连接邮箱、DNS 或身份证权威数据源验证输入真实性。动态正则语法错误时
`matches` 返回 `false`，适合处理来自配置中心或规则平台的表达式。

基础值工具稳定错误码如下：

| 错误码 | 含义 |
|---|---|
| `TOOL_VALUE_001` | 字符串、集合或枚举参数不符合方法契约 |
| `TOOL_VALUE_002` | 枚举属性不存在或读取失败 |
| `TOOL_VALUE_003` | 严格枚举编码查询未命中 |
| `TOOL_VALUE_004` | 枚举描述重复，无法安全生成选项映射 |

**2.0 迁移说明**

- `StrUtil` 的截取长度按 Unicode 码点计算；负截断长度和非法分隔符不再产生底层异常或模糊结果。
- `CollUtil` 的转换与集合运算统一返回可变快照；依赖空结果不可修改的代码应自行使用
  `List.copyOf(...)` 或 `Map.copyOf(...)` 固化结果。
- `intersection` 改为按第一个集合首次出现顺序输出去重交集；依赖哈希遍历顺序的代码必须调整。
- `partition` 对空列表同样校验分片大小，非法配置统一抛出 `ValueOperationException`。
- `EnumUtil.getBy*` 未命中仍返回 `null`，但属性缺失或访问失败会抛出稳定异常；新代码优先迁移到
  `findBy*` 或 `requireByCode`。
- 身份证、URL、邮箱和 IPv4 校验规则已经收紧，旧版仅通过正则外形的无效输入现在会返回 `false`。

### 9. 日期工具 DateUtil

日期工具直接封装 JDK 17 `java.time` 的高频组合，不引入额外日期框架。公共格式器不可变且线程安全；
严格入口不会再把空值转换为 `null` 或 `0`，需要容错时应显式使用 `tryParse`。

```java
// 格式化与严格解析
String dateText = DateUtil.formatDate(LocalDate.of(2025, 1, 15));
LocalDate date = DateUtil.parseDate("2025-01-15");
LocalDateTime dateTime = DateUtil.parseDateTime("2025-01-15 14:30:00");

// 外部输入不合法时不抛异常
Optional<LocalDate> optionalDate = DateUtil.tryParseDate(request.getDate());

// 固定业务时钟，测试和定时业务不会依赖机器当前时间
Clock businessClock = Clock.system(ZoneId.of("Asia/Shanghai"));
LocalDate businessDate = DateUtil.today(businessClock);

// 显式时区转换
Instant instant = DateUtil.toInstant(dateTime, ZoneId.of("Asia/Shanghai"));
long epochMilli = DateUtil.toEpochMilli(dateTime, ZoneId.of("Asia/Shanghai"));

// 数据库查询使用左闭右开范围：[当天开始, 次日开始)
DateTimeRange range = DateUtil.dayRange(businessDate);
query.ge("created_at", range.startInclusive())
     .lt("created_at", range.endExclusive());
```

当前字段提供无参入口和 `Clock` 重载。月份整数采用自然月份 `1-12`；需要类型安全语义时可以直接
获取 JDK `Month` 枚举：

```java
int year = DateUtil.currentYear();
int month = DateUtil.currentMonth();
Month monthEnum = DateUtil.currentMonthEnum();

Clock businessClock = Clock.system(ZoneId.of("Asia/Shanghai"));
String currentText = DateUtil.nowText(businessClock);
int businessHour = DateUtil.currentHour(businessClock);
```

自定义格式支持常用 `yyyy` 和 JDK 推荐的 `uuuu`，但始终严格校验真实日期，不会把
`2025-02-30` 自动修正到三月：

```java
String custom = DateUtil.format(
        LocalDateTime.of(2025, 1, 15, 14, 30),
        "yyyy/MM/dd HH:mm"
);
LocalDate parsed = DateUtil.parseDate("2025/01/15", "yyyy/MM/dd");
LocalTime time = DateUtil.parseTime("14点30分", "HH点mm分");

// 高频固定格式只创建一次，DateTimeFormatter 不可变且线程安全
DateTimeFormatter formatter = DateUtil.formatter("yyyyMMddHHmmss");
String reused = DateUtil.format(DateUtil.now(), formatter);
```

旧版 `Date` 表示绝对时刻，格式化或提取字段时应在跨系统场景显式提供时区：

```java
String legacyText = DateUtil.format(
        legacyDate,
        "yyyy-MM-dd HH:mm:ss XXX",
        ZoneId.of("Asia/Shanghai")
);
int legacyDay = DateUtil.day(legacyDate, ZoneId.of("Asia/Shanghai"));
```

`parseDate`、`parseDateTime` 和 `parseTime` 不会自动猜测输入格式。外部输入需要容错时使用对应的
`tryParse` 方法；格式来自配置时应先调用 `formatter(pattern)` 校验，并复用返回结果。

带时区的日边界使用真实时区规则计算，不会把一天固定视为 24 小时：

```java
ZoneId zoneId = ZoneId.of("America/New_York");
ZonedDateTime start = DateUtil.startOfDay(date, zoneId);
ZonedDateTime endExclusive = DateUtil.startOfNextDay(date, zoneId);
```

稳定错误码如下：

| 错误码 | 含义 |
|---|---|
| `TOOL_DATE_001` | 必填日期时间参数无效 |
| `TOOL_DATE_002` | 日期时间严格解析失败 |
| `TOOL_DATE_003` | 日期时间格式化失败 |
| `TOOL_DATE_004` | 日期时间或时区转换失败 |

**2.0 迁移说明**

- 必填参数为空时不再返回 `null` 或 `0`，统一抛出 `DateOperationException`。
- 标准格式器使用 `uuuu` 和严格公历解析，`2024-02-30` 等非法日期不会被自动修正。
- `endOfDay(date)` 现在返回 `23:59:59.999999999`；范围查询应迁移到 `dayRange(date)` 或
  `[startOfDay(date), startOfNextDay(date))`。
- 无时区的 Date、时间戳转换仍使用系统默认时区；跨系统业务应迁移到带 `ZoneId` 的重载。

### 10. Bean 与反射工具组

#### BeanUtil

```java
// 基于 Spring BeanUtils 的标准属性拷贝
UserVO vo = BeanUtil.copy(user, UserVO.class);

// 忽略主键和审计属性
UserVO safe = BeanUtil.copy(user, UserVO.class, "id", "createdAt");

// 无默认构造器或受控对象通过 Supplier 创建
UserVO supplied = BeanUtil.copy(user, () -> new UserVO(defaultStatus));

// 批量拷贝保持输入顺序和 null 元素位置，返回可修改的新列表
List<UserVO> vos = BeanUtil.copyList(users, UserVO.class);

// Map 互转
Map<String, Object> map = BeanUtil.toMap(user);
User user = BeanUtil.toBean(map, User.class);
```

`copyFast` 和 `copyListFast` 已标记废弃并委托标准拷贝路径，不再维护可能持有类加载器的 CGLIB
`BeanCopier` 缓存。高频且类型固定的大规模映射应使用 MapStruct 等编译期映射方案。

#### ReflectUtil

```java
// 预期字段可能不存在时使用 Optional
Optional<Field> field = ReflectUtil.findField(User.class, "nickname");

// 业务要求字段必须存在时使用严格入口
Field idField = ReflectUtil.requireField(User.class, "id");
ReflectUtil.setFieldValue(user, "nickname", "小李");
String nickname = ReflectUtil.getFieldValue(user, "nickname");

// 自动处理基本类型包装、父类型参数和唯一的最接近重载
String result = ReflectUtil.invokeMethod(service, "handle", request);

// null 参数或重载存在歧义时，先显式选择方法再调用
Method method = ReflectUtil.requireMethod(Service.class, "handle", String.class);
String explicit = ReflectUtil.invokeMethod(service, method, new Object[]{null});

// 从指定泛型接口解析类型
Class<?> entityType = ReflectUtil.resolveTypeArgument(
        UserRepository.class,
        Repository.class,
        0
).orElse(Object.class);
```

类、字段和方法注解查询使用 Spring 合并注解语义，能够识别组合注解和元注解。查询型接口使用
`Optional` 表达正常缺失；写入、调用和 `requireXxx` 等命令型接口遇到空目标或缺失成员时快速失败。

#### ClassUtil

```java
// 只读取 ASM 元数据，不加载候选类
List<String> classNames = ClassUtil.scanClassNames("com.example.handler");

// 加载类型定义，但不会执行静态初始化块
List<Class<?>> classes = ClassUtil.scan("com.example.handler");

// 注解扫描支持元注解，接口扫描只返回具体实现类
List<Class<?>> components = ClassUtil.scanByAnnotation(
        "com.example.handler",
        Component.class
);
List<Class<? extends DataHandler>> handlers = ClassUtil.scanByInterface(
        "com.example.handler",
        DataHandler.class
);
```

所有扫描结果按类名去重并稳定排序。插件、热部署或容器环境应使用带 `ClassLoader` 的重载，确保资源
扫描和类型加载使用同一类加载器。可读候选的元数据解析失败或匹配类型无法加载时会快速失败，不会
返回难以判断完整性的部分结果。

#### LambdaUtil

```java
String name = LambdaUtil.getPropertyName(User::getName);
String active = LambdaUtil.getPropertyName(User::isActive);
String recordName = LambdaUtil.getPropertyName(UserRecord::name);
```

仅支持标准 `getXxx()`、返回 primitive `boolean` 的 `isXxx()` 和 record 组件访问器。普通 Lambda、
静态方法及任意业务方法不会被猜测为属性。工具只缓存 Lambda 类的 `writeReplace` 反射入口，不缓存
`SerializedLambda` 或捕获参数。

稳定错误码如下：

| 错误码 | 含义 |
|---|---|
| `TOOL_REFLECTION_001` | 必填参数或调用契约无效 |
| `TOOL_REFLECTION_002` | 字段、方法不存在或重载存在歧义 |
| `TOOL_REFLECTION_003` | 字段或 Bean 属性访问失败 |
| `TOOL_REFLECTION_004` | 目标方法调用失败 |
| `TOOL_REFLECTION_005` | Bean 或目标类型实例化失败 |
| `TOOL_REFLECTION_006` | 类路径扫描或候选类加载失败 |
| `TOOL_REFLECTION_007` | Lambda 属性解析失败 |

**2.0 迁移说明**

- `copyFast`、`copyListFast` 已废弃；迁移到标准拷贝，性能敏感场景迁移到编译期映射方案。
- 命令型反射操作不再对空目标静默返回，统一抛出 `ReflectionOperationException`。
- `invokeMethod` 现在使用 Spring 类型匹配并拒绝同权重歧义；歧义调用需要显式传入 `Method`。
- Class 扫描结果改为不可修改、稳定排序的完整快照，且不再触发候选类静态初始化。
- 非 Getter Lambda 不再返回错误字符串，而是通过稳定错误码明确拒绝。

### 11. Spring 容器工具 SpringUtil

```java
UserService service = SpringUtil.getBean(UserService.class);
UserService service = SpringUtil.getBean("userService", UserService.class);
String appName = SpringUtil.getProperty("spring.application.name");
int port = SpringUtil.getProperty("server.port", Integer.class);
Map<String, DataHandler> handlers = SpringUtil.getBeansOfType(DataHandler.class);
boolean exists = SpringUtil.containsBean("dataSource");
String profile = SpringUtil.getActiveProfile();  // "dev" / "prod"
```

### 12. Spring 表达式工具 SpelUtil

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

### 13. 重试工具 RetryUtil

`RetryUtil` 使用 Resilience4j Retry 执行同步重试，Letool 负责提供类型安全的策略、便捷入口、
参数校验和统一异常。常用的固定等待可以直接调用：

```java
String result = RetryUtil.retry(
        () -> remoteClient.query(orderNo),
        3,
        Duration.ofMillis(200),
        throwable -> throwable instanceof IOException
);
```

`maxAttempts` 包含首次调用。上例最多执行三次任务，而不是首次调用后再重试三次。
需要指数退避、随机抖动或按结果重试时，可以创建不可变策略：

```java
RetryPolicy<JobStatus> policy = RetryPolicy.<JobStatus>builder()
        .maxAttempts(5)
        .exponentialRandomBackoff(
                Duration.ofMillis(100),
                2.0,
                0.5,
                Duration.ofSeconds(2))
        .retryOnException(throwable -> throwable instanceof IOException)
        .retryOnResult(status -> status == JobStatus.RUNNING)
        .build();

JobStatus status = RetryUtil.execute(
        () -> jobClient.queryStatus(jobId),
        policy
);
```

也可以使用 `retryExponential(...)` 和 `retryByResult(...)` 完成常见场景。线程中断或显式取消
不会被重试，并且工具会恢复当前线程的中断标记。工具只控制尝试次数和两次调用之间的等待，
不会为单次任务创建额外线程或强制超时；HTTP、数据库等客户端仍需配置自己的连接与读取超时。

稳定错误码如下：

| 错误码 | 含义 |
|---|---|
| `TOOL_RETRY_001` | 重试参数无效 |
| `TOOL_RETRY_002` | 任务发生不可重试的执行失败 |
| `TOOL_RETRY_003` | 最大尝试次数耗尽 |
| `TOOL_RETRY_004` | 任务执行或退避等待被中断 |

**2.0 迁移说明**

- 旧版 `maxRetries` 表示首次调用后的额外重试次数；新版 `maxAttempts` 表示包含首次调用的总次数。
- 毫秒数参数已替换为 `Duration`，避免单位歧义和负数等待时间。
- 指数退避必须提供最大等待上限，避免等待时间无限增长；随机抖动通过
  `RetryPolicy.exponentialRandomBackoff(...)` 配置。
- 失败时不再抛出无差别的 `RuntimeException`，统一抛出保留原始原因链的
  `RetryOperationException`。
- 工具不再记录可能包含业务数据的任务结果和异常消息。需要观测时，应由业务层按脱敏规则记录。
- Hutool 已从本模块依赖中移除；业务若直接使用 Hutool API，需要在业务项目中显式声明依赖。

### 14. 分页结果 PageResult

```java
PageResult<User> page = PageResult.of(users, totalCount, 1, 20);
PageResult<User> empty = PageResult.empty(1, 20);

// 类型转换（DO → VO）
PageResult<UserVO> vos = page.map(UserVO::from);

int totalPages = page.getTotalPages();
```

## 配置属性

通用 `JsonCodec` 不依赖 YAML，通过应用 Bean 或 `Fastjson2JsonCodec.builder()` 配置。
Redis 连接、对象序列化和 `RedisFacade` 配置已经迁至
[`letool-starter-redis`](../letool-starter-redis/README.md)。

传递依赖的异常模块配置参阅
[`letool-starter-exception`](../letool-starter-exception/README.md)。
