# letool-starter-exception

`letool-starter-exception` 提供统一错误码、业务/系统异常、基于 Spring
`MessageSource` 的国际化消息解析，以及相应的 Spring Boot 自动配置。异常在创建时保存稳定的默认消息，
Web 层可以在生成响应时再按请求 Locale 解析展示文案。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-exception</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 定义错误码

业务模块推荐用枚举集中管理错误码。错误码必须稳定且非空，默认消息使用
`MessageFormat` 的 `{0}`、`{1}` 占位符：

```java
import com.github.leyland.letool.exception.code.ErrorCode;

public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND("ORDER_001", "订单不存在：{0}"),
    ORDER_PAID("ORDER_002", "订单已支付");

    private final String code;
    private final String defaultMessage;

    OrderErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }
}
```

临时或非常小的场景也可以直接创建错误码：

```java
ErrorCode errorCode = ErrorCode.of("PAY_001", "支付渠道不可用");
```

## 抛出异常

```java
import com.github.leyland.letool.exception.core.BusinessException;
import com.github.leyland.letool.exception.core.SystemException;

// 可国际化的业务异常；orderId 会用于默认消息和本地化消息的占位符。
throw BusinessException.of(OrderErrorCode.ORDER_NOT_FOUND, orderId);

// 明确使用固定文案时调用 custom；该文案不会再参与国际化解析。
throw BusinessException.custom(OrderErrorCode.ORDER_PAID, "订单已关闭，不能再次支付");

// 系统异常保留底层 cause，便于日志输出完整堆栈和因果链。
throw SystemException.causedBy(
        ErrorCode.of("PAY_SYS_001", "支付渠道调用失败"),
        cause);
```

`BusinessException` 表示可预期的业务拒绝；`SystemException` 表示基础设施或其他技术故障。
在 `letool-starter-web` 中，前者映射为 HTTP 400，后者映射为 HTTP 500。

## 国际化消息

starter 自带的公共消息位于
`classpath:i18n/letool-exception/messages*.properties`。应用可以在
`src/main/resources` 下提供自己的 `messages` 资源：

`messages.properties`

```properties
ORDER_001=订单不存在：{0}
ORDER_002=订单已支付
```

`messages_zh_CN.properties`

```properties
ORDER_001=订单不存在：{0}
ORDER_002=订单已支付
```

`messages_en.properties`

```properties
ORDER_001=Order not found: {0}
ORDER_002=The order has already been paid
```

解析顺序为：

1. 应用自己的 `MessageSource`；
2. starter 和 `MessageBundleContributor` 提供的资源；
3. `ErrorCode#getDefaultMessage()`。

因此，应用只需在自己的 `messages_zh_CN.properties` 中声明同名编码，就可以覆盖 starter 文案。
例如，下面的配置会把 starter 的 `ARG_001` 中文消息替换为应用文案，而无需替换
`MessageResolver`：

```properties
ARG_001=请求参数错误：{0}
```

`BusinessException.custom(...)` 是显式固定文案，解析时会跳过上述资源查找。

## 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `letool.exception.enabled` | `true` | 是否启用异常模块自动配置 |
| `letool.exception.i18n.enabled` | `true` | 是否从应用和 starter 消息资源解析错误码 |
| `letool.exception.i18n.default-locale` | `zh_CN` | 没有显式 Locale 或请求 Locale 时使用的语言 |
| `letool.exception.i18n.fallback-to-system-locale` | `false` | starter 消息资源未命中时，是否回退到 JVM 默认 Locale |

```yaml
letool:
  exception:
    enabled: true
    i18n:
      enabled: true
      default-locale: zh_CN
      fallback-to-system-locale: false
```

应用声明自己的 `MessageResolver` Bean 时，自动配置会退让。

## 日志与 HTTP 响应

异常的 `getMessage()` 在构造时固定为 `[CODE] fallback`，不依赖当前请求或 Spring
上下文。因此，标准的 Throwable 日志写法可以输出错误码、稳定默认消息、堆栈和 cause：

```java
try {
    paymentClient.pay(command);
} catch (RuntimeException cause) {
    SystemException exception = SystemException.causedBy(
            ErrorCode.of("PAY_SYS_001", "支付渠道调用失败"),
            cause);
    log.error("调用后端失败", exception);
    throw exception;
}
```

日志中的异常消息类似 `[PAY_SYS_001] 支付渠道调用失败`。`letool-starter-web` 生成 HTTP
响应时使用 `MessageResolver` 按请求 Locale 返回展示文案，不返回异常 cause、堆栈或其他底层细节。

## 扩展消息资源

其他 starter 或业务模块可以声明 `MessageBundleContributor`，把独立资源包加入统一解析链：

```java
import com.github.leyland.letool.exception.message.MessageBundleContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OrderExceptionConfiguration {

    @Bean
    MessageBundleContributor orderExceptionMessages() {
        // 对应 classpath:i18n/order/messages*.properties
        return MessageBundleContributor.of("i18n/order/messages");
    }
}
```

资源 basename 不带语言后缀和 `.properties` 扩展名。多个贡献者按注入顺序参与 starter
资源查找；应用的 `MessageSource` 仍然拥有最高优先级。

## 从旧异常类迁移

`com.github.leyland.letool.tool.exception.LetoolException` 已删除，并且不提供兼容层。
旧代码需要根据异常语义迁移到新包和工厂方法：

| 旧代码 | 新代码 |
|--------|--------|
| `new BusinessException(code, message)` | `BusinessException.of(ErrorCode.of(code, message))` |
| `new BusinessException(code, message, cause)` | `BusinessException.causedBy(ErrorCode.of(code, message), cause)` |
| `new SystemException(code, message)` | `SystemException.of(ErrorCode.of(code, message))` |
| `new SystemException(code, message, cause)` | `SystemException.causedBy(ErrorCode.of(code, message), cause)` |
| 继承 `LetoolException` | 按语义继承 `BusinessException`、`SystemException`，或直接继承抽象的 `BaseException` |

对应的新包为：

```java
com.github.leyland.letool.exception.code.ErrorCode
com.github.leyland.letool.exception.core.BaseException
com.github.leyland.letool.exception.core.BusinessException
com.github.leyland.letool.exception.core.SystemException
```

若错误需要国际化，优先定义 `ErrorCode` 枚举并使用 `of(...)` / `causedBy(...)` 传递占位符参数；
只有明确不需要国际化时才使用 `custom(...)`。
