# Internationalized Exception Framework Design

## Goal

Add a reusable `letool-starter-exception` module that gives every letool starter a common exception model, error-code contract, Spring `MessageSource` integration, module-owned message bundles, application overrides, and predictable logging behavior.

This work is a prerequisite for the distributed-lock redesign. The lock starter will use the new framework for its error hierarchy after the exception module is implemented and verified.

## Current State

The repository currently contains `LetoolException`, `BusinessException`, and `SystemException` under `letool-starter-tool`, plus a web global exception handler. The root README and tool README claim internationalization support, but there is no `MessageSource` auto-configuration, message resolver, message bundle, or working `letool.tool.i18n` implementation.

The design is informed by the existing `ai-zy` exception framework, especially its `BaseException`, `ResultCode`, and `MessageUtils` concepts, while deliberately avoiding:

- a static globally injected `MessageSource`;
- message resolution inside exception constructors;
- one application-wide enum containing unrelated module and business codes;
- ambiguous constructors where a `String` may mean either a message argument or a custom message.

## Scope

This phase will:

- add `letool-starter-exception`;
- add the module to the root reactor and dependency management;
- introduce `ErrorCode`, `BaseException`, `BusinessException`, and `SystemException`;
- add injectable message resolution and Spring Boot auto-configuration;
- support application message overrides and starter-owned message bundles;
- update `letool-starter-web` to localize framework exceptions at the HTTP boundary;
- migrate the existing tool, web, data, sample, and distributed-lock references required to remove `LetoolException`;
- delete `LetoolException` without a compatibility adapter;
- add public documentation, configuration metadata, and tests.

Other starter-specific exceptions that currently extend `RuntimeException` are not all migrated in this phase. They can adopt the framework incrementally without changing the core design.

## Non-goals

- Replacing Spring's locale resolution or `MessageSource`.
- Adding a second response-envelope type.
- Encoding HTTP status codes into the core exception module.
- Automatically translating arbitrary third-party exceptions.
- Maintaining source or binary compatibility for `LetoolException`.
- Moving every historical starter exception in the same change.

## Module and Dependency Structure

Create:

```text
letool-starter-exception
```

The module does not depend on `letool-starter-tool`. It may depend on Spring Context and Spring Boot auto-configuration APIs needed for `MessageSource` integration.

Dependency direction:

```text
letool-starter-exception
          ↑
letool-starter-tool
          ↑
other starters
```

Modules that directly expose exception-framework types in their public API should declare `letool-starter-exception` explicitly instead of relying only on a transitive dependency. In this phase, web, data, and distributed-lock will declare or inherit the dependency as appropriate to their public API.

## Package Structure

```text
com.github.leyland.letool.exception
├─ code
│  ├─ ErrorCode
│  ├─ SimpleErrorCode
│  └─ CommonErrorCode
├─ core
│  ├─ BaseException
│  ├─ BusinessException
│  └─ SystemException
├─ message
│  ├─ MessageResolver
│  ├─ DefaultMessageResolver
│  ├─ SpringMessageResolver
│  └─ MessageBundleContributor
└─ config
   ├─ ExceptionProperties
   └─ ExceptionAutoConfiguration
```

`BaseException` is the abstract root name. The new public API does not use the name `LetoolException`.

## Error Code Contract

Each module owns its error-code enum and implements:

```java
public interface ErrorCode {

    String getCode();

    String getDefaultMessage();
}
```

Examples:

```java
public enum CommonErrorCode implements ErrorCode {
    SYSTEM_ERROR("SYS_001", "系统内部错误"),
    INVALID_ARGUMENT("ARG_001", "参数不合法：{0}"),
    SERVICE_UNAVAILABLE("SYS_002", "服务暂不可用");
}
```

The distributed-lock phase will add a separate `LockErrorCode`; it will not append lock codes to `CommonErrorCode`.

`SimpleErrorCode` is an immutable implementation used when migrating an existing `(String code, String message)` API or when an application needs a small dynamic code without creating an enum. Module-owned enums remain the recommended API.

Error codes are also message keys. Codes must be stable and globally unique within an application.

## Exception Model

`BaseException` extends `RuntimeException` and is abstract. It stores:

- `ErrorCode errorCode`;
- a defensively copied `Object[] messageArgs`;
- optional `String customMessage`;
- the stable formatted fallback message used for logs;
- the original cause through `RuntimeException`.

It provides:

