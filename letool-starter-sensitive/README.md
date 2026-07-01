# letool-starter-sensitive

## 模块简介

`letool-starter-sensitive` 是企业级数据脱敏模块，提供**注解驱动**与**编程式**两种脱敏方式，覆盖 Jackson JSON 序列化、日志输出、HTTP 响应等场景。内置 19 种脱敏策略，支持手机号、身份证、银行卡、邮箱、地址等常见敏感数据类型，同时支持自定义正则脱敏和策略扩展。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-sensitive</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-sensitive</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

### 2. 注解方式：字段上添加 @Sensitive

```java
public class UserVO {
    private Long id;

    @Sensitive(type = SensitiveType.PHONE)
    private String phone;              // "13812345678" → "138****5678"

    @Sensitive(type = SensitiveType.ID_CARD, keepPrefix = 6, keepSuffix = 4)
    private String idCard;             // "320123199001011234" → "320123********1234"

    @Sensitive(type = SensitiveType.EMAIL)
    private String email;              // "test@example.com" → "t***@example.com"

    @Sensitive(type = SensitiveType.NAME)
    private String name;               // "张三" → "张*"

    // getter / setter ...
}
```

Controller 返回 UserVO 时，Jackson 序列化自动执行脱敏。

### 3. 编程方式：调用 SensitiveUtil

```java
// 按类型脱敏（使用默认参数）
String masked = SensitiveUtil.mask("13812345678", SensitiveType.PHONE);
// → "138****5678"

// 自定义遮盖规则
MaskContext ctx = new MaskContext()
    .withKeepPrefix(2)
    .withKeepSuffix(2)
    .withMaskChar('#');
String masked = SensitiveUtil.mask("13812345678", SensitiveType.PHONE, ctx);
// → "13######78"

// 反射扫描对象
UserVO maskedUser = SensitiveUtil.mask(user);
```

## 核心 API 示例

### 1. 注解声明式：@Sensitive

```java
public class OrderVO {
    // 内置类型，使用策略默认规则
    @Sensitive(type = SensitiveType.PHONE)
    private String contactPhone;       // "13812345678" → "138****5678"

    @Sensitive(type = SensitiveType.BANK_CARD)
    private String bankCard;           // "6222021234567890" → "6222****7890"

    @Sensitive(type = SensitiveType.ID_CARD)
    private String idCard;             // "320123199001011234" → "3201**********1234"

    @Sensitive(type = SensitiveType.ADDRESS)
    private String address;            // "北京市海淀区中关村大街1号" → "北京市海淀区****"

    @Sensitive(type = SensitiveType.PASSWORD)
    private String password;           // "abc123" → "********"

    @Sensitive(type = SensitiveType.CAR_LICENSE)
    private String carLicense;         // "京A12345" → "京A****5"

    // 自定义正则：匹配 "工号" 后面的 4 位数字
    @Sensitive(type = SensitiveType.CUSTOM,
               pattern = "(?<=工号)\\d{4}",
               replacement = "****")
    private String employeeId;         // "工号123456" → "工号****56"

    // 覆盖策略默认保留长度
    @Sensitive(type = SensitiveType.ID_CARD, keepPrefix = 6, keepSuffix = 4)
    private String idCardV2;           // 保留前 6 位地区码 + 后 4 位

    // 自定义遮盖字符
    @Sensitive(type = SensitiveType.BANK_CARD, maskChar = '#')
    private String bankCardV2;         // "6222####7890"
}
```

**生效时机**：
- **Jackson 序列化**：Controller 返回 JSON 时自动拦截脱敏
- **日志输出**：日志打印对象时脱敏字段被遮盖
- **编程式调用**：`SensitiveProcessor#mask(Object)` 反射扫描

### 2. 编程式：SensitiveUtil

```java
// 单值脱敏（使用策略默认参数）
SensitiveUtil.mask("13812345678", SensitiveType.PHONE);     // "138****5678"
SensitiveUtil.mask("test@example.com", SensitiveType.EMAIL); // "t***@example.com"
SensitiveUtil.mask("6222021234567890", SensitiveType.BANK_CARD); // "6222****7890"
SensitiveUtil.mask("320123199001011234", SensitiveType.ID_CARD); // "3201**********1234"

// 单值脱敏（自定义 MaskContext）
MaskContext ctx = new MaskContext()
    .withKeepPrefix(2)
    .withKeepSuffix(2)
    .withMaskChar('#');
SensitiveUtil.mask("13812345678", SensitiveType.PHONE, ctx); // "13######78"

// 对象脱敏（反射扫描所有 @Sensitive 字段）
UserVO maskedUser = SensitiveUtil.mask(user);
```

