# Letool Rule Engine Core

`letool-rule-engine-core` 是纯 Java 17 的强类型表达式内核。它把规则源码先编译为不可变 `CompiledExpression`，再用不可变 `RuleFacts` 快照求值；运行时只依赖 `letool-exception-core`，不依赖 Spring、数据库、LiteFlow、脚本引擎或缓存。

## 适用范围

本模块适合条件判断和无副作用值计算，例如“订单金额是否达到阈值”。当前不提供规则集、多规则聚合、动作执行、缓存、持久化或数据库表结构。

三个规则相关模块职责不同：

| 模块 | 职责 |
|------|------|
| `letool-rule-engine-core` | 纯 Java DSL 编译、类型检查与表达式求值 |
| `letool-starter-rule-engine` | 为 core 提供 Spring Boot 自动配置、外部化限制和消息国际化接入 |
| `letool-starter-rule-liteflow` | LiteFlow 节点和规则链编排，可执行带副作用的业务节点 |

通用规则引擎与 LiteFlow 没有替换关系，也没有运行时耦合。业务可以分别使用；若确有需要，也可由 LiteFlow 节点显式调用 `ExpressionEngine`。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-rule-engine-core</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

## 最小纯 Java 示例

下面示例只使用当前公开 API，可直接放入 Java 17 方法中执行：

```java
import com.github.leyland.letool.ruleengine.api.ExpressionEngine;
import com.github.leyland.letool.ruleengine.compile.CompiledExpression;
import com.github.leyland.letool.ruleengine.evaluate.EvaluationOptions;
import com.github.leyland.letool.ruleengine.fact.RuleFacts;
import com.github.leyland.letool.ruleengine.type.FactContract;
import com.github.leyland.letool.ruleengine.type.TypeDescriptor;
import com.github.leyland.letool.ruleengine.type.TypeKind;

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
        "order", Map.of(
                "amount", new BigDecimal("128.50"),
                "paid", true)));

boolean accepted = engine.evaluate(
        expression, facts, EvaluationOptions.defaults()).requireBoolean();
```

`FactContract` 是编译期允许访问的路径和类型目录；`RuleFacts` 是运行期事实快照。不要用空契约编译后再期待运行时动态发现路径。

## 文档导航

| 文档 | 内容 |
|------|------|
| [架构](docs/architecture.md) | 模块依赖、编译/求值链、快照、线程与失败模型 |
| [完整使用手册](docs/user-guide.md) | 从契约建模、编译发布到缓存、监控和生产检查 |
| [DSL 参考](docs/dsl-reference.md) | 字面量、路径、函数、运算符、优先级与边界 |
| [事实与类型](docs/facts-and-types.md) | Java 输入、事实快照、路径、契约和类型指纹 |
| [函数扩展](docs/function-extension.md) | `RuleFunction`、工厂、签名和线程模型 |
| [诊断与追踪](docs/diagnostics-and-tracing.md) | 结果对象、诊断位置、国际化和安全轨迹 |
| [安全与限制](docs/security-and-limits.md) | 不可信输入、九项预算、硬边界和依赖门禁 |
| [Spring Boot 集成](docs/spring-boot-integration.md) | Starter 配置、Bean 退让、函数收集和排障 |
| [EDC 接入指南](docs/edc-integration-guide.md) | EDC 职责边界、发布缓存、双跑迁移和回滚 |

## 边界与演进

规则源码可以来自配置文件、数据库、远程配置中心或管理平台，但来源读取属于宿主业务层。框架不建表，也不依赖 JDBC、JPA 或 MyBatis。当前实现只承诺单表达式能力；规则集、动作计划、持久化与缓存如果未来出现，必须以独立 API 或可选模块交付，不能从本文推断为已经实现。
