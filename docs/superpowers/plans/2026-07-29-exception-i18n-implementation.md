# Internationalized Exception Framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `letool-starter-exception`, migrate the existing shared exceptions to it, and localize framework errors through Spring `MessageSource` without sacrificing standalone logging.

**Architecture:** A Spring-independent exception core stores `ErrorCode`, arguments, a stable fallback message, and causes. An injectable resolver localizes messages at the application boundary by checking the application `MessageSource`, starter-owned bundles, and finally the default error-code message. `letool-starter-web` resolves messages for HTTP responses while ordinary throwable logging continues to use a stable code-prefixed message.

**Tech Stack:** Java 17, Spring Boot 3.4.5 auto-configuration, Spring `MessageSource`, JUnit 5, AssertJ, Spring Boot `ApplicationContextRunner`, SLF4J/Logback, Maven Wrapper.

---

## Comment and Documentation Standard

This implementation must include useful comments, not comment noise.

- Every public type and public/protected API method must have Javadoc describing semantics, arguments, return values, fallback behavior, and thread/locale behavior where applicable.
- `BaseException` must document why localized lookup is delayed until the response boundary.
- `SpringMessageResolver` must comment the resolution precedence at the branch where it is enforced.
- `ExceptionAutoConfiguration` must explain why it consumes but never replaces the application's bean named `messageSource`.
- Resource bundle keys must have a short grouping comment where the properties format permits it.
- Do not add comments that merely restate assignments, getters, or obvious control flow.
- The final verification includes Javadoc generation for the new public API.

## File Map

### New module

- Create `letool-starter-exception/pom.xml` — module dependencies and test dependencies.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/code/ErrorCode.java` — common error-code contract.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/code/SimpleErrorCode.java` — validated dynamic error code.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/code/CommonErrorCode.java` — framework-wide codes only.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/support/MessageFormatter.java` — stable `MessageFormat` fallback.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/core/BaseException.java` — abstract exception state and logging semantics.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/core/BusinessException.java` — client/business failure factories.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/core/SystemException.java` — infrastructure/system failure factories.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/message/MessageResolver.java` — localization SPI.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/message/DefaultMessageResolver.java` — non-Spring/disabled-i18n fallback.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/message/MessageBundleContributor.java` — starter bundle contribution SPI.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/message/SpringMessageResolver.java` — application-first composite lookup.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/config/ExceptionProperties.java` — `letool.exception` configuration.
- Create `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/config/ExceptionAutoConfiguration.java` — default resolver and bundle wiring.
- Create `letool-starter-exception/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
- Create `letool-starter-exception/src/main/resources/META-INF/additional-spring-configuration-metadata.json`.
- Create `letool-starter-exception/src/main/resources/i18n/letool-exception/messages.properties`.
- Create `letool-starter-exception/src/main/resources/i18n/letool-exception/messages_zh_CN.properties`.
- Create `letool-starter-exception/src/main/resources/i18n/letool-exception/messages_en.properties`.
- Create `letool-starter-exception/README.md`.

### New tests

- Create `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/code/ErrorCodeTest.java`.
- Create `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/core/BaseExceptionTest.java`.
- Create `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/core/ExceptionLoggingTest.java`.
- Create `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/message/DefaultMessageResolverTest.java`.
- Create `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/message/SpringMessageResolverTest.java`.
- Create `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/config/ExceptionPropertiesTest.java`.
- Create `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/config/ExceptionAutoConfigurationTest.java`.

### Existing files

- Modify `pom.xml` — reactor module and dependency-management entry.
- Modify `letool-starter-tool/pom.xml` — depend on the exception starter.
- Delete `letool-starter-tool/src/main/java/io/github/leylaragg/letool/tool/exception/LetoolException.java`.
- Delete `letool-starter-tool/src/main/java/io/github/leylaragg/letool/tool/exception/BusinessException.java`.
- Delete `letool-starter-tool/src/main/java/io/github/leylaragg/letool/tool/exception/SystemException.java`.
- Modify `letool-starter-web/pom.xml`.
- Modify `letool-starter-web/src/main/java/io/github/leylaragg/letool/web/advice/GlobalExceptionHandler.java`.
- Modify `letool-starter-web/src/main/java/io/github/leylaragg/letool/web/config/WebAutoConfiguration.java`.
- Modify `letool-starter-web/src/test/java/io/github/leylaragg/letool/web/advice/GlobalExceptionHandlerTest.java`.
- Modify `letool-starter-web/src/test/java/io/github/leylaragg/letool/web/config/WebAutoConfigurationTest.java`.
- Modify `letool-starter-data/pom.xml`.
- Modify `letool-starter-data/src/main/java/io/github/leylaragg/letool/data/exception/DataException.java`.
- Modify `letool-starter-data/src/test/java/io/github/leylaragg/letool/data/exception/DataExceptionTest.java`.
- Modify `letool-sample/src/main/java/io/github/leylaragg/letool/sample/controller/WebController.java`.
- Modify `letool-starter-distributed-lock/src/test/java/io/github/leylaragg/letool/lock/exception/LockExceptionTest.java` — remove the stale `LetoolException` reference only; the lock hierarchy is handled by the later lock plan.
- Modify `README.md`.
- Modify `letool-starter-tool/README.md`.

## Task 1: Add the Maven Module Skeleton

**Files:**

- Create: `letool-starter-exception/pom.xml`
- Modify: `pom.xml:30-33`
- Modify: `pom.xml:158-169`

- [ ] **Step 1: Verify the module does not yet exist**

Run:

```powershell
Test-Path 'letool-starter-exception\pom.xml'
.\mvnw.cmd -pl letool-starter-exception test -DskipTests
```

Expected:

- `Test-Path` prints `False`.
- Maven fails with `Could not find the selected project in the reactor`.

- [ ] **Step 2: Create the child POM**

Create `letool-starter-exception/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>io.github.leylaragg</groupId>
        <artifactId>letool</artifactId>
        <version>2.0.0-beta.1</version>
    </parent>

    <artifactId>letool-starter-exception</artifactId>
    <name>letool-starter-exception</name>
    <description>letool 统一异常、错误码与国际化消息解析</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Add the module before `letool-starter-tool`**

