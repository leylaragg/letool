# Letool 验证指南

本文档规定 Letool 各治理批次和日常模块修改的最低验证要求。命令示例面向
Windows PowerShell，并假设当前目录是项目根目录。

## 1. 验证层级

验证应从最小相关范围逐步扩大，不能只运行全仓构建后忽略具体失败原因。

| 层级 | 使用时机 | 必须报告 |
|---|---|---|
| 定向测试 | 修复一个缺陷或新增一个明确行为 | 测试类、失败原因、修复后的结果 |
| 模块测试 | 完成一个模块内的代码修改 | 模块名、退出码 |
| 关联模块测试 | 修改公共 API、基础模块或传递依赖 | `-am` 构建范围、退出码 |
| 全仓验证 | 一个治理批次交付前 | reactor 数量、测试数、失败/错误/跳过数 |
| 模块 Javadoc | 修改公共 API 或注释后 | 模块名、退出码和警告 |
| 聚合 Javadoc | 正式版本发布前 | 退出码、历史错误和本次新增错误 |
| 差异检查 | 用户审查或提交前 | 行尾错误、工作区范围 |

## 2. 定向测试

只运行一个测试类时使用：

```powershell
mvn -q -pl letool-starter-data-structure -am `
  '-Dtest=DecisionChainTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  test
```

参数说明：

- `-pl` 指定当前修改的模块；
- `-am` 同时构建该模块依赖的 reactor 项目；
- `-Dtest` 指定测试类或测试方法；
- `-Dsurefire.failIfNoSpecifiedTests=false` 避免上游模块没有该指定测试时误报失败。

PowerShell 中包含点号的 `-D` 参数应使用引号，避免被错误解释为 Maven 生命周期阶段。

## 3. 模块与关联模块测试

完成模块内修改后执行：

```powershell
mvn -q -pl letool-starter-data-structure -am test
```

修改 Letool 模块时必须保留 `-am`。如果只执行 `-pl`，Maven 可能从本地仓库加载旧版
Letool 依赖，造成 `NoSuchMethodError` 或让测试验证了错误版本的类。

公共基础模块发生变化时，除上游模块外，还应选择直接依赖它的代表性下游模块执行
`-pl module-a,module-b -am test`。

## 4. 全仓验证

一个批次交付前执行：

```powershell
mvn -q verify
```

全仓验证必须在所有生产代码和测试代码修改完成后重新执行。最终报告至少包含：

- 命令和退出码；
- Maven reactor project 数；
- 测试总数；
- failures、errors 和 skipped 数；
- 已知但不影响结果的警告。

不能使用历史构建或只执行局部测试来声称当前全仓验证通过。

## 5. Javadoc 验证

修改公共类、方法、参数、返回值或异常契约后，先验证当前模块：

```powershell
mvn -q -pl letool-starter-data-structure -DskipTests javadoc:javadoc
```

正式版本发布前验证完整聚合文档：

```powershell
mvn -q -DskipTests javadoc:aggregate
```

公开 API 的 Javadoc 至少说明：

- 类或方法解决的问题；
- 每个参数的语义和是否允许为 `null`；
- 返回值语义；
- 可能抛出的可观察异常；
- 容易误用的顺序、一致性或线程安全约束。

方法内部注释应解释设计原因、失败策略和不明显的边界，不重复翻译代码本身。

截至 2026-07-29，聚合 Javadoc 仍存在历史 DocLint 错误，包括 HTML 标题级别跳跃、
表格缺少 caption 和公开成员缺少注释。各模块治理批次必须保证本模块
`javadoc:javadoc` 通过，并逐步清理历史错误；在所有历史错误清零前，聚合 Javadoc
不能被报告为通过。

截至 2026-07-30，`letool-starter-tool` 单模块 Javadoc 仍有 37 个历史 DocLint 错误，
主要位于本轮 JSON 治理范围之外的 HTTP、注解、模型、Redis 工具和通用工具 API。
JSON 新增 API 未出现在错误列表中；tool 模块整体仍不得报告为 Javadoc 通过。

## 6. 差异与工作区检查

交付用户审查前执行：

```powershell
git diff --check
git status --short
```

`git diff --check` 应无输出。`git status --short` 用于确认：

- 没有修改与当前批次无关的用户文件；
- 没有误加入构建产物、日志或临时文件；
- 用户要求手动提交时，自动化执行方没有创建提交。

如果工作区在开始前已经存在未跟踪或未提交文件，应记录初始状态并保留这些文件，
不能通过 reset、checkout 或删除来制造“干净工作区”。

## 7. 失败处理

验证失败时：

1. 完整阅读第一处失败和堆栈；
2. 使用定向命令稳定复现；
3. 区分本次引入、历史失败、环境错误和本地仓库旧依赖；
4. 对代码缺陷先添加能够复现问题的失败测试；
5. 修复后依次重跑定向测试、模块测试和全仓验证；
6. 最终报告如实列出仍未解决的失败，不得用“应该通过”替代证据。

## 8. 批次交付模板

每个治理批次的最终说明应包含：

```text
批次：
行为变更：
异常与扩展点：
注释与文档：
验证命令及结果：
已知限制：
建议审查顺序：
提交状态：未提交，由用户审查后手动提交
```

## 9. 动态打印专项验证

打印安全回归从 Starter 公开入口覆盖 XML、include、受限 SpEL、扩展数据视图、外部资源标识、PDF 页数与临时目录清理：

```powershell
mvn --% -pl letool-starter-print-spring-boot -am -Dtest=PrintSecurityRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test
```

模板作者指南中的 XML 会由 `PrintDocumentationExampleTest` 原样发布并生成 PDF。修改指南示例、DSL 或发布链路后应执行：

```powershell
mvn --% -pl letool-starter-print-spring-boot -am -Dtest=PrintDocumentationExampleTest -Dsurefire.failIfNoSpecifiedTests=false test
```

容量基线默认被普通测试排除，只能通过专用 Profile 显式运行：

```powershell
mvn --% -P print-capacity -pl letool-starter-print-spring-boot -am -Dtest=PrintCapacityBaselineTest -Dsurefire.failIfNoSpecifiedTests=false test
```

容量报告保存在 `letool-starter-print-spring-boot/target/print-capacity/capacity-baseline.md`，不提交到 Git。报告中的机器耗时用于同环境前后比较，不作为跨机器的固定通过阈值。
