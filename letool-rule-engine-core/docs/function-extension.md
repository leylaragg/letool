# 函数扩展

规则函数是宿主向 DSL 提供受控计算能力的唯一入口。函数在注册时冻结元数据，在编译时参与参数和返回类型检查，在求值时只接收不可变参数与只读上下文。

## 两个接口

`RuleFunction` 的公开契约是：

```java
String code();
String semanticVersion();
FunctionSignature signature();
TypeDescriptor returnType();
FunctionCharacteristics characteristics();
FactValue execute(FunctionArguments arguments, FunctionContext context);
```

`RuleFunctionFactory` 用于调用级隔离：

```java
FunctionDescriptor descriptor();
RuleFunction create();
```

工厂的 `descriptor()` 在注册时读取并冻结；`create()` 必须为每次函数调用返回全新实例，且实例元数据必须与冻结描述一致。

## 编码、版本与签名

函数编码以 ASCII 字母开头，后续可含 ASCII 字母、数字、下划线，最长 128 个字符；注册时统一为大写。相同规范编码不能重复注册。

`semanticVersion()` 必须匹配 `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`。只要函数行为、参数解释或返回语义发生兼容性相关变化，就应显式更新版本。编码、语义版本、签名、返回类型和特征共同进入函数目录指纹。

参数通过 `FunctionParameter.required`、`optional`、`varargs` 声明：必填参数必须在可选参数之前；可变参数只能位于最后且本身可选；名称在单个签名内唯一。单个签名和单次调用最多 256 个参数，可变参数也不能突破该上限。

## 函数特征

`FunctionCharacteristics` 包含三个维度：

| 维度 | 值 | 含义 |
|------|----|------|
| 确定性 | `DETERMINISTIC` / `NON_DETERMINISTIC` | 相同输入与上下文是否保证相同结果 |
| 状态读取 | `PURE` / `CONTEXTUAL` | 只读显式参数，或可读取框架提供的只读上下文 |
| 线程模型 | `THREAD_SAFE` / `INVOCATION_SCOPED` | 共享同一实例，或每次调用创建独立实例 |

`FunctionContext` 只暴露 `facts()`、`locale()`、`zoneId()` 和有界字符串 `invocationMetadata()`。它不会提供 Spring 容器、数据库连接、文件、网络客户端或任意属性访问。

共享函数必须声明 `THREAD_SAFE` 并通过 `registerFunction(RuleFunction)` 注册。存在可变调用状态的函数必须声明 `INVOCATION_SCOPED`，并通过 `registerFunction(RuleFunctionFactory)` 注册；两种注册方式混用会被拒绝。

## 完整共享函数示例

```java
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.fact.FactValues;
import io.github.leylaragg.letool.ruleengine.function.FunctionArguments;
import io.github.leylaragg.letool.ruleengine.function.FunctionCharacteristics;
import io.github.leylaragg.letool.ruleengine.function.FunctionContext;
import io.github.leylaragg.letool.ruleengine.function.FunctionDeterminism;
import io.github.leylaragg.letool.ruleengine.function.FunctionEffect;
import io.github.leylaragg.letool.ruleengine.function.FunctionParameter;
import io.github.leylaragg.letool.ruleengine.function.FunctionSignature;
import io.github.leylaragg.letool.ruleengine.function.FunctionThreading;
import io.github.leylaragg.letool.ruleengine.function.RuleFunction;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;

import java.math.BigInteger;

public final class DoubleFunction implements RuleFunction {
    private static final TypeDescriptor INTEGER =
            TypeDescriptor.scalar(TypeKind.INTEGER, false);

    @Override
    public String code() { return "DOUBLE"; }

    @Override
    public String semanticVersion() { return "1.0"; }

    @Override
    public FunctionSignature signature() {
        return FunctionSignature.of(FunctionParameter.required("value", INTEGER));
    }

    @Override
    public TypeDescriptor returnType() { return INTEGER; }

    @Override
    public FunctionCharacteristics characteristics() {
        return FunctionCharacteristics.of(
                FunctionDeterminism.DETERMINISTIC,
                FunctionEffect.PURE,
                FunctionThreading.THREAD_SAFE);
    }

    @Override
    public FactValue execute(FunctionArguments arguments, FunctionContext context) {
        BigInteger value = arguments.get(0).asBigInteger();
        return FactValues.integer(value.multiply(BigInteger.TWO));
    }

    public static ExpressionEngine newEngine() {
        return ExpressionEngine.builder()
                .registerFunction(new DoubleFunction())
                .build();
    }
}
```

