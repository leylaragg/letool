# Spring Boot 集成

`letool-starter-rule-engine` 为 `letool-rule-engine-core` 提供 Spring Boot 3.5 自动配置。它依赖 core、`letool-starter-exception` 和 Spring Boot autoconfigure；不加入数据库、缓存或 LiteFlow。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-rule-engine</artifactId>
    <version>2.0.0-beta.1</version>
</dependency>
```

## 完整配置

下面列出总开关和全部九项限制，数值均为默认值：

```yaml
letool:
  rule-engine:
    enabled: true
    limits:
      max-source-length: 16384
      max-tokens: 2048
      max-ast-depth: 128
      max-function-calls: 1024
      max-trace-nodes: 4096
      max-summary-length: 512
      max-fact-depth: 64
      max-fact-nodes: 100000
      max-container-size: 10000
```

任一限制为零或负数会在创建不可变 `EngineLimits` 时失败。配置只定义上限，不能越过 core 的固定硬边界。`enabled=false` 会使整组自动配置退让，包括属性 Bean、消息资源贡献和引擎协作者。

## 自动配置 Bean 与退让

| Bean 类型/名称 | 默认实现或用途 | 退让规则 |
|----------------|----------------|----------|
| `RuleEngineProperties` | 绑定 `letool.rule-engine` | 总开关关闭时不创建 |
| `ExpressionCompiler` | `DefaultExpressionCompiler` | 存在同类型 Bean 时退让 |
| `ExpressionEvaluator` | `DefaultExpressionEvaluator` | 存在同类型 Bean 时退让 |
| `DiagnosticMessageResolver` | 通用消息适配器或中文回退 | 存在同类型 Bean 时退让 |
| `ExpressionEngine` | 固化配置与函数目录的默认引擎 | 存在同类型 Bean 时退让 |
| `ruleEngineMessageBundle` | `i18n/letool-rule-engine/messages` 资源贡献 | 启用时固定创建 |

自定义某个协作者只替换该 Bean，不会自动关闭其他默认 Bean。多个 `ExpressionCompiler`、`ExpressionEvaluator` 或构造引擎所需的同类型候选且没有 `@Primary` 时，Spring 会按标准规则以 `NoUniqueBeanDefinitionException` 启动失败。

诊断适配器通过 `ObjectProvider<MessageResolver>.getIfAvailable()` 取得通用解析器，因此多个 `MessageResolver` 候选同样必须由 `@Primary` 或唯一候选消除歧义；框架不会按注册顺序静默选择。

## 函数 Bean 与顺序

所有 `RuleFunction` 和 `RuleFunctionFactory` Bean 会通过 `orderedStream()` 收集；可以用 `@Order` 表达确定的注册顺序：

```java
@Configuration(proxyBeanMethods = false)
class RuleFunctionsConfiguration {
    @Bean
    @Order(10)
    RuleFunction doubleFunction() {
        return new DoubleFunction();
    }
}
```

`RuleFunction` 必须是 `THREAD_SAFE`；`RuleFunctionFactory` 描述必须是 `INVOCATION_SCOPED`。重复函数编码仍会失败，`@Order` 不构成覆盖策略。目录指纹按规范编码生成，Bean 声明顺序变化不会改变相同目录的语义指纹。

## 国际化与异常模块关闭

Starter 提供默认、`zh_CN` 和英文规则消息。应用自己的 `messageSource` 对相同错误码的消息优先，可逐码覆盖；动态诊断参数由规则诊断格式化边界统一追加。

异常功能有两种关闭方式，回退不同：

| 配置 | 结果 |
|------|------|
| `letool.exception.i18n.enabled=false` | 异常模块仍提供 `DefaultMessageResolver`，规则诊断继续使用适配器和诊断码默认文案 |
| `letool.exception.enabled=false` | 不存在通用 `MessageResolver`，规则 Starter 使用 `ChineseDiagnosticMessageResolver`；传入其他 Locale 仍返回中文兜底 |

应用也可以直接声明一个 `DiagnosticMessageResolver` Bean 完全替换默认诊断展示层。

## 并发使用

自动配置创建一个不可变 `ExpressionEngine` 快照，可被多个线程共享。每次编译和求值使用独立会话。并发安全仍取决于应用提供的自定义编译器、求值器以及 `THREAD_SAFE` 函数是否兑现契约；调用级可变函数应使用工厂。

Spring 配置中的事实预算不会自动作用于宿主先行创建的 `RuleFacts`。如果输入可能很大，应把同一限制策略显式传给 `RuleFacts.fromMap(source, limits)`，或在业务入口先做规模控制。

## 数据库与规则来源

Starter 不建表，不依赖 JDBC、JPA 或 MyBatis，不提供 repository Bean。规则源码可以来自 `application.yml`、文件、数据库、配置中心或远程管理服务，但读取和发布由业务层负责。不要为接入 Starter 被迫采用某种表结构；若业务自定义 `RuleSourceRepository`，它仍是应用代码而不是框架 API。

## 故障排查

| 现象 | 检查项 |
|------|--------|
| 没有任何规则引擎 Bean | 检查 `letool.rule-engine.enabled` 是否为 `false`，以及 `ExpressionEngine` 类是否在 classpath |
| 启动时报限制参数无效 | 九项限制必须全部为正数；检查环境变量绑定后的最终值 |
| 启动时报 Bean 不唯一 | 为编译器、求值器或 `MessageResolver` 保留唯一候选，或明确 `@Primary` |
| 启动时报函数注册冲突 | 检查大小写规范化后是否存在重复函数编码 |
| 编译返回 `UNKNOWN_FUNCTION` | 确认函数是 Spring Bean，且应用上下文已用 Starter 创建引擎 |
| 求值返回 `FINGERPRINT_MISMATCH` | 重新用当前应用的事实契约和函数目录编译，不要跨不兼容环境复用产物 |
| 英文 Locale 仍显示中文 | 检查异常模块是否整体关闭；整体关闭会使用中文 core 回退 |
| 应用消息未覆盖 | 使用稳定错误码作为 message key，并检查应用 `messageSource` Locale 与加载顺序 |
| 追踪被截断 | 调整 `max-trace-nodes` 或减少表达式复杂度；不要把截断误判为求值失败 |

更多 core API 见[模块 README](../README.md)，从编译发布到生产监控见[完整使用手册](user-guide.md)，安全预算见[安全与限制](security-and-limits.md)。EDC 系统接入、双跑和回滚流程见 [EDC 接入指南](edc-integration-guide.md)。