- `getErrorCode()`;
- `getCode()`;
- `getMessageArgs()` with a defensive copy;
- `getCustomMessage()`;
- `getFallbackMessage()`;
- `hasCustomMessage()`.

The constructor passes a stable code-prefixed log message to `RuntimeException`:

```java
super("[" + errorCode.getCode() + "] " + fallbackMessage, cause);
```

The fallback is either the explicit custom message or the default error-code message formatted with the supplied arguments. Formatting uses a stable locale and does not access Spring. If formatting fails, exception construction still succeeds and retains the raw default message plus the arguments.

This guarantees that `getMessage()`, `getLocalizedMessage()`, Logback/Log4j throwable rendering, stack traces, and non-Spring execution contain both the error code and readable information. `MessageResolver` uses `getFallbackMessage()` rather than the code-prefixed log string when building a user-facing response.

The standard throwable rendering therefore includes the concrete class, code, and fallback message:

```text
com.github.leyland.letool.lock.exception.LockAcquisitionException:
[LOCK_001] 获取分布式锁超时，lockKey=rule:patient:123，waitTime=30000ms
```

Consequently, the ordinary call:

```java
log.error("规则执行失败", exception);
```

prints the error code, readable message, complete stack trace, suppressed exceptions, and cause without requiring a web handler or `MessageResolver`.

## Concrete Exception API

`BusinessException` and `SystemException` use named factories so message arguments cannot be confused with a custom message:

```java
throw BusinessException.of(
        CommonErrorCode.INVALID_ARGUMENT,
        fieldName
);

throw BusinessException.custom(
        CommonErrorCode.INVALID_ARGUMENT,
        "当前状态不允许执行"
);

throw SystemException.causedBy(
        CommonErrorCode.SERVICE_UNAVAILABLE,
        redisException
);
```

Cause-preserving factory variants may also accept message arguments.

Module-specific exceptions may expose constructors or named factories suited to their domain, but must ultimately populate the same `BaseException` fields.

## Message Resolution

`MessageResolver` is injectable and replaceable:

```java
public interface MessageResolver {

    String resolve(BaseException exception);

    String resolve(BaseException exception, Locale locale);

    String resolve(ErrorCode errorCode, Locale locale, Object... args);
}
```

`resolve(exception)` uses the locale bound to `LocaleContextHolder`. When no locale is bound, it uses the configured default locale.

Resolution precedence:

1. The exception's explicit custom message.
2. The application's existing `MessageSource`.
3. Starter-owned message bundles contributed through `MessageBundleContributor`.
4. `ErrorCode.getDefaultMessage()` formatted with message arguments.
5. The error code string as the final defensive fallback.

The resolver never replaces the application's bean named `messageSource`. It consumes it when available and maintains a separate internal source for letool bundles.

If internationalization is disabled, Spring is absent, or no message source is available, `DefaultMessageResolver` returns the formatted default or custom message.

## Starter-owned Message Bundles

`letool-starter-exception` includes:

```text
i18n/letool-exception/messages.properties
i18n/letool-exception/messages_zh_CN.properties
i18n/letool-exception/messages_en.properties
```

Other starters keep their resources inside their own artifacts. For example:

```text
i18n/letool-lock/messages.properties
i18n/letool-lock/messages_zh_CN.properties
i18n/letool-lock/messages_en.properties
```

They contribute basenames through:

```java
@Bean
MessageBundleContributor lockMessageBundle() {
    return MessageBundleContributor.of("i18n/letool-lock/messages");
}
```

`ExceptionAutoConfiguration` collects all contributors and creates the internal bundle source. Consumers do not need to append starter basenames to `spring.messages.basename`.

Applications override a starter message by placing the same code in their own standard bundle:

```properties
LOCK_001=患者规则正在执行，请稍后重试
```

Application messages always win.

## Configuration

Replace the unimplemented `letool.tool.i18n` documentation with:

```yaml
letool:
  exception:
    enabled: true
    i18n:
      enabled: true
      default-locale: zh_CN
      fallback-to-system-locale: false
```

Properties:

- `letool.exception.enabled`: enables exception auto-configuration; default `true`.
- `letool.exception.i18n.enabled`: enables localized lookup; default `true`.
- `letool.exception.i18n.default-locale`: locale used when no locale is bound; default `zh_CN`.
- `letool.exception.i18n.fallback-to-system-locale`: whether internal bundles may fall back to the JVM locale; default `false`.

