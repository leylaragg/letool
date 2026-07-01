# letool-starter-rule

> 规则引擎模块，基于 LiteFlow 封装，提供规则编排、热加载、Groovy 脚本和可视化规则链能力。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-rule</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 添加依赖并配置

```yaml
letool:
  rule:
    enabled: true
    source: file
    file:
      path: classpath:rule/chains/*.yml
      watch: true
    groovy:
      script-path: classpath:rule/scripts/
      cache-scripts: true
    hot-reload:
      enabled: true
      check-interval: 10s
```

### 2. 定义规则节点

```java
@RuleComponent("ageValidator")
public class AgeValidator extends NodeComponent {

    @Override
    public void process(RuleContext context) {
        Integer age = context.getParam("age");
        if (age < 18) {
            throw new RuleException("AGE_001", "年龄不满足要求");
        }
    }
}
```

### 3. 编写规则链 YAML（classpath:rule/chains/risk-check.yml）

```yaml
chain:
  name: riskCheck
  nodes:
    - id: ageValidator
      type: action
    - id: riskScoreCalculator
      type: action
    - id: riskDecision
      type: if
      condition: riskCondition
```

### 4. 执行规则链

```java
@Autowired
private RuleEngine ruleEngine;

RuleContext ctx = new RuleContext();
ctx.setParam("age", 25);
ctx.setParam("score", 85);
RuleResult result = ruleEngine.execute("riskCheck", ctx);
```

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `letool.rule.enabled` | boolean | true | 是否启用规则引擎 |
| `letool.rule.source` | String | file | 规则源类型：file / database |
| `letool.rule.file.path` | String | classpath:rule/chains/*.yml | 规则链文件路径（支持 Ant 通配符） |
| `letool.rule.file.watch` | boolean | true | 是否监听文件变化 |
| `letool.rule.groovy.script-path` | String | classpath:rule/scripts/ | Groovy 脚本路径 |
| `letool.rule.groovy.cache-scripts` | boolean | true | 是否缓存编译后的脚本 |
| `letool.rule.groovy.compile-timeout` | long | 5 | 脚本编译超时（秒） |
| `letool.rule.hot-reload.enabled` | boolean | true | 是否启用热重载 |
| `letool.rule.hot-reload.check-interval` | long | 10 | 文件变更检查间隔（秒） |
| `letool.rule.monitoring.enabled` | boolean | true | 是否启用执行监控 |

## 核心 API

### 注解声明式——定义规则节点

```java
// 动作节点
@RuleComponent("riskScoreCalculator")
public class RiskScoreCalculator extends NodeComponent {

    @Override
    public void process(RuleContext context) {
        Integer score = context.getParam("score");
        if (score > 80) {
            context.setResult("riskLevel", "HIGH");
        }
    }
}

// 条件节点——重写 condition() 方法
@RuleComponent("riskCondition")
public class RiskCondition extends NodeComponent {

    @Override
    public boolean condition(RuleContext context) {
        Integer score = context.getParam("score");
        return score != null && score > 60;
    }

    @Override
    public void process(RuleContext context) {
        // 条件满足时执行
        context.setResult("decision", "APPROVE");
    }
}
```

### 编程式——管理规则链

```java
@Autowired
private ChainManager chainManager;

// 加载规则链文件
int count = chainManager.loadFromDirectory("classpath:rule/chains/");

// 查询规则链
ChainDefinition chain = chainManager.get("riskCheck");

// 运行时热更新
ChainDefinition updatedChain = ChainDefinition.builder()
        .name("riskCheck")
        .nodes(newNodes)
        .build();
chainManager.reload("riskCheck", updatedChain);

// 列出所有链
List<ChainDefinition> all = chainManager.listAll();
```

### 节点生命周期

```java
@RuleComponent("customNode")
public class CustomNode extends NodeComponent {

    @Override
    public void init() {
        // 引擎启动时调用：初始化连接、加载配置
    }

    @Override
    public void process(RuleContext context) {
        // 核心业务逻辑
    }

    @Override
    public void destroy() {
        // 引擎关闭时调用：释放资源
    }

    @Override
    public String getName() {
        return "customNode"; // 自定义节点名称
    }
}
```
