# EDC 接入 Letool 3.0 指南

EDC 接入采用“宿主兼容、Letool 求值”的边界。Letool 不吸收 EDC 的历史隐式类型、多状态、表格遍历、数据库脚本或质疑动作语义。

## 职责划分

| EDC 负责 | Letool 负责 |
| --- | --- |
| 规则兼容分类和表达式翻译 | 标量 DSL 编译和强类型校验 |
| 字段变化采集和受影响规则选择 | 静态事实与函数依赖描述 |
| 规则版本、发布、缓存和路由 | 执行环境与产物内容摘要 |
| 全部依赖字段的快照加载 | 不可变事实规范化和标量求值 |
| 旧引擎、多状态、表格和动态函数 | 资源限制、诊断和安全轨迹 |
| 质疑等动作及事务顺序 | 无副作用结果返回 |

## 首期迁移子集

首期只迁移能够证明语义等价的纯标量规则。以下规则继续走旧引擎，直到 EDC 有独立兼容设计：

- 依赖字符串数值比较、truthy/falsy、空字符串特殊含义或模糊日期；
- 表格隐式逐行、动态下标和 EDC 通配路径；
- `NOT_READY`、`NOT_CALCULABLE` 等多状态传播；
- 读取可变 Session 或执行数据库脚本的函数；
- 求值过程中产生质疑、回写字段或其他副作用。

`"12" > 2` 不应通过放宽 Letool 类型系统迁移。EDC 可以在事实规范化或表达式翻译阶段做显式、可证明等价的转换；无法证明时继续路由旧引擎。

## 建议组件

| EDC 组件 | 职责 |
| --- | --- |
| `EdcRuleCompatibilityClassifier` | 分类 LETOOL、LEGACY、SHADOW |
| `EdcExpressionTranslator` | 只转换语义可证明等价的表达式 |
| `EdcFactContractProvider` | 按业务版本提供稳定事实契约 |
| `EdcRuleFactsAssembler` | 批量加载依赖并建立不可变事实 |
| `EdcExpressionEngineProvider` | 提供引擎快照和缓存环境身份 |
| `EdcRuleExecutionRouter` | 选择新引擎、旧引擎或影子双跑 |
| `EdcEvaluationResultAdapter` | 映射值、诊断和现有执行日志 |
| `EdcShadowResultComparator` | 双跑比较但不执行新引擎动作 |

这些类型属于 EDC，不进入 Letool core。

## 字段级增量触发

字段增量优化不等待 Letool 迁移：

```text
变化字段
  -> EDC 选择受影响规则
  -> 合并命中规则的全部字段依赖
  -> 一次加载最新事实快照
  -> 规则求值
  -> 按原顺序执行 EDC 动作
```

只有变化字段决定“执行哪些规则”，命中规则的完整依赖决定“加载哪些数据”。`DependencyCoverage.DYNAMIC` 的规则不能按静态字段依赖排除，应由 EDC 保守回退。

## 缓存和发布

EDC 缓存键至少包含：

```text
ruleCode + ruleVersion + factContract.contractDigest()
  + engine.executionModel().environmentDigest()
```

缓存值可以是当前进程的 `CompiledExpression`，但不应把它当成跨 Letool 大版本的持久化协议。升级后从规则源码重新编译。

## 上线路径

1. 扫描真实规则并按业务重要度、函数、表格、多状态和副作用分类。
2. 冻结首期纯标量子集及严格类型契约。
3. 建立 EDC 适配、路由和缓存键。
4. 对存量候选规则离线编译。
5. 使用同一事实快照影子双跑，新引擎不执行动作。
6. 按规则类型或项目灰度，保留旧引擎回滚。
7. 根据兼容率决定是否另行设计表格或多状态能力。

具体实施文件和测试范围以仓库计划 `docs/superpowers/plans/2026-08-24-edc-letool-scalar-rule-integration-plan.md` 为准。