Modify the root `<modules>` block:

```xml
<!-- 阶段 1：基础设施 -->
<module>letool-starter-exception</module>
<module>letool-starter-tool</module>
```

Add the managed dependency before `letool-starter-tool`:

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-exception</artifactId>
    <version>${letool.version}</version>
</dependency>
```

- [ ] **Step 4: Verify Maven recognizes the module**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-exception test -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 5: Commit only the module skeleton**

```powershell
git add pom.xml letool-starter-exception/pom.xml
git commit -m "build(exception): add exception starter module"
```

## Task 2: Implement the Error-Code and Exception Core with TDD

**Files:**

- Create: `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/code/ErrorCodeTest.java`
- Create: `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/core/BaseExceptionTest.java`
- Create: `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/core/ExceptionLoggingTest.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/code/ErrorCode.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/code/SimpleErrorCode.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/code/CommonErrorCode.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/support/MessageFormatter.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/core/BaseException.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/core/BusinessException.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/core/SystemException.java`

- [ ] **Step 1: Write failing error-code validation tests**

Create `ErrorCodeTest.java` with these cases:

```java
package io.github.leylaragg.letool.exception.code;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ErrorCodeTest {

    @Test
    void shouldCreateSimpleErrorCode() {
        ErrorCode code = ErrorCode.of("TEST_001", "测试消息：{0}");

        assertThat(code.getCode()).isEqualTo("TEST_001");
        assertThat(code.getDefaultMessage()).isEqualTo("测试消息：{0}");
    }

    @Test
    void shouldRejectBlankCode() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ErrorCode.of(" ", "message"))
                .withMessageContaining("code");
    }

    @Test
    void shouldRejectBlankDefaultMessage() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ErrorCode.of("TEST_001", " "))
                .withMessageContaining("defaultMessage");
    }
}
```

- [ ] **Step 2: Write failing exception-state and factory tests**

Create `BaseExceptionTest.java`:

```java
package io.github.leylaragg.letool.exception.core;

