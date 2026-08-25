# 事实与类型

## 从 Java 输入建立快照

`RuleFacts.fromMap(Map<String, ?>)` 和 `FactValues.fromJavaValue(Object)` 把白名单 Java 值深层规范化为不可变事实树。支持：

- `null`、`String`、`Character`、`Boolean`；
- `Byte`、`Short`、`Integer`、`Long`、`BigInteger`；
- 有限的 `Float`、`Double` 与 `BigDecimal`；
- `LocalDate`、`LocalDateTime`、`Instant`；
- 字符串键 `Map`、`Collection`、对象数组和基本类型数组。

整数统一为 `BigInteger`，小数统一为 `BigDecimal`。输入 Map、Collection 和数组会按遍历顺序深拷贝；之后修改宿主容器不会改变事实。`toSafeJavaValue()` 返回不可修改的深层 Java 视图，不泄漏内部可变状态。

任意 POJO、`Class`、反射对象、线程、回调、非字符串键、空白键、`NaN`、无穷值都会被拒绝。循环引用也会被拒绝。共享但无环的对象图可以输入，不过每条引用分支都会展开为独立的最终树节点，因此会分别消耗节点预算；这也避免事实快照保留宿主对象身份。

## `FactValue` 与工厂

扩展函数返回值必须是 `FactValue`。使用公开 `FactValues` 工厂，不要自行伪造实现：

```java
FactValue text = FactValues.string("ok");
FactValue count = FactValues.integer(42);
FactValue ratio = FactValues.decimal(new BigDecimal("0.75"));
FactValue day = FactValues.date(LocalDate.of(2026, 8, 14));
FactValue none = FactValues.nullValue();
FactValue nested = FactValues.fromJavaValue(Map.of("scores", List.of(80, 90)));
```

`FactValue` 是 sealed 接口，分类见 `FactKind`。读取通用值用 `toSafeJavaValue()`；整数还可用 `asBigInteger()`。

## 事实路径

`FactPathParser.parse` 同时接受 `customer.age` 和完整 `${customer.age}`。路径由属性段和非负下标段组成，例如 `order.items[0].price`。`RuleFacts.resolve(path)` 在缺失时返回空 `Optional`；`require(path)` 要求路径存在。

路径解析不会反射访问 POJO，也不支持通配符、动态键、负下标、方法或 `.class`。路径经过规范化后，`FactPath.toString()` 给出唯一文本。

## `FactContract` 与 `TypeDescriptor`

规则编译前必须声明允许引用的路径：

```java
FactContract contract = FactContract.builder("customer-v2")
        .path("customer.id", TypeDescriptor.scalar(TypeKind.INTEGER, false))
        .path("customer.nickname", TypeDescriptor.scalar(TypeKind.STRING, true))
        .path("customer.tags", TypeDescriptor.array(
                TypeDescriptor.scalar(TypeKind.STRING, false), false))
        .path("request", TypeDescriptor.object(false))
        .build();
```

`scalar(kind, nullable)` 声明标量，`array(elementType, nullable)` 分别声明数组自身与元素可空性，`object(nullable)` 声明字符串键对象。`UNKNOWN` 和 `NULL` 主要用于类型分析内部占位；业务契约应声明真实预期类型。

同一契约不能重复注册路径，也不能同时注册互为父子的路径，例如 `customer` 与 `customer.id`。这保证每个表达式依赖只有一个明确类型。契约版本必须由宿主显式维护；`contractDigest()` 对“版本 + 排序后的路径和规范类型”计算 SHA-256，因此注册顺序不影响摘要，但版本、路径、类型或可空性变化都会改变摘要。

编译产物只记录实际引用路径的类型化依赖；求值前也只校验这些依赖。运行时允许 `INTEGER` 赋给声明为 `DECIMAL` 的路径，但不允许任意字符串、时间或布尔隐式转换；实际 `NULL` 只能赋给 `nullable=true` 的类型。

## 事实相关三项预算

九项 `EngineLimits` 中有三项直接约束规范化：

| 限制 | 默认值 | 计数方式 |
|------|--------|----------|
| `maxFactDepth` | 64 | 根值从深度 1 开始，约束容器递归层数 |
| `maxFactNodes` | 100000 | 最终事实树展开节点总数；共享分支重复计数 |
| `maxContainerSize` | 10000 | 单个 Map、Collection 或数组的实际元素数 |

要让事实和引擎使用同一预算，应把同一个 `EngineLimits` 同时交给引擎构建器与 `RuleFacts.fromMap(source, limits)`。单次 `EvaluationOptions` 只能在求值时进一步收紧限制，不能重新规范化一个已创建的事实快照。

## 完整可编译示例

```java
import io.github.leylaragg.letool.ruleengine.api.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

EngineLimits limits = EngineLimits.defaults();
ExpressionEngine engine = ExpressionEngine.builder().limits(limits).build();

FactContract contract = FactContract.builder("checkout-v1")
        .path("cart.total", TypeDescriptor.scalar(TypeKind.DECIMAL, false))
        .path("cart.items[0].sku", TypeDescriptor.scalar(TypeKind.STRING, false))
        .build();

CompiledExpression expression = engine.compile(
        "${cart.total} >= 99.00 AND ${cart.items[0].sku} IS NOT NULL", contract)
        .requireCompiled();

Map<String, Object> source = Map.of(
        "cart", Map.of(
                "total", new BigDecimal("120.50"),
                "items", List.of(Map.of("sku", "A-100"))));
RuleFacts facts = RuleFacts.fromMap(source, limits);

boolean matched = engine.evaluate(
        expression, facts, EvaluationOptions.defaults()).requireBoolean();
```

数组下标是契约路径的一部分；声明 `cart.items` 为数组并不会自动授权或推导任意元素子字段。当前阶段没有通用集合遍历或通配路径。