### 3. 编程式：SensitiveProcessor 底层 API

```java
// 按注解实例脱敏（Jackson 序列化器内部使用）
Sensitive annotation = field.getAnnotation(Sensitive.class);
String masked = SensitiveProcessor.mask(value, annotation);

// 按类型 + 默认参数脱敏
String masked = SensitiveProcessor.mask(value, SensitiveType.PHONE);

// 按类型 + 自定义 Context 脱敏
String masked = SensitiveProcessor.mask(value, SensitiveType.PHONE, context);

// 反射扫描对象
T masked = SensitiveProcessor.mask(object);

// 注册/覆盖自定义策略
SensitiveProcessor.register(SensitiveType.CUSTOM, new MyCustomStrategy());

// 获取已注册策略
SensitiveStrategy<MaskContext> strategy = SensitiveProcessor.getStrategy(SensitiveType.PHONE);
```

### 4. 内置脱敏类型一览

| 分类 | 类型 | 示例输入 | 示例输出 |
|------|------|---------|---------|
| 个人信息 | `PHONE` | 13812345678 | 138\*\*\*\*5678 |
| 个人信息 | `ID_CARD` | 320123199001011234 | 3201\*\*\*\*\*\*\*\*\*\*1234 |
| 个人信息 | `NAME` | 张三 | 张\* |
| 个人信息 | `EMAIL` | test@example.com | t\*\*\*@example.com |
| 个人信息 | `ADDRESS` | 北京市海淀区中关村大街1号 | 北京市海淀区\*\*\*\* |
| 个人信息 | `PASSWORD` | abc123 | \*\*\*\*\*\*\*\* |
| 金融 | `BANK_CARD` | 6222021234567890 | 6222\*\*\*\*7890 |
| 交通 | `CAR_LICENSE` | 京A12345 | 京A\*\*\*\*5 |
| 通讯 | `FIXED_PHONE` | 010-12345678 | 010-\*\*\*\*5678 |
| 网络 | `IPV4` | 192.168.1.1 | 192.168.\*.\* |
| 网络 | `IPV6` | 2001:db8::1 | 2001:\*\*\*\*:\*\*\*\*::1 |
| 社交 | `WECHAT` | wxid12388 | w\*\*\*\*88 |
| 社交 | `QQ` | 1234567890 | 12\*\*\*\*90 |
| 证件 | `PASSPORT` | E12345678 | E\*\*\*\*1234 |
| 位置 | `POSITION` | 39.9042,116.4074 | 39.9\*\*\*,116.3\*\*\* |
| 扩展 | `CUSTOM` | 配合 pattern/replacement 使用 | 自定义正则替换 |
| 扩展 | `KEEP_LENGTH` | 按比例遮盖 | 保留首尾 |
| 扩展 | `TAIL_DISPLAY` | 仅展示尾部 | 用于支付尾号 |

### 5. MaskContext 配置详解

```java
// 使用链式 Builder
MaskContext ctx = new MaskContext()
    .withKeepPrefix(3)        // 保留前 3 位
    .withKeepSuffix(4)        // 保留后 4 位
    .withMaskChar('#')        // 遮盖字符
    .withPattern("\\d{4}")    // 自定义正则（仅 CUSTOM 类型时生效）
    .withReplacement("****"); // 替换字符串（仅 CUSTOM 类型时生效）

// 从 @Sensitive 注解提取配置
MaskContext ctx = MaskContext.from(annotation);

// 使用默认配置单例
MaskContext ctx = MaskContext.DEFAULT;
```

## 配置属性

```yaml
letool:
  sensitive:
    # 无需额外配置，开箱即用
    # @Sensitive 注解添加到字段即可自动生效
    # 如需禁用某策略可编程式覆盖：
    # SensitiveProcessor.register(SensitiveType.XXX, new CustomStrategy())
```
