# Spring Boot 集成

`letool-starter-rule-engine` 为 Letool 3.0 提供 Spring Boot 自动配置，不增加数据库、缓存或规则编排依赖。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-rule-engine</artifactId>
    <version>3.0.0</version>
</dependency>
```

## 配置

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

所有限制必须为正数。`enabled=false` 时，属性、消息资源贡献和引擎自动配置一起关闭。

## 自动配置边界

| Bean | 行为 |
| --- | --- |
| `RuleEngineProperties` | 绑定开关和限制 |
| `ExpressionEngine` | 用限制、函数和工厂建立完整不可变引擎 |
| `DiagnosticMessageResolver` | 适配通用消息解析器或使用中文回退 |
| `ruleEngineMessageBundle` | 贡献规则诊断资源 |

Starter 收集所有 `RuleFunction` 和 `RuleFunctionFactory` Bean。重复函数编码直接失败；`@Order` 不构成覆盖策略。函数目录摘要按规范内容计算，等价目录不受 Bean 遍历顺序影响。

若应用声明自己的完整 `ExpressionEngine` Bean，默认引擎整体退让。3.0 不创建独立编译器或求值器 Bean，也不支持只替换流水线中的一个部分。

`ValueSummarizer` 当前通过纯 Java Builder 配置。Spring 应用需要自定义轨迹摘要时，可以声明一个用 `ExpressionEngine.builder().valueSummarizer(...)` 构建的完整引擎 Bean；摘要策略不参与求值语义和环境摘要。

## 并发与事实预算

自动配置得到的引擎可多线程共享，每次求值使用独立会话。共享函数必须声明并兑现 `THREAD_SAFE`；需要调用级状态时使用 `RuleFunctionFactory`。

Starter 限制不会重新规范化宿主已经创建的事实。大输入应显式使用同一 `EngineLimits` 调用 `RuleFacts.fromMap(source, limits)`。

## 故障排查

| 现象 | 检查项 |
| --- | --- |
| 没有引擎 Bean | 检查总开关和 classpath |
| 启动时报限制无效 | 九项限制必须全部为正数 |
| 启动时报函数冲突 | 检查规范化后的函数编码 |
| 编译返回未知函数 | 确认函数 Bean 已进入当前应用上下文 |
| 求值返回执行环境不匹配 | 使用当前引擎和事实契约重新编译，不跨环境复用产物 |
| 轨迹被截断 | 收紧表达式或调整轨迹节点预算 |

规则来源、表结构、缓存和发布由应用负责。Starter 不提供 Repository，也不会访问数据库。
