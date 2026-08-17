# 诊断与追踪

## 两类结果对象

`CompilationResult<CompiledExpression>` 表示编译成功产物或编译诊断。`isSuccessful()` 判断状态，`diagnostics()` 返回不可变列表，只有成功时才能调用 `requireCompiled()`。

`ExpressionEvaluationResult` 表示求值成功值或运行期失败。成功时可用 `requireValue()`，布尔表达式可用 `requireBoolean()`；失败时读取 `diagnostics()`、`trace()` 和可选 `failureCause()`。失败结果调用 `requireValue()` 会抛出保存的同一 `RuleEngineException`。

不要用异常消息区分规则错误。机器判断应使用 `RuleDiagnostic.code()`；展示文本通过 `DiagnosticMessageResolver` 解析。

## `RuleDiagnostic`

每条诊断包含：

| 字段 | 含义 |
|------|------|
| `code()` | 稳定的 `RuleDiagnosticCode`，同时实现通用 `ErrorCode` |
| `severity()` | `INFO`、`WARNING` 或 `ERROR` |
| `phase()` | `LEXICAL`、`SYNTAX`、`SEMANTIC`、`RUNTIME`；`PLANNING` 为类型定义，当前表达式链不产生动作规划 |
| `startPosition()` | 零基 UTF-16 起始偏移，包含该位置 |
| `endPosition()` | 零基 UTF-16 结束偏移，不包含该位置 |
| `arguments()` | 已校验、不可变、安全可渲染参数，最多 16 项 |
| `suggestedExpression()` | 可选修正表达式；没有建议时为 `null` |

位置使用 Java `String` 下标语义，即 UTF-16 代码单元的左闭右开区间 `[startPosition, endPosition)`。编辑器高亮可直接使用 `source.substring(start, end)`；不要把偏移误当成 Unicode 码点或 UTF-8 字节。

`RuleDiagnosticCode.getCode()` 是对外稳定机器码，例如 `RULE_ENGINE_COMPILE_SEMANTIC_002`；默认中文文案可替换，不能作为协议字段。

## 消息解析

core 内置 `ChineseDiagnosticMessageResolver`，使用诊断码自带的中文兜底文案。Starter 会在存在通用 `MessageResolver` 时适配国际化资源。统一格式包含机器码前缀，动态参数按安全边界追加，不交给通用对象 `toString()`。

```java
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.compile.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.compile.CompilationResult;
import io.github.leylaragg.letool.ruleengine.diagnostic.ChineseDiagnosticMessageResolver;
import io.github.leylaragg.letool.ruleengine.diagnostic.RuleDiagnostic;
import io.github.leylaragg.letool.ruleengine.type.FactContract;

import java.util.Locale;

ExpressionEngine engine = ExpressionEngine.builder().build();
FactContract empty = FactContract.builder("empty-v1").build();
CompilationResult<CompiledExpression> result =
        engine.compile("${missing} > 0", empty);

if (!result.isSuccessful()) {
    RuleDiagnostic diagnostic = result.diagnostics().get(0);
    String message = new ChineseDiagnosticMessageResolver()
            .resolve(diagnostic, Locale.SIMPLIFIED_CHINESE);
    System.out.println(message);
}
```

异常原因链属于技术日志边界。`failureCause()` 可供受控日志或调试使用，但不要把 `getMessage()` 原样返回给规则作者或 API 客户端；框架有意不把宿主函数、自定义求值器或消息解析器的原始异常文本写入诊断展示。

## 开启追踪

默认 `EvaluationOptions.defaults()` 使用 `Locale.ROOT`、UTC、关闭追踪和默认限制。显式开启：

```java
import io.github.leylaragg.letool.ruleengine.api.EngineLimits;
import io.github.leylaragg.letool.ruleengine.api.ExpressionEngine;
import io.github.leylaragg.letool.ruleengine.compile.CompiledExpression;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationOptions;
import io.github.leylaragg.letool.ruleengine.evaluate.EvaluationTrace;
import io.github.leylaragg.letool.ruleengine.evaluate.ExpressionEvaluationResult;
import io.github.leylaragg.letool.ruleengine.evaluate.TraceNode;
import io.github.leylaragg.letool.ruleengine.fact.RuleFacts;
import io.github.leylaragg.letool.ruleengine.type.FactContract;
import io.github.leylaragg.letool.ruleengine.type.TypeDescriptor;
import io.github.leylaragg.letool.ruleengine.type.TypeKind;

import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

ExpressionEngine engine = ExpressionEngine.builder().build();
FactContract contract = FactContract.builder("customer-v1")
        .path("customer.age", TypeDescriptor.scalar(TypeKind.INTEGER, false))
        .build();
CompiledExpression expression = engine.compile(
        "${customer.age} >= 18", contract).requireCompiled();
RuleFacts facts = RuleFacts.fromMap(
        Map.of("customer", Map.of("age", 20)));

EvaluationOptions options = EvaluationOptions.of(
        Locale.ROOT,
        ZoneId.of("UTC"),
        true,
        EngineLimits.defaults());

ExpressionEvaluationResult result = engine.evaluate(expression, facts, options);
EvaluationTrace trace = result.trace();
for (TraceNode node : trace.nodes()) {
    System.out.printf("%s [%d,%d) %s %s%n",
            node.nodeType(),
            node.startPosition(),
            node.endPosition(),
            node.resultCategory(),
            node.summary());
}
if (trace.isTruncated()) {
    System.out.println("后续节点因轨迹预算未记录");
}
```

`EvaluationTrace` 明确记录是否启用、按节点完成顺序排列的不可变 `TraceNode`，以及是否被截断。每个节点包含 AST 简类名、源码范围、`VALUE`/`FAILURE`、运行期 `TypeDescriptor` 和安全摘要。失败节点使用未知占位类型。

## 值摘要与脱敏

默认 `DefaultValueSummarizer` 不深遍历容器：对象和数组只显示大小；长整数、小数只显示结构信息；字符串只保留受限前缀。`maxTraceNodes` 与 `maxSummaryLength` 分别限制节点数和单摘要长度。

高敏感业务可以实现 `ValueSummarizer`，再构造 `new DefaultExpressionEvaluator(customSummarizer)` 并注入引擎。Starter 不会单独自动收集 `ValueSummarizer` Bean；Spring 应用应提供自定义 `ExpressionEvaluator` Bean。摘要器应返回有界、适合展示的文本；`null`、异常和过长输出仍会在求值会话边界回退、清理或截断。

追踪用于解释求值过程，不是审计日志、性能剖析器或事实导出接口。生产环境应按需开启，并根据数据分类决定是否允许字符串前缀进入日志。