import io.github.leylaragg.letool.exception.code.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaseExceptionTest {

    private static final ErrorCode CODE =
            ErrorCode.of("TEST_001", "资源 {0} 不存在");

    @Test
    void shouldKeepCodeAndFormatStableLogMessage() {
        BusinessException exception = BusinessException.of(CODE, "patient-1");

        assertThat(exception.getCode()).isEqualTo("TEST_001");
        assertThat(exception.getFallbackMessage()).isEqualTo("资源 patient-1 不存在");
        assertThat(exception.getMessage()).isEqualTo("[TEST_001] 资源 patient-1 不存在");
        assertThat(exception.toString())
                .contains(BusinessException.class.getName())
                .contains("[TEST_001]")
                .contains("patient-1");
    }

    @Test
    void shouldDefensivelyCopyMessageArguments() {
        Object[] arguments = {"before"};
        BusinessException exception = BusinessException.of(CODE, arguments);

        arguments[0] = "mutated";
        Object[] returned = exception.getMessageArgs();
        returned[0] = "changed-again";

        assertThat(exception.getMessageArgs()).containsExactly("before");
        assertThat(exception.getFallbackMessage()).isEqualTo("资源 before 不存在");
    }

    @Test
    void shouldPreferCustomMessage() {
        BusinessException exception =
                BusinessException.custom(CODE, "患者规则正在执行");

        assertThat(exception.hasCustomMessage()).isTrue();
        assertThat(exception.getFallbackMessage()).isEqualTo("患者规则正在执行");
        assertThat(exception.getMessage()).isEqualTo("[TEST_001] 患者规则正在执行");
    }

    @Test
    void shouldRetainCause() {
        IllegalStateException cause = new IllegalStateException("redis down");

        SystemException exception = SystemException.causedBy(CODE, cause, "patient-1");

        assertThat(exception).hasCause(cause);
        assertThat(exception.getMessage()).contains("patient-1");
    }

    @Test
    void shouldSurviveInvalidMessagePattern() {
        ErrorCode invalid = ErrorCode.of("TEST_002", "invalid { pattern");

        BusinessException exception = BusinessException.of(invalid, "value");

        assertThat(exception.getMessage())
                .contains("[TEST_002]")
                .contains("invalid { pattern")
                .contains("value");
    }
}
```

- [ ] **Step 3: Write the real throwable logging test**

Create `ExceptionLoggingTest.java`:

```java
package io.github.leylaragg.letool.exception.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import io.github.leylaragg.letool.exception.code.ErrorCode;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionLoggingTest {

    @Test
    void logErrorShouldRenderCodeMessageStackAndCause() {
        Logger logger = (Logger) LoggerFactory.getLogger("exception-logging-test");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            IllegalStateException cause = new IllegalStateException("redis down");
            SystemException exception = SystemException.causedBy(
                    ErrorCode.of("SYS_TEST", "锁后端不可用：{0}"),
                    cause,
                    "redis");

            logger.error("operation failed", exception);

            ILoggingEvent event = appender.list.get(0);
            String rendered = ThrowableProxyUtil.asString(event.getThrowableProxy());
            assertThat(rendered)
                    .contains(SystemException.class.getName())
                    .contains("[SYS_TEST]")
                    .contains("锁后端不可用：redis")
                    .contains("ExceptionLoggingTest")
                    .contains(IllegalStateException.class.getName())
                    .contains("redis down");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
```

- [ ] **Step 4: Run the tests and confirm the red state**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-exception -Dtest=ErrorCodeTest,BaseExceptionTest,ExceptionLoggingTest test
```

Expected: test compilation fails because the exception API does not exist.

- [ ] **Step 5: Implement `ErrorCode`, `SimpleErrorCode`, and common codes**

Implement `ErrorCode` with complete interface Javadoc and a convenience factory:

```java
public interface ErrorCode {

    String getCode();

    String getDefaultMessage();

    static ErrorCode of(String code, String defaultMessage) {
        return new SimpleErrorCode(code, defaultMessage);
    }
}
```

Implement `SimpleErrorCode` as a Java 17 record. Its compact constructor must reject null/blank code and default message with an `IllegalArgumentException` naming the invalid field.

Implement:

```java
public enum CommonErrorCode implements ErrorCode {
    SYSTEM_ERROR("SYS_001", "系统内部错误"),
    INVALID_ARGUMENT("ARG_001", "参数不合法：{0}"),
    SERVICE_UNAVAILABLE("SYS_002", "服务暂不可用");

    private final String code;
    private final String defaultMessage;

    CommonErrorCode(String code, String defaultMessage) {
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

- [ ] **Step 6: Implement stable fallback formatting**

Create `MessageFormatter` as a non-instantiable utility with this behavior:

```java
public static String format(String pattern, Locale locale, Object... arguments) {
    Objects.requireNonNull(pattern, "pattern");
    Locale effectiveLocale = locale == null ? Locale.ROOT : locale;
    Object[] safeArguments = arguments == null ? new Object[0] : arguments.clone();
    if (safeArguments.length == 0) {
        return pattern;
    }
    try {
        return new MessageFormat(pattern, effectiveLocale).format(safeArguments);
    } catch (IllegalArgumentException formattingFailure) {
        // Exception construction must never hide the original failure because a
        // message bundle contains a malformed MessageFormat pattern.
        return pattern + " " + Arrays.toString(safeArguments);
    }
}
```

Add class and method Javadoc explaining stable log formatting and malformed-pattern fallback.

- [ ] **Step 7: Implement `BaseException`**

Implement an abstract `BaseException` with a protected constructor:

```java
protected BaseException(
        ErrorCode errorCode,
        Object[] messageArgs,
        String customMessage,
        Throwable cause)
```

Use a private immutable constructor state so the call to `super(...)` receives:

```java
"[" + errorCode.getCode() + "] " + fallbackMessage
```

Requirements:

- validate `errorCode` before reading it;
- clone arguments on input and output;
- compute `fallbackMessage` from custom message or
  `MessageFormatter.format(defaultMessage, Locale.ROOT, args)`;
- expose `getErrorCode()`, `getCode()`, `getMessageArgs()`,
  `getCustomMessage()`, `getFallbackMessage()`, and `hasCustomMessage()`;
- override `toString()` as
  `getClass().getName() + ": " + getMessage()`;
- include class-level Javadoc explaining delayed localization and logging.

- [ ] **Step 8: Implement named factories**

Implement non-final `BusinessException` and `SystemException`.

Each class must provide:

```java
public static BusinessException of(ErrorCode code, Object... args)
public static BusinessException custom(ErrorCode code, String customMessage)
public static BusinessException causedBy(ErrorCode code, Throwable cause, Object... args)
```

and the corresponding `SystemException` factories.

Each class also needs a protected constructor accepting
`(ErrorCode, Object[], String, Throwable)` so domain exceptions such as
`DataException` can inherit the correct category.

Add Javadoc to every factory that distinguishes localized arguments from custom messages.

- [ ] **Step 9: Run core and logging tests**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-exception -Dtest=ErrorCodeTest,BaseExceptionTest,ExceptionLoggingTest test
```

Expected: all listed tests pass, including the Logback throwable-rendering assertion.

- [ ] **Step 10: Commit the core**

```powershell
git add letool-starter-exception/src/main/java letool-starter-exception/src/test/java
git commit -m "feat(exception): add error code and base exception model"
```

## Task 3: Implement Message Resolution and Bundle Composition with TDD

**Files:**

- Create: `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/message/DefaultMessageResolverTest.java`
- Create: `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/message/SpringMessageResolverTest.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/message/MessageResolver.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/message/DefaultMessageResolver.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/message/MessageBundleContributor.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/message/SpringMessageResolver.java`
- Create: `letool-starter-exception/src/main/resources/i18n/letool-exception/messages.properties`
- Create: `letool-starter-exception/src/main/resources/i18n/letool-exception/messages_zh_CN.properties`
- Create: `letool-starter-exception/src/main/resources/i18n/letool-exception/messages_en.properties`

- [ ] **Step 1: Write fallback resolver tests**

Create `DefaultMessageResolverTest.java`:

```java
class DefaultMessageResolverTest {

    private final MessageResolver resolver =
            new DefaultMessageResolver(Locale.SIMPLIFIED_CHINESE);

    @Test
    void shouldResolveFormattedFallback() {
        BusinessException exception = BusinessException.of(
                ErrorCode.of("TEST_001", "字段 {0} 不存在"),
                "age");

        assertThat(resolver.resolve(exception)).isEqualTo("字段 age 不存在");
    }

    @Test
    void shouldPreferCustomMessage() {
        BusinessException exception = BusinessException.custom(
                ErrorCode.of("TEST_001", "默认消息"),
                "自定义消息");

        assertThat(resolver.resolve(exception)).isEqualTo("自定义消息");
    }

    @Test
    void shouldFormatExplicitLocaleWithoutSpring() {
        String message = resolver.resolve(
                ErrorCode.of("TEST_002", "value {0,number}"),
                Locale.US,
                1234);

        assertThat(message).contains("1,234");
    }
}
```

Add the package, imports, and AssertJ static import.

- [ ] **Step 2: Write composite Spring resolver tests**

Create `SpringMessageResolverTest.java`. Build two
`StaticMessageSource` instances: one representing the application and one
representing starter bundles.

Required test methods:

```java
@Test
void applicationMessageShouldOverrideStarterMessage()

@Test
void starterMessageShouldBeUsedWhenApplicationHasNoCode()

@Test
void defaultMessageShouldBeUsedWhenNoSourceHasCode()

@Test
void explicitCustomMessageShouldBypassBothSources()

@Test
void localeContextShouldNotLeakAcrossThreads()
```

Use these exact assertions in the first three:

```java
application.addMessage("TEST_001", Locale.ENGLISH, "application {0}");
starter.addMessage("TEST_001", Locale.ENGLISH, "starter {0}");
assertThat(resolver.resolve(code, Locale.ENGLISH, "value"))
        .isEqualTo("application value");
```

```java
starter.addMessage("TEST_001", Locale.SIMPLIFIED_CHINESE, "组件消息 {0}");
assertThat(resolver.resolve(code, Locale.SIMPLIFIED_CHINESE, "值"))
        .isEqualTo("组件消息 值");
```

```java
assertThat(resolver.resolve(code, Locale.ENGLISH, "value"))
        .isEqualTo("default value");
```

For the thread test, use two tasks with `LocaleContextHolder.setLocale(...)`,
resolve the same exception, clear the context in `finally`, and assert one
English and one Chinese result.

- [ ] **Step 3: Run message tests and verify failure**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-exception -Dtest=DefaultMessageResolverTest,SpringMessageResolverTest test
```

Expected: test compilation fails because resolver types do not exist.

- [ ] **Step 4: Implement the `MessageResolver` SPI**

Create the interface with fully documented methods:

```java
public interface MessageResolver {

    String resolve(BaseException exception);

    String resolve(BaseException exception, Locale locale);

    String resolve(ErrorCode errorCode, Locale locale, Object... args);
}
```

Document that returned user-facing messages do not include the log-only
`[CODE]` prefix.

- [ ] **Step 5: Implement `DefaultMessageResolver`**

Store a non-null default locale.

Behavior:

```java
resolve(exception)
    -> resolve with LocaleContextHolder locale when bound,
       otherwise configured default locale

resolve(exception, locale)
    -> custom message when present,
       otherwise MessageFormatter.format(
           exception.getErrorCode().getDefaultMessage(),
           effective locale,
           exception.getMessageArgs())

resolve(code, locale, args)
    -> format the code's default message
```

Add one focused comment at locale selection explaining why the configured
default is used instead of the JVM locale.

- [ ] **Step 6: Implement `MessageBundleContributor`**

Expose:

```java
List<String> getBasenames();

static MessageBundleContributor of(String... basenames)
```

The factory must:

- reject a null/empty basename array;
- reject blank entries;
- defensively copy inputs;
- return an unmodifiable list.

Document that basenames use Spring resource-bundle notation without locale
suffixes.

- [ ] **Step 7: Implement `SpringMessageResolver`**

Constructor:

```java
public SpringMessageResolver(
        MessageSource applicationMessageSource,
        MessageSource starterMessageSource,
        Locale defaultLocale)
```

Allow the application source to be null; require the starter source and default
locale.

Implement this exact lookup order in a small private method:

```java
// Application messages intentionally win so a service can customize framework
// wording without replacing the resolver or repackaging a starter.
String resolved = find(applicationMessageSource, code, args, locale);
if (resolved != null) {
    return resolved;
}
resolved = find(starterMessageSource, code, args, locale);
if (resolved != null) {
    return resolved;
}
return MessageFormatter.format(defaultMessage, locale, args);
```

Use `MessageSource.getMessage(code, args, null, locale)` for non-throwing lookup.
Custom exception messages bypass all sources.

- [ ] **Step 8: Add the common bundles**

Create the default and Chinese files with:

```properties
# Common framework errors
SYS_001=系统内部错误
ARG_001=参数不合法：{0}
SYS_002=服务暂不可用
```

Create the English file with:

```properties
# Common framework errors
SYS_001=Internal system error
ARG_001=Invalid argument: {0}
SYS_002=Service is temporarily unavailable
```

- [ ] **Step 9: Run message tests**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-exception -Dtest=DefaultMessageResolverTest,SpringMessageResolverTest test
```

Expected: all resolver precedence, locale, and fallback tests pass.

- [ ] **Step 10: Commit resolver and bundles**

```powershell
git add letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/message
git add letool-starter-exception/src/main/resources/i18n
git add letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/message
git commit -m "feat(exception): add localized message resolution"
```

## Task 4: Add Spring Boot Auto-configuration with TDD

**Files:**

- Create: `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/config/ExceptionPropertiesTest.java`
- Create: `letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/config/ExceptionAutoConfigurationTest.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/config/ExceptionProperties.java`
- Create: `letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/config/ExceptionAutoConfiguration.java`
- Create: `letool-starter-exception/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `letool-starter-exception/src/main/resources/META-INF/additional-spring-configuration-metadata.json`

- [ ] **Step 1: Write property default and binding tests**

Create `ExceptionPropertiesTest.java` with:

```java
@Test
void defaultsShouldBeSafeAndStable() {
    ExceptionProperties properties = new ExceptionProperties();

    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getI18n().isEnabled()).isTrue();
    assertThat(properties.getI18n().getDefaultLocale())
            .isEqualTo(Locale.SIMPLIFIED_CHINESE);
    assertThat(properties.getI18n().isFallbackToSystemLocale()).isFalse();
}
```

Add an `ApplicationContextRunner` binding test for:

```text
letool.exception.enabled=true
letool.exception.i18n.enabled=false
letool.exception.i18n.default-locale=en_US
letool.exception.i18n.fallback-to-system-locale=true
```

Assert every bound value.

- [ ] **Step 2: Write auto-configuration tests**

Create an `ApplicationContextRunner` using:

```java
new ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(ExceptionAutoConfiguration.class));
```

Required tests:

- default context has one `MessageResolver`, of type `SpringMessageResolver`;
- `letool.exception.enabled=false` has no resolver;
- `letool.exception.i18n.enabled=false` uses `DefaultMessageResolver`;
- a user `MessageResolver` bean wins;
- a user bean named `messageSource` is not replaced and its message overrides the starter bundle;
- contributor basenames are collected;
- bound default locale is passed to the resolver.

For user overrides, define nested `@Configuration(proxyBeanMethods = false)`
classes with explicit beans rather than mocks.

- [ ] **Step 3: Run tests and verify the red state**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-exception -Dtest=ExceptionPropertiesTest,ExceptionAutoConfigurationTest test
```