Spring's `spring.messages.*` properties continue to control the application's own `MessageSource`.

## Auto-configuration

`ExceptionAutoConfiguration`:

- activates when `BaseException` is present and `letool.exception.enabled=true`;
- registers `ExceptionProperties`;
- collects every `MessageBundleContributor`;
- uses the application `MessageSource` when present without replacing it;
- registers `SpringMessageResolver` when i18n is enabled and Spring message support is available;
- otherwise registers `DefaultMessageResolver`;
- backs off when the application supplies its own `MessageResolver`.

The auto-configuration is listed in Boot 3's `AutoConfiguration.imports` and has configuration metadata.

No static field holds an application context or `MessageSource`.

## Web Integration

`letool-starter-web` injects `MessageResolver` into `GlobalExceptionHandler`.

Mappings:

- `BusinessException`: HTTP 400 and localized `R.fail(code, message)`.
- `SystemException`: HTTP 500, full server-side log, and localized safe message.
- other `BaseException`: HTTP 500 by default.
- validation and existing framework handlers retain their existing behavior.

More specific application or module handlers may override the generic mapping. `WebAutoConfiguration` continues to back off when the application supplies a custom global handler.

The web handler resolves the response message at the request boundary. It does not mutate the exception or replace its stable fallback log message.

## Logging and Failure Semantics

Logging and user-facing localization are deliberately independent:

- `getMessage()` is always useful without Spring.
- `getMessage()` and standard throwable rendering include the error code.
- `log.error("message", exception)` prints the code, fallback message, full stack, suppressed exceptions, and cause.
- response localization uses the request locale and may differ from the stable log message.
- backend or system exception causes are retained but are not exposed directly to clients.

For operations that produce both a primary business failure and a cleanup failure, the primary failure remains the thrown exception and the cleanup failure is attached through `addSuppressed`.

## Migration

The change intentionally removes:

```text
com.github.leyland.letool.tool.exception.LetoolException
```

No deprecated compatibility class remains.

The existing tool-package `BusinessException` and `SystemException` are replaced by the new exception-module types, and all repository usages are updated. Required migrations include:

- `letool-starter-web` handler and tests;
- `letool-starter-data` exception inheritance and tests;
- `letool-sample` imports and examples;
- tool documentation and examples;
- distributed-lock exception design and tests.

The root README is corrected so it no longer advertises the nonexistent `letool.tool.i18n` configuration.

## Testing

### Core unit tests

- `ErrorCode` and `SimpleErrorCode` validation.
- Default, parameterized, custom, and cause-preserving exception factories.
- Defensive copying of message arguments.
- Stable fallback formatting.
- Formatting failure does not prevent exception creation.
- `getMessage()` remains non-null without Spring.
- `toString()` contains class, code, and formatted message.

### Resolver tests

- Chinese and English lookup.
- Placeholder replacement.
- Explicit locale and `LocaleContextHolder` locale.
- No cross-thread locale leakage.
- Custom message precedence.
- Application message override precedence.
- Starter-bundle fallback.
- Default-message and code fallback.
- Disabled i18n behavior.
- Missing application `MessageSource`.

### Auto-configuration tests

- Default resolver registration.
- Custom `MessageResolver` backoff.
- Application `MessageSource` reuse without replacement.
- Contributor collection.
- Disabled exception and disabled i18n modes.
- Property binding and metadata defaults.

### Web tests

- Business, system, and generic base-exception status codes.
- Localized `R` response messages.
- System cause details are not returned to the client.
- Application handler override behavior.

### Logging test

Use a real SLF4J/Logback test appender and call:

```java
log.error("operation failed", exception);
```

Assert that the captured output contains:

- the concrete exception class;
- the error code;
- the formatted fallback message;
- a stack frame;
- the cause class and cause message.

### Reactor verification

- Run focused tests for exception, tool, web, data, sample, and distributed-lock.
- Run the full Maven reactor tests after the migration.
- Scan the repository to confirm no source or documentation reference to `LetoolException` or `letool.tool.i18n` remains.

## Delivery Sequence

1. Add the exception module, dependency management, configuration, and core tests.
2. Implement message resolution, bundle contribution, and auto-configuration tests.
3. Migrate tool, web, data, and sample references; delete `LetoolException`.
4. Add web localization and logging integration tests.
5. Verify focused modules and the full reactor.
6. Resume the distributed-lock redesign using the new exception framework.