`newEngine()` 展示了纯 Java 共享函数注册；构建出的引擎冻结函数元数据和目录指纹。

## 调用级工厂示例

下面的恒等函数只是演示精确的工厂契约；实际使用工厂通常是为了隔离函数内部的短生命周期可变状态。

```java
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.fact.FactValue;
import io.github.leylaragg.letool.ruleengine.function.FunctionArguments;
import io.github.leylaragg.letool.ruleengine.function.FunctionCharacteristics;
import io.github.leylaragg.letool.ruleengine.function.FunctionContext;
import io.github.leylaragg.letool.ruleengine.function.FunctionDescriptor;
import io.github.leylaragg.letool.ruleengine.function.FunctionDeterminism;
import io.github.leylaragg.letool.ruleengine.function.FunctionEffect;
import io.github.leylaragg.letool.ruleengine.function.FunctionParameter;
import io.github.leylaragg.letool.ruleengine.function.FunctionSignature;
import io.github.leylaragg.letool.ruleengine.function.FunctionThreading;
import io.github.leylaragg.letool.ruleengine.function.RuleFunction;
import io.github.leylaragg.letool.ruleengine.function.RuleFunctionFactory;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;

public final class IdentityFunctionFactory implements RuleFunctionFactory {
    private static final TypeDescriptor STRING =
            TypeDescriptor.scalar(TypeKind.STRING, false);

    private static final FunctionDescriptor DESCRIPTOR = FunctionDescriptor.of(
            "IDENTITY",
            "1.0",
            FunctionSignature.of(FunctionParameter.required("value", STRING)),
            STRING,
            FunctionCharacteristics.of(
                    FunctionDeterminism.DETERMINISTIC,
                    FunctionEffect.PURE,
                    FunctionThreading.INVOCATION_SCOPED));

    @Override
    public FunctionDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public RuleFunction create() {
        return new RuleFunction() {
            public String code() { return DESCRIPTOR.code(); }
            public String semanticVersion() { return DESCRIPTOR.semanticVersion(); }
            public FunctionSignature signature() { return DESCRIPTOR.signature(); }
            public TypeDescriptor returnType() { return DESCRIPTOR.returnType(); }
            public FunctionCharacteristics characteristics() { return DESCRIPTOR.characteristics(); }
            public FactValue execute(FunctionArguments arguments, FunctionContext context) {
                return arguments.get(0);
            }
        };
    }

    public static ExpressionEngine newEngine() {
        return ExpressionEngine.builder()
                .registerFunction(new IdentityFunctionFactory())
                .build();
    }
}
```

## Spring Boot 收集

`letool-starter-rule-engine` 自动收集所有 `RuleFunction` 和 `RuleFunctionFactory` Bean，并按 Spring `@Order` 顺序注册。共享实例和工厂仍必须满足各自线程模型；`@Order` 不用于解决重复编码，重复编码会使引擎构建失败。

```java
import io.github.leylaragg.letool.ruleengine.function.RuleFunction;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration(proxyBeanMethods = false)
public class RuleFunctionsConfiguration {
    @Bean
    @Order(10)
    RuleFunction doubleFunction() {
        return new DoubleFunction();
    }
}
```

## 错误边界

- 未知函数、参数数量和参数类型错误在编译期返回结构化诊断。
- 函数抛出的 `RuntimeException` 被包装为 `FUNCTION_EXECUTION_ERROR`；底层异常文本不会成为诊断参数。
- 函数返回 `null` 或与声明返回类型不一致，返回 `RUNTIME_TYPE_MISMATCH`。
- 工厂描述、创建结果、元数据或线程模型不合约时，注册或求值失败。
- 框架只能验证声明和可观察结果，不能自动证明 `PURE`、确定性或线程安全；这些特征由扩展作者负责。

规则函数不应直接执行 IO、网络、数据库读写或业务动作。需要外部状态时，优先在求值前读取并规范化为事实；需要副作用时，在求值结果确定后由宿主执行。