Expected: compilation fails because properties and auto-configuration are absent.

- [ ] **Step 4: Implement `ExceptionProperties`**

Use:

```java
@ConfigurationProperties(prefix = "letool.exception")
public class ExceptionProperties {

    private boolean enabled = true;
    private I18n i18n = new I18n();

    public static class I18n {
        private boolean enabled = true;
        private Locale defaultLocale = Locale.SIMPLIFIED_CHINESE;
        private boolean fallbackToSystemLocale = false;
        // getters and setters
    }
    // getters and setters
}
```

Add Javadoc to the class and each property field. Avoid comments on trivial
getter implementations.

- [ ] **Step 5: Implement auto-configuration**

Use:

```java
@AutoConfiguration(after = MessageSourceAutoConfiguration.class)
@EnableConfigurationProperties(ExceptionProperties.class)
@ConditionalOnProperty(
        prefix = "letool.exception",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ExceptionAutoConfiguration {
```

Register:

```java
@Bean
MessageBundleContributor commonExceptionMessageBundle() {
    return MessageBundleContributor.of("i18n/letool-exception/messages");
}
```

Register a conditional `MessageResolver`. Construct a
`ResourceBundleMessageSource`, flatten and de-duplicate contributor basenames,
set UTF-8 encoding, and apply `fallbackToSystemLocale`.

