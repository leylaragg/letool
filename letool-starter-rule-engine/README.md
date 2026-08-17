# Letool Rule Engine Spring Boot Starter

`letool-starter-rule-engine` 自动配置通用规则表达式引擎：绑定资源限制、提供默认编译器和求值器、收集规则函数 Bean，并接入 Letool 异常消息国际化。它不提供规则链编排、数据库持久化、缓存或动作执行。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-rule-engine</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

## 最小 Bean 用法

引入依赖后，应用可直接注入自动配置的 `ExpressionEngine`：

```java
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.compile.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public final class AdultRule {
    private final ExpressionEngine engine;
    private final CompiledExpression expression;

    public AdultRule(ExpressionEngine engine) {
        this.engine = engine;
        FactContract contract = FactContract.builder("customer-v1")
                .path("customer.age",
                        TypeDescriptor.scalar(TypeKind.INTEGER, false))
                .build();
        this.expression = engine.compile("${customer.age} >= 18", contract)
                .requireCompiled();
    }

    public boolean matches(int age) {
        RuleFacts facts = RuleFacts.fromMap(
                Map.of("customer", Map.of("age", age)));
        return engine.evaluate(expression, facts, EvaluationOptions.defaults())
                .requireBoolean();
    }
}
```

应用声明的 `RuleFunction` 和 `RuleFunctionFactory` Bean 会自动注册；用 `@Order` 指定顺序，用 `@Primary` 解决自定义编译器、求值器或消息解析器的候选歧义。

## 与其他规则模块的关系

- [core](../letool-rule-engine-core/README.md)：纯 Java DSL 编译与求值，本 Starter 的内核。
- `letool-starter-rule-engine`：Spring Boot 装配层，不改变 DSL 语义。
- [LiteFlow Starter](../letool-starter-rule-liteflow/README.md)：规则链和业务节点编排，与本 Starter 无替换关系、无运行时耦合。

## 配置与深入文档

完整十项 YAML、Bean 退让、异常国际化回退、并发与故障排查见 [Spring Boot 集成](../letool-rule-engine-core/docs/spring-boot-integration.md)。从首次接入到发布运维见[完整使用手册](../letool-rule-engine-core/docs/user-guide.md)，EDC 改造见 [EDC 接入指南](../letool-rule-engine-core/docs/edc-integration-guide.md)，其他专题从 [core 文档导航](../letool-rule-engine-core/README.md#文档导航)进入。

框架不建表，也不依赖 JDBC、JPA 或 MyBatis。规则来源由业务决定；数据库 repository 和表结构不是 Starter API。
