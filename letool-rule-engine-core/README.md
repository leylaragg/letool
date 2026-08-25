# Letool Rule Engine Core

`letool-rule-engine-core` 是纯 Java 17 的强类型标量表达式内核。它完整拥有词法、语法、类型分析、编译和求值语义；宿主只提供事实契约、不可变事实、受控函数和资源限制。

它可以被临床、保险、风控、订单等不同系统复用，不包含患者、保单、项目、数据库或业务会话模型。

## 能力边界

| Letool 负责 | 宿主负责 |
| --- | --- |
| DSL 编译、强类型校验和标量求值 | 规则来源、审批、发布和持久化 |
| 精确整数与小数运算 | 业务数据读取与事实组装 |
| 事实和函数依赖分析 | 多规则筛选、优先级和动作编排 |
| 资源限制、诊断和安全轨迹 | 缓存介质、租户隔离、审计和回滚 |
| 执行环境与产物内容摘要 | 旧规则兼容、翻译和双跑比对 |

框架不提供规则集、数据库访问、脚本执行、动作副作用或 `EvaluationContext`。需要外部数据时，宿主应在求值前读取并转成显式事实；需要动作时，在求值结束后由宿主按业务顺序执行。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-rule-engine-core</artifactId>
    <version>3.0.0</version>
</dependency>
```

## 最小示例

```java
import io.github.leylaragg.letool.ruleengine.api.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;

import java.math.BigDecimal;
import java.util.Map;

ExpressionEngine engine = ExpressionEngine.builder().build();
FactContract contract = FactContract.builder("order-v1")
        .path("order.amount", TypeDescriptor.scalar(TypeKind.DECIMAL, false))
        .path("order.paid", TypeDescriptor.scalar(TypeKind.BOOLEAN, false))
        .build();

CompiledExpression expression = engine.compile(
        "${order.amount} >= 100.00 AND ${order.paid}", contract)
        .requireCompiled();

RuleFacts facts = RuleFacts.fromMap(Map.of(
        "order", Map.of("amount", new BigDecimal("128.50"), "paid", true)));

boolean accepted = engine.evaluate(
        expression, facts, EvaluationOptions.defaults()).requireBoolean();
```

临床系统可以把字段快照建模为 `subject.age`、`visit.date`，保险系统可以把事实建模为 `applicant.age`、`policy.amount`。两类系统使用同一个 `ExpressionEngine`，差异只存在于宿主的事实契约、事实组装和函数目录中。

## 3.0 公共契约

- `ExpressionEngine` 是唯一编译与求值门面；编译器和求值器不再是可分别替换的 SPI。
- `CompilationRequest`、`EvaluationRequest` 是完整调用参数；便捷重载只委托同一主流程。
- `CompiledExpression` 是只读产物，不公开 AST，也不是跨 Letool 大版本的序列化协议。
- `ExecutionModelDescriptor.environmentDigest()` 覆盖语言、内核语义、类型目录、函数目录和编译选项，可纳入宿主缓存键。
- `artifactDigest()`、`contractDigest()`、`catalogDigest()` 表示相应内容摘要，不用于加密或授权。
- `DependencyCoverage.DYNAMIC` 表示函数可能读取显式参数以外的事实，宿主做增量规则筛选时必须保守回退。

## 类型与函数原则

Letool 不执行含糊的业务转换。`"12" > 2` 在编译期失败；字符串转数值、空字符串含义、模糊日期和多状态传播由宿主兼容层处理。整数和小数分别使用 `BigInteger`、`BigDecimal` 语义。

LIKE、正则等运算可先由宿主注册强类型 `RuleFunction`。函数若只读取参数，应显式返回 `FunctionFactAccess.EXPLICIT_ARGUMENTS_ONLY`；默认值为 `DYNAMIC_FACTS`，避免错误宣称静态依赖完整。

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [架构](docs/architecture.md) | 唯一门面、执行模型、编译与求值链 |
| [完整使用手册](docs/user-guide.md) | 契约、编译、缓存、求值和上线检查 |
| [DSL 参考](docs/dsl-reference.md) | 字面量、路径、函数和运算符 |
| [事实与类型](docs/facts-and-types.md) | 输入白名单、事实快照和严格类型 |
| [函数扩展](docs/function-extension.md) | 函数签名、依赖声明和线程模型 |
| [诊断与追踪](docs/diagnostics-and-tracing.md) | 结构化诊断和安全摘要 |
| [安全与限制](docs/security-and-limits.md) | 不可信输入、预算和依赖门禁 |
| [Spring Boot 集成](docs/spring-boot-integration.md) | Starter 配置和完整引擎退让 |
| [EDC 接入指南](docs/edc-integration-guide.md) | EDC 兼容分类、路由、双跑和回滚 |
| [2.1 到 3.0 迁移](../docs/migration/rule-engine-2.1-to-3.0.md) | 破坏性 API 变化与替代方式 |