Inject the application source as:

```java
@Qualifier(AbstractApplicationContext.MESSAGE_SOURCE_BEAN_NAME)
ObjectProvider<MessageSource> applicationMessageSource
```

Do not register the internal source as a `MessageSource` bean.

When i18n is disabled, return `DefaultMessageResolver`; otherwise return
`SpringMessageResolver`.

Add a method-level comment explaining that keeping the internal source private
prevents it from competing with Spring Boot's application `messageSource`.

- [ ] **Step 6: Add Boot registration and metadata**

`AutoConfiguration.imports`:

```text
io.github.leylaragg.letool.exception.config.ExceptionAutoConfiguration
```

Add JSON metadata for all four properties with exact defaults:

```json
{
  "properties": [
    {
      "name": "letool.exception.enabled",
      "type": "java.lang.Boolean",
      "defaultValue": true,
      "description": "Whether to enable letool exception auto-configuration."
    },
    {
      "name": "letool.exception.i18n.enabled",
      "type": "java.lang.Boolean",
      "defaultValue": true,
      "description": "Whether to resolve error messages from localized bundles."
    },
    {
      "name": "letool.exception.i18n.default-locale",
      "type": "java.util.Locale",
      "defaultValue": "zh_CN",
      "description": "Locale used when no locale is bound to the current context."
    },
    {
      "name": "letool.exception.i18n.fallback-to-system-locale",
      "type": "java.lang.Boolean",
      "defaultValue": false,
      "description": "Whether starter bundles may fall back to the JVM locale."
    }
  ]
}
```

