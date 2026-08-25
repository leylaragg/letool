# Letool Rule Engine Spring Boot Starter

`letool-starter-rule-engine` 为 3.0 标量表达式内核绑定资源限制、收集函数 Bean、接入诊断国际化，并装配一个完整 `ExpressionEngine`。它不提供数据库、缓存、规则集或动作编排。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-rule-engine</artifactId>
    <version>3.0.0</version>
</dependency>
```

## 使用方式

```java
import io.github.leylaragg.letool.ruleengine.api.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
final class AdultRule {
    private final ExpressionEngine engine;
    private final CompiledExpression expression;

    AdultRule(ExpressionEngine engine) {
        this.engine = engine;
        FactContract contract = FactContract.builder("customer-v1")
                .path("customer.age", TypeDescriptor.scalar(TypeKind.INTEGER, false))
                .build();
        this.expression = engine.compile("${customer.age} >= 18", contract)
                .requireCompiled();
    }

    boolean matches(int age) {
        RuleFacts facts = RuleFacts.fromMap(
                Map.of("customer", Map.of("age", age)));
        return engine.evaluate(expression, facts, EvaluationOptions.defaults())
                .requireBoolean();
    }
}
```

应用声明的 `RuleFunction` 和 `RuleFunctionFactory` Bean 会自动注册；`@Order` 只固定注册遍历顺序，不允许重复函数编码覆盖。若应用声明完整 `ExpressionEngine` Bean，Starter 整体退让该引擎，不会混合 Letool 编译流程和第三方求值流程。

完整配置、退让规则和排障见 [Spring Boot 集成](../letool-rule-engine-core/docs/spring-boot-integration.md)。核心模型见 [core README](../letool-rule-engine-core/README.md)，破坏性升级见 [2.1 到 3.0 迁移指南](../docs/migration/rule-engine-2.1-to-3.0.md)。
