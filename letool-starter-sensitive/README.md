# letool-starter-sensitive

`letool-starter-sensitive` 是轻量的数据脱敏工具，提供常用单值脱敏策略、
`@Sensitive` 字段注解和 Spring Boot Jackson 自动配置。模块只封装开发中高频、
边界明确的脱敏能力，不接管日志框架、用户权限或数据库读写。

## 功能边界

- 内置手机号、身份证、姓名、邮箱、银行卡、地址、密码、IP 等 19 种策略。
- `SensitiveUtil` 提供无需 Spring 容器的单值脱敏方法。
- Spring Boot 自动提供 `SensitiveProcessor`；类路径存在 Jackson 时自动脱敏 Controller 响应字段。
- `SensitiveStrategyRegistry` 支持用户覆盖任意内置策略。
- Jackson 只接管带 `@Sensitive` 的字符串字段，不覆盖用户的全局字符串序列化器。
- 策略失败、配置缺失或不支持的字段类型会抛出结构化异常，不回退返回明文。

本模块不提供日志文本猜测式脱敏，也不提供基于角色、请求头或 URL 的动态放行。
日志中的敏感值应在写入日志前显式调用 `SensitiveUtil`，或在业务日志组件中使用结构化字段方案。

## 引入依赖

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-sensitive</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

核心单值脱敏不依赖 Jackson。`jackson-databind` 在本模块中是可选的 `provided`
依赖；字段自动脱敏要求应用类路径中存在 Jackson，常规 Spring Boot Web 项目通常已由
Web Starter 传递引入。

## Jackson 字段脱敏

```java
public class UserView {

    private String nickname;

    @Sensitive(type = SensitiveType.PHONE)
    private String phone;

    @Sensitive(type = SensitiveType.ID_CARD, keepPrefix = 6, keepSuffix = 4)
    private String idCard;

    @Sensitive(type = SensitiveType.EMAIL)
    private String email;

    // getter / setter
}
```

应用类路径存在 Jackson 时，Controller 返回 `UserView` 会使用自动注册的
`SensitiveModule`：

```json
{
  "nickname": "公开昵称",
  "phone": "138****5678",
  "idCard": "320123********1234",
  "email": "t***@example.com"
}
```

`@Sensitive` 只能标注 `String` 字段。普通字符串仍使用应用自己的 Jackson
序列化配置；如果字段格式异常或长度不足以安全保留首尾，策略会完整遮盖该值。

## 编程式单值脱敏

```java
String phone = SensitiveUtil.mask("13812345678", SensitiveType.PHONE);
// 138****5678

MaskContext context = MaskContext.DEFAULT
        .withKeepPrefix(2)
        .withKeepSuffix(2)
        .withMaskChar('#');
String customized = SensitiveUtil.mask("13812345678", SensitiveType.PHONE, context);
// 13#######78
```

`MaskContext` 是不可变对象。链式方法会返回新上下文，不会修改共享的
`MaskContext.DEFAULT`，可以安全地在并发环境中复用。

## 自定义正则脱敏

```java
MaskContext context = MaskContext.DEFAULT
        .withPattern("(?<=工号)\\d{4}")
        .withReplacement("****");

String employeeId = SensitiveUtil.mask(
        "工号123456",
        SensitiveType.CUSTOM,
        context);
// 工号****56
```

使用 `CUSTOM` 类型时必须配置 `pattern`。表达式缺失会抛出
`SensitiveException`；表达式没有匹配到内容时会完整遮盖原值，避免误配置导致明文泄露。

## 覆盖内置策略

Spring 应用可以声明自己的不可变注册表。构建器已预装全部内置策略，用户只需覆盖目标类型：

```java
@Configuration(proxyBeanMethods = false)
public class SensitiveConfiguration {

    /**
     * 覆盖手机号脱敏规则。
     *
     * @return 用户自定义策略注册表
     */
    @Bean
    public SensitiveStrategyRegistry sensitiveStrategyRegistry() {
        return SensitiveStrategyRegistry.builder()
                .register(SensitiveType.PHONE, (value, context) ->
                        "***-***-" + value.substring(value.length() - 4))
                .build();
    }
}
```

自动配置创建的 `SensitiveProcessor` 和 `SensitiveModule` 会共同使用该注册表：

```java
@Service
public class CustomerService {

    private final SensitiveProcessor sensitiveProcessor;

    /**
     * 创建客户服务。
     *
     * @param sensitiveProcessor 脱敏处理器
     */
    public CustomerService(SensitiveProcessor sensitiveProcessor) {
        this.sensitiveProcessor = sensitiveProcessor;
    }

    /**
     * 脱敏手机号。
     *
     * @param phone 原始手机号
     * @return 脱敏手机号
     */
    public String maskPhone(String phone) {
        return sensitiveProcessor.mask(phone, SensitiveType.PHONE);
    }
}
```

非 Spring 环境也可以显式创建处理器：

```java
SensitiveStrategyRegistry registry = SensitiveStrategyRegistry.builder()
        .register(SensitiveType.CUSTOM, customStrategy)
        .build();
SensitiveProcessor processor = new SensitiveProcessor(registry);
```

## 内置类型

| 分类 | 类型 | 示例 |
|------|------|------|
| 个人信息 | `PHONE` | `13812345678` → `138****5678` |
| 个人信息 | `ID_CARD` | `320123199001011234` → `3201**********1234` |
| 个人信息 | `NAME` | `张三` → `张*` |
| 个人信息 | `EMAIL` | `test@example.com` → `t***@example.com` |
| 个人信息 | `ADDRESS` | 地址后半段遮盖 |
| 认证信息 | `PASSWORD` | 最多输出 8 个遮盖字符 |
| 金融 | `BANK_CARD` | 保留前 4 位和后 4 位 |
| 交通 | `CAR_LICENSE` | 保留省市代码和末位 |
| 通讯 | `FIXED_PHONE` | 保留区号和后 4 位 |
| 网络 | `IPV4` | `192.168.1.1` → `192.168.*.*` |
| 网络 | `IPV6` | 保留指定前段和末段 |
| 社交 | `WECHAT`、`QQ` | 保留有限首尾字符 |
| 证件 | `PASSPORT`、`DOM` | 保留证件类型和末 4 位 |
| 位置 | `POSITION` | 保留整数和首位小数 |
| 扩展 | `CUSTOM` | 正则匹配并替换 |
| 扩展 | `KEEP_LENGTH` | 自定义首尾保留长度 |
| 扩展 | `TAIL_DISPLAY` | 只显示指定尾部长度 |

## 配置

```yaml
letool:
  sensitive:
    enabled: true
    jackson:
      enabled: true
```

- `letool.sensitive.enabled=false`：不创建注册表、处理器和 Jackson 模块。
- `letool.sensitive.jackson.enabled=false`：保留编程式处理器，仅关闭 Jackson 自动脱敏。
- 用户声明 `SensitiveStrategyRegistry`、`SensitiveProcessor` 或 `SensitiveModule` Bean 时，默认 Bean 自动退让。

## 异常

模块异常统一继承 `SystemException`：

| 错误码 | 含义 |
|--------|------|
| `SENSITIVE_001` | 脱敏配置无效 |
| `SENSITIVE_002` | 策略不存在 |
| `SENSITIVE_003` | 策略执行失败 |

异常消息不会携带待脱敏明文。对象级脱敏请直接使用 Jackson 序列化结果，模块不再提供
依赖无参构造器和反射复制的 `mask(Object)` API。