- [ ] **Step 7: Run auto-configuration tests**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-exception -Dtest=ExceptionPropertiesTest,ExceptionAutoConfigurationTest test
```

Expected: all property, backoff, bundle, and application override tests pass.

- [ ] **Step 8: Run the complete new-module suite**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-exception test
```

Expected: `BUILD SUCCESS`, zero test failures.

- [ ] **Step 9: Commit auto-configuration**

```powershell
git add letool-starter-exception/src/main/java/io/github/leylaragg/letool/exception/config
git add letool-starter-exception/src/main/resources/META-INF
git add letool-starter-exception/src/test/java/io/github/leylaragg/letool/exception/config
git commit -m "feat(exception): add Spring Boot message auto-configuration"
```

## Task 5: Migrate Tool, Data, Sample, and Stale Lock References

**Files:**

- Modify: `letool-starter-tool/pom.xml`
- Delete: `letool-starter-tool/src/main/java/io/github/leylaragg/letool/tool/exception/LetoolException.java`
- Delete: `letool-starter-tool/src/main/java/io/github/leylaragg/letool/tool/exception/BusinessException.java`
- Delete: `letool-starter-tool/src/main/java/io/github/leylaragg/letool/tool/exception/SystemException.java`
- Modify: `letool-starter-data/pom.xml`
- Modify: `letool-starter-data/src/main/java/io/github/leylaragg/letool/data/exception/DataException.java`
- Modify: `letool-starter-data/src/test/java/io/github/leylaragg/letool/data/exception/DataExceptionTest.java`
- Modify: `letool-sample/src/main/java/io/github/leylaragg/letool/sample/controller/WebController.java`
- Modify: `letool-starter-distributed-lock/src/test/java/io/github/leylaragg/letool/lock/exception/LockExceptionTest.java`

- [ ] **Step 1: Update data tests first**

Replace `LetoolException` assertions with:

```java
import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.exception.core.SystemException;

@Test
void shouldExtendSharedSystemException() {
    DataException exception = new DataException("DATA_001", "查询失败");

    assertThat(exception).isInstanceOf(BaseException.class);
    assertThat(exception).isInstanceOf(SystemException.class);
    assertThat(exception.getCode()).isEqualTo("DATA_001");
    assertThat(exception.getFallbackMessage()).isEqualTo("查询失败");
}
```

Keep existing constructor and cause-chain coverage, updating expected
`getMessage()` to include `[DATA_xxx]`.

- [ ] **Step 2: Run the data test and verify failure**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-data -am -Dtest=DataExceptionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: test compilation fails because `DataException` still extends
`LetoolException`.

- [ ] **Step 3: Add explicit exception dependencies**

Add to `letool-starter-tool/pom.xml` and `letool-starter-data/pom.xml`:

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-exception</artifactId>
</dependency>
```

Keep the dependency compile-scoped because public APIs expose exception types.

- [ ] **Step 4: Migrate `DataException`**

Change it to:

```java
public class DataException extends SystemException {

    public DataException(String errorCode, String message) {
        super(ErrorCode.of(errorCode, message), null, null, null);
    }

    public DataException(String errorCode, String message, Throwable cause) {
        super(ErrorCode.of(errorCode, message), null, null, cause);
    }
}
```

Update imports and class Javadoc to reference `SystemException` and explain
that the string constructor remains for the existing data error-code API.

- [ ] **Step 5: Delete the old tool exceptions**

Delete all three files under:

```text
letool-starter-tool/src/main/java/io/github/leylaragg/letool/tool/exception/
```

Do not leave a `LetoolException` compatibility class.

- [ ] **Step 6: Migrate the sample**

Change the import to the new `BusinessException` and replace:

```java
throw new BusinessException(code, "演示业务异常：订单不存在");
```

with:

```java
throw BusinessException.of(
        ErrorCode.of(code, "演示业务异常：订单不存在"));
```

Add the `ErrorCode` import. Keep the endpoint Javadoc and update it to mention
the new exception module.

- [ ] **Step 7: Remove the stale lock-test comment**

In `LockExceptionTest`, remove or rewrite the assertion comment that says
`DataException` extends `LetoolException`. Do not redesign `LockException` in
this task.

- [ ] **Step 8: Scan for old imports**

Run:

```powershell
rg -n "tool\.exception|LetoolException" --glob '*.java' .
```

Expected: no matches.

- [ ] **Step 9: Run focused migration tests**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-tool,letool-starter-data,letool-sample,letool-starter-distributed-lock -am test
```

