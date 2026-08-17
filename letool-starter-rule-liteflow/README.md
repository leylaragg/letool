# letool-starter-rule-liteflow

`letool-starter-rule-liteflow` 是基于 LiteFlow 2.12.4 的规则链执行薄封装，不维护独立的规则引擎。模块重命名只用于明确 LiteFlow 技术边界，现有 `io.github.leylaragg.letool.rule.*` Java API 和配置保持不变。

模块只提供以下能力：

- `RuleTemplate`：校验规则链标识并调用 LiteFlow 原生 `FlowExecutor`。
- `RuleException`、`RuleErrorCode`：把参数错误和执行失败映射为 Letool 统一异常协议。
- `RuleAutoConfiguration`：在 LiteFlow 已提供 `FlowExecutor` 时自动注册 `RuleTemplate`，并允许用户 Bean 覆盖。

规则编排、组件模型、规则源、热刷新、脚本语言、执行监控和高级执行选项均由 LiteFlow
原生能力负责。需要这些能力时，请直接使用 LiteFlow 官方配置和扩展点。

## Maven 坐标

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-rule-liteflow</artifactId>
    <version>${letool.version}</version>
</dependency>
```

模块会传递 LiteFlow Spring Boot Starter 和 `letool-starter-exception`。LiteFlow
版本由 Letool 父 POM 统一管理，当前为 `2.12.4`。

## 快速开始

### 1. 配置 LiteFlow 规则源

Letool 不提供 `letool.rule.*` 配置。规则文件位置以及刷新、脚本等能力全部使用
LiteFlow 原生配置：

```yaml
liteflow:
  rule-source: classpath:rules/risk-chain.xml
  print-banner: false
```

示例规则文件 `src/main/resources/rules/risk-chain.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<flow>
    <chain id="riskCheck">
        THEN(ageValidator);
    </chain>
</flow>
```

其他规则源格式、外部规则源和热刷新配置以 LiteFlow 官方文档为准，Letool
不复制或转换这些配置。

### 2. 使用 LiteFlow 官方组件扩展点

```java
import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;

@LiteflowComponent("ageValidator")
public class AgeValidator extends NodeComponent {

    /**
     * 校验请求中的年龄。
     */
    @Override
    public void process() {
        UserRiskRequest request = this.getRequestData();
        if (request.age() < 18) {
            throw new IllegalArgumentException("年龄不满足要求");
        }
    }
}
```

条件组件、脚本组件、上下文、组件生命周期及其他组件类型同样使用 LiteFlow
原生 API，不再经过 Letool 自定义接口。

### 3. 使用 `RuleTemplate` 执行规则链

```java
import io.github.leylaragg.letool.rule.core.RuleTemplate;
import com.yomahub.liteflow.flow.LiteflowResponse;

@Service
public class RiskService {

    private final RuleTemplate ruleTemplate;

    public RiskService(RuleTemplate ruleTemplate) {
        this.ruleTemplate = ruleTemplate;
    }

    public LiteflowResponse check(UserRiskRequest request) {
        return ruleTemplate.execute("riskCheck", request);
    }
}
```

`RuleTemplate` 返回 LiteFlow 原生 `LiteflowResponse`。执行成功后可以继续使用响应中的
链标识、上下文和响应数据；执行失败时统一抛出 `RuleException`。

## 进阶使用

`RuleTemplate` 只提供最常用的 `chainId + requestData` 调用方式。需要指定上下文类型、
请求标识、执行选项或使用 LiteFlow 其他重载方法时，直接注入原生 `FlowExecutor`：

```java
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;

@Service
public class AdvancedRiskService {

    private final FlowExecutor flowExecutor;

    public AdvancedRiskService(FlowExecutor flowExecutor) {
        this.flowExecutor = flowExecutor;
    }