Expected: all selected modules compile and test successfully.

- [ ] **Step 10: Commit the migration**

Stage only the named module files and commit:

```powershell
git add letool-starter-tool/pom.xml
git add letool-starter-tool/src/main/java/io/github/leylaragg/letool/tool/exception
git add letool-starter-data/pom.xml letool-starter-data/src
git add letool-sample/src/main/java/io/github/leylaragg/letool/sample/controller/WebController.java
git add letool-starter-distributed-lock/src/test/java/io/github/leylaragg/letool/lock/exception/LockExceptionTest.java
git commit -m "refactor(exception): migrate shared exception types"
```

## Task 6: Localize Web Responses with TDD

**Files:**

- Modify: `letool-starter-web/pom.xml`
- Modify: `letool-starter-web/src/test/java/io/github/leylaragg/letool/web/advice/GlobalExceptionHandlerTest.java`
- Modify: `letool-starter-web/src/test/java/io/github/leylaragg/letool/web/config/WebAutoConfigurationTest.java`
- Modify: `letool-starter-web/src/main/java/io/github/leylaragg/letool/web/advice/GlobalExceptionHandler.java`
- Modify: `letool-starter-web/src/main/java/io/github/leylaragg/letool/web/config/WebAutoConfiguration.java`

- [ ] **Step 1: Rewrite handler tests around the resolver boundary**

Construct the handler with a `StaticMessageSource` and `SpringMessageResolver`.

Add tests:

```java
@Test
void businessExceptionShouldReturnLocalizedMessageAndCode()

@Test
void systemExceptionShouldReturnSafeLocalizedMessageWithoutCauseDetails()

@Test
void baseExceptionShouldUseGeneric500Mapping()

@Test
void customMessageShouldBypassMessageSource()
```

The business test must set:

```java
messageSource.addMessage("BIZ_001", Locale.ENGLISH, "Order {0} not found");
LocaleContextHolder.setLocale(Locale.ENGLISH);
BusinessException exception = BusinessException.of(
        ErrorCode.of("BIZ_001", "订单 {0} 不存在"),
        "42");
```

Assert response code `BIZ_001` and message `Order 42 not found`. Clear
`LocaleContextHolder` in `finally`.

The system test must use a cause containing `redis password=secret` and assert
that the returned message does not contain `password` or `secret`.

- [ ] **Step 2: Update auto-configuration tests first**

Change the context runner to include:

```java
AutoConfigurations.of(
        ExceptionAutoConfiguration.class,
        WebAutoConfiguration.class)
```

Assert:

- default context has one `GlobalExceptionHandler`;
- handler construction succeeds because a `MessageResolver` exists;
- `letool.exception.enabled=false` causes only the framework exception handler
  bean to back off instead of failing the entire web context;
- a user `GlobalExceptionHandler` still wins.

- [ ] **Step 3: Run web tests and verify failure**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-web -am -Dtest=GlobalExceptionHandlerTest,WebAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because the old handler imports tool exceptions
and has no resolver constructor.

- [ ] **Step 4: Add the explicit web dependency**

Add to `letool-starter-web/pom.xml`:

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-exception</artifactId>
</dependency>
```

- [ ] **Step 5: Migrate `GlobalExceptionHandler`**

Replace old imports with:

```java
import io.github.leylaragg.letool.exception.core.BaseException;
import io.github.leylaragg.letool.exception.core.BusinessException;
import io.github.leylaragg.letool.exception.core.SystemException;
import io.github.leylaragg.letool.exception.message.MessageResolver;
```

Add:

```java
private final MessageResolver messageResolver;