    public LiteflowResponse check(UserRiskRequest request) {
        return flowExecutor.execute2Resp("riskCheck", request);
    }
}
```

规则编排 DSL、XML/YAML/JSON 规则文件、外部规则源、热刷新、脚本语言、监控和组件扩展
均直接参考 LiteFlow 2.12.4 文档。Letool 不在这些成熟能力之上再维护一套兼容层。

## 自动配置边界

`RuleAutoConfiguration` 的行为如下：

- LiteFlow `FlowExecutor` 类和 Bean 同时存在时才创建 `RuleTemplate`。
- 用户已经声明 `RuleTemplate` Bean 时自动退让。
- 没有独立的 `letool.rule.enabled` 开关。
- 不创建规则解析器、规则存储、监听器、监控器或 Web Controller。
- 不引入 Servlet/Web 运行时。

如果 LiteFlow 因缺少规则源或配置错误而没有创建 `FlowExecutor`，Letool 也不会创建
不可执行的占位 `RuleTemplate`。

## 错误码

| 错误码 | 枚举项 | 触发条件 |
|---|---|---|
| `RULE_001` | `CHAIN_ID_INVALID` | `chainId` 为 `null`、空字符串或仅包含空白字符 |
| `RULE_002` | `EXECUTION_FAILED` | LiteFlow 抛出运行时异常、返回 `null` 或返回失败响应 |

`RULE_002` 会保留规则链标识和底层异常原因，但不会把 LiteFlow 内部错误消息直接作为
对外错误文案。调用方可以通过 `RuleException#getChainId()` 和 `getCause()` 进行诊断。

## 从旧版自研 API 迁移

本次调整是破坏性变更。旧实现不是要求用户补全的预留接口，而是一套与 LiteFlow
重复且不完整的自研规则设施，因此不保留 Deprecated 空壳或伪实现。用户扩展应迁移到
LiteFlow 官方扩展点。

| 旧 API 或配置 | 迁移方式 |
|---|---|
| `RuleEngine#execute(...)` | 简单调用改用 `RuleTemplate#execute(...)`；高级调用直接使用 `FlowExecutor` |
| `ChainManager`、`ChainParser`、`ChainDefinition` | 使用 LiteFlow 原生规则链 DSL、规则文件和规则源 |
| `@RuleComponent`、`@RuleAction`、`@RuleCondition` | 使用 LiteFlow 官方 `@LiteflowComponent` 及对应组件类型 |
| Letool 自研 `NodeComponent` | 继承 LiteFlow 官方 `com.yomahub.liteflow.core.NodeComponent` |
| `RuleContext`、`RuleResult` | 使用 LiteFlow 请求数据、上下文和 `LiteflowResponse` |
| `GroovyScriptEngine` | 使用 LiteFlow 原生脚本组件及其脚本语言配置 |
| `FileWatcher`、`RuleHotReloadListener` | 使用 LiteFlow 规则源热刷新机制 |
| `RuleStore`、`FileRuleStore` | 使用 LiteFlow 原生文件或外部规则源 |
| `RuleMonitor`、`RuleMetrics` | 使用 LiteFlow 原生监控能力或项目统一可观测方案 |
| `RuleController` | 按业务权限和接口协议自行提供管理端点，底层直接调用 LiteFlow |
| `new RuleException(code, message, ...)` | 改用 `RuleException.invalidChainId()` 或 `RuleException.executionFailed(chainId, cause)` |
| `RuleException#getErrorCode()` 返回字符串、`getChainName()` | 使用 `getCode()` 获取字符串错误码，使用结构化 `getErrorCode()`，并通过 `getChainId()` 获取规则链标识 |
| 自定义类继承 `RuleException` | `RuleException` 现在不可继承；改为组合 `RuleTemplate` 统一转换业务异常，或使用 LiteFlow 原生异常扩展机制 |
| `letool.rule.*` | 改用 `liteflow.*` 官方配置；具体键按所选规则源和能力配置 |

迁移后，组件、规则源和脚本仍然是用户可实现的扩展点，但其契约由 LiteFlow 官方
维护；Letool 不再提供同名接口或默认抛异常的占位实现。