public GlobalExceptionHandler(MessageResolver messageResolver) {
    this.messageResolver =
            Objects.requireNonNull(messageResolver, "messageResolver");
}
```

Handlers:

```java
@ExceptionHandler(BusinessException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public R<Void> handleBusinessException(BusinessException exception) {
    String message = messageResolver.resolve(exception);
    log.warn("Business exception: [{}] {}", exception.getCode(), exception.getFallbackMessage());
    return R.fail(exception.getCode(), message);
}
```

```java
@ExceptionHandler(SystemException.class)
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public R<Void> handleSystemException(SystemException exception) {
    String message = messageResolver.resolve(exception);
    log.error("System exception: [{}] {}", exception.getCode(), exception.getFallbackMessage(), exception);
    return R.fail(exception.getCode(), message);
}
```

Add a generic `BaseException` handler with HTTP 500 and remove the
`LetoolException` handler.

Retain existing validation, illegal argument, and generic handlers.

Update class-level Javadoc with the new hierarchy and explain that response
localization occurs here while logs use the stable fallback.

- [ ] **Step 6: Update web auto-configuration**

Make the handler bean method accept `MessageResolver`:

```java
@Bean
@ConditionalOnMissingBean(GlobalExceptionHandler.class)
@ConditionalOnBean(MessageResolver.class)
public GlobalExceptionHandler globalExceptionHandler(
        MessageResolver messageResolver) {
    return new GlobalExceptionHandler(messageResolver);
}
```

Order `WebAutoConfiguration` after `ExceptionAutoConfiguration` and add class
Javadoc explaining the dependency.

- [ ] **Step 7: Run web tests**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-web -am -Dtest=GlobalExceptionHandlerTest,WebAutoConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass.

- [ ] **Step 8: Run the complete web suite**

Run:

```powershell
.\mvnw.cmd -pl letool-starter-web -am test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit web integration**

```powershell
git add letool-starter-web/pom.xml letool-starter-web/src
git commit -m "feat(web): localize shared exception responses"
```

## Task 7: Add User Documentation and Configuration Examples

**Files:**

- Create: `letool-starter-exception/README.md`
- Modify: `README.md`
- Modify: `letool-starter-tool/README.md`

- [ ] **Step 1: Write the exception starter README**

Include these complete sections:

- dependency coordinates;
- `ErrorCode` enum example;
- `BusinessException.of`, `custom`, and `SystemException.causedBy`;
- `messages.properties`, `messages_zh_CN.properties`, and
  `messages_en.properties` examples;
- application override example;
- configuration table;
- logging example showing `log.error("xxx", exception)`;
- extension example with `MessageBundleContributor`;
- migration note that `LetoolException` was removed.

Use this logging example:

```java
try {
    invokeBackend();
} catch (Exception cause) {
    SystemException exception = SystemException.causedBy(
            CommonErrorCode.SERVICE_UNAVAILABLE,
            cause);
    log.error("调用后端失败", exception);
    throw exception;
}
```

State explicitly that the log uses the stable default message while HTTP output
may use the request locale.

- [ ] **Step 2: Update the root README**

Add `letool-starter-exception` to the module table and replace:

```yaml
letool:
  tool:
    i18n:
```

with:

```yaml
letool:
  exception:
    enabled: true
    i18n:
      enabled: true
      default-locale: zh_CN
      fallback-to-system-locale: false
```

- [ ] **Step 3: Correct the tool README**

Remove claims that `letool-starter-tool` itself owns internationalization and
the exception hierarchy. Link to `letool-starter-exception` instead. Preserve
the actual tool utilities documentation.

- [ ] **Step 4: Verify documentation references**

Run:

```powershell
rg -n "LetoolException|letool\.tool\.i18n" README.md letool-starter-*/README.md docs
```

Expected: no active API/config references. Historical design text may mention
the removed class only in the explicit migration rationale; verify those
matches manually rather than editing unrelated user documents.

- [ ] **Step 5: Commit documentation**

```powershell
git add README.md letool-starter-tool/README.md letool-starter-exception/README.md
git commit -m "docs(exception): document i18n exception framework"
```

## Task 8: Final Verification and Scope Audit

**Files:**

- Verify all files from Tasks 1-7.
- Do not modify unrelated untracked documents or user changes.

- [ ] **Step 1: Run the complete new-module tests**

```powershell
.\mvnw.cmd -pl letool-starter-exception test
```

Expected: `BUILD SUCCESS`, zero failures and errors.

- [ ] **Step 2: Run all directly affected modules**

```powershell
.\mvnw.cmd -pl letool-starter-tool,letool-starter-web,letool-starter-data,letool-sample,letool-starter-distributed-lock -am test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Generate Javadoc for comment quality**

```powershell
.\mvnw.cmd -pl letool-starter-exception -am -DskipTests javadoc:javadoc
```

Expected: `BUILD SUCCESS` with no missing-reference or malformed-Javadoc errors
from the new public API.

- [ ] **Step 4: Run the full reactor**

```powershell
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`. If an unrelated pre-existing module fails, record the
exact module, test, and failure without claiming the reactor passes; affected
module verification from Steps 1-2 must still remain green.

- [ ] **Step 5: Run source and configuration scans**

```powershell
rg -n "tool\.exception|LetoolException" --glob '*.java' .
rg -n "letool\.tool\.i18n" README.md letool-starter-* docs
rg -n "static\s+MessageSource|setMessageSource\s*\(" letool-starter-exception
```

Expected:

- no Java reference to old tool exceptions;
- no active legacy configuration reference;
- no static `MessageSource` holder or setter.

- [ ] **Step 6: Inspect Git scope**

```powershell
git status --short
git diff --stat adc031a..HEAD
git diff --check adc031a..HEAD
```

Expected:

- only the approved exception design, implementation plan, exception-framework,
  and named migration files changed after design commit `adc031a`;
- unrelated untracked `CHANGELOG.md` or `docs` files remain untouched;
- no whitespace errors.

- [ ] **Step 7: Create a final verification commit only if verification changed tracked files**

If verification required a code or documentation fix, stage only that fix and
commit:

```powershell
git commit -m "fix(exception): address final verification findings"
```

If verification changed nothing, do not create an empty commit.

## Completion Gate

The exception phase is complete only when:

- `letool-starter-exception` exists in the reactor and dependency management;
- public exception APIs have meaningful Javadoc;
- `log.error("xxx", exception)` is covered by a real Logback rendering test;
- application messages override starter messages;
- starter bundles work without manual basename configuration;
- custom `MessageResolver` beans cause auto-configuration to back off;
- `LetoolException` and old tool exception imports are removed;
- affected module tests pass;
- Javadoc generation passes;
- full-reactor status is reported with fresh command evidence.

After this gate, write the separate distributed-lock implementation plan that
uses this framework for `LockErrorCode` and the lock exception hierarchy.
