# 动态打印生产运维指南

本指南面向把 Letool 动态打印接入生产环境的应用开发与运维人员。框架负责同步模板发布、版本锁定、编译缓存、PDF 输出和安全观测；模板来源、业务数据、鉴权、持久化和产物交付仍由宿主应用负责。

## 生产配置

下面给出全部 Starter 配置及默认值。页数、字节数、缓存容量和临时目录应根据容量基线、实例内存与并发量重新评估。

```yaml
letool:
  print:
    enabled: true
    renderer-profile-version: 1
    locale: zh-CN
    zone-id: Asia/Shanghai
    max-pages: 2500
    max-output-bytes: 104857600
    max-temporary-bytes: 314572800
    include-document-metadata: true
    template-set-cache-capacity: 64
    template-cache-capacity: 1024
    temporary-directory: ""
    startup:
      require-active-template: false
      validate-fonts: false
    metrics:
      enabled: true
    health:
      enabled: true
    spel:
      enabled: false
```

`renderer-profile-version` 是渲染环境的显式版本。字体、样式、渲染器参数或扩展能力发生不兼容变化时提升它，旧编译结果便不会被新环境继续复用。`locale` 和 `zone-id` 是业务门面构造请求时的默认值；模板格式化器仍可在受控选项中覆盖。

`max-output-bytes` 最低为 1 MiB，默认 100 MiB，用于限制单份最终产物。`max-temporary-bytes` 默认 300 MiB，用于限制单次 PDF 请求所有活动临时文件，不能小于最终产物上限且最多为 8 GiB。两项都在实际写入前检查，生产接口仍应限制并发、请求数据规模和响应传输时间。

## 模板发布与版本切换

通过 `TemplateSetPublisher` 发布模板，不要直接向仓库写入未校验集合。同一版本不可覆盖，推荐流程是：

1. 组装完整文档和片段；
2. 调用 `publish(version, definitions)` 完成发布校验；
3. 在受控变更窗口调用 `activate(version)`；
4. 用不带版本的 `PrintService.renderTo` 处理新请求；
5. 重打历史文档时显式传入历史版本。

`publishAndActivate` 适合发布与启用必须一次完成的场景。单次打印开始时会锁定一个不可变模板集合，切换当前版本不会让进行中的文档混入新模板。默认内存仓库只适合单实例、启动期固定模板和测试；多实例生产环境应实现 `TemplateRepository`，并自行提供事务、CAS 或其他一致性保证。

框架没有内置数据库表、模板管理 API、审批流或配置中心监听器。宿主可以热切换已发布版本，但外部配置的监听、权限和回滚编排不属于打印 Starter。

## 字体与许可证

中文、自由文本框批注和特定品牌字形应配置 `PdfFont` Bean。供应器每次调用都要打开新的输入流，字体文件应来自宿主掌控的 classpath、制品目录或受控存储，不允许模板提供路径。

上线前确认字体许可证允许服务器端嵌入和生成文档。字体变更后提升 `renderer-profile-version`，并用代表性中文、数字、符号和批注样例回归。开启 `startup.validate-fonts` 后，Starter 会在启动阶段读取每个字体流的少量头部；空流或读取失败会阻止启动，错误不会回显字体路径。

## 临时目录

`temporary-directory` 为空时使用 PDF 模块的默认受控目录。显式配置时请为每个应用准备独立、可写、配额受控的目录，不要和上传区、静态资源或其他应用共享。

渲染任务使用独立工作目录，并在成功和失败路径清理。PDF 通过最终结构检查后才写入调用方流，因此渲染或验证失败不会留下半份响应；如果调用方输出流在传输中失败，框架会停止写入并保留原因，但不能回滚已经接收的字节。开启 `startup.validate-fonts` 且配置了临时目录时，启动检查还会创建并删除探针文件。运维侧仍应监控磁盘容量和异常残留，不要用宽泛的系统临时目录作为长期兜底。

## 编译缓存

XML 使用模板集合与单模板两层本地有界缓存。容量分别由 `template-set-cache-capacity` 和 `template-cache-capacity` 控制，默认是 64 和 1024。缓存键包含模板内容摘要、DSL 能力和渲染器配置版本；发布新集合或提升配置版本后会自然使用新的条目。

缓存只加速编译，不改变仓库版本语义，也不是分布式缓存。不要把业务数据、请求上下文或产物放进编译缓存。命中率长期偏低时，先检查模板版本是否被频繁无意义地递增，再评估容量，而不是直接配置无界缓存。

## 指标

类路径中存在 Micrometer `MeterRegistry` 且 `letool.print.metrics.enabled=true` 时，Starter 自动注册以下指标：

| 指标 | 类型 | 标签 |
|---|---|---|
| `letool.print.render.duration` | Timer | `output`、`result`、`failure` |
| `letool.print.render.failures` | Counter | `output`、`failure` |
| `letool.print.output.bytes` | DistributionSummary | `output` |
| `letool.print.output.pages` | DistributionSummary | `output` |
| `letool.print.cache.entries` | Gauge | `cache` |
| `letool.print.cache.hits` | Gauge | `cache` |
| `letool.print.cache.misses` | Gauge | `cache` |
| `letool.print.cache.loads.success` | Gauge | `cache` |
| `letool.print.cache.loads.failure` | Gauge | `cache` |

`cache` 只有 `template-set` 和 `template` 两个固定值。渲染指标也只使用输出格式、成功状态和框架失败分类，不包含模板代码、版本、业务主键、表达式或异常正文，因此不会制造高基数标签或泄露业务数据。

建议至少告警：失败率持续上升、P95 渲染耗时偏离本机基线、产物页数或字节数突增、缓存装载失败增加，以及缓存命中率长期下降。阈值应结合实例规格、模板复杂度和业务峰值制定。

## 健康检查与严格启动

类路径中存在 Actuator 且 `letool.print.health.enabled=true` 时，会注册两项健康检查：

- `PrintTemplateHealthIndicator` 检查仓库可访问性和当前集合，只输出活动状态、版本和摘要；
- `PrintInfrastructureHealthIndicator` 检查字体流与临时目录，只输出安全组件状态。

健康检查开启不等于启动失败。需要启动时强制具备活动模板集合，可设置 `startup.require-active-template=true`；需要启动时验证字体及已配置临时目录，可设置 `startup.validate-fonts=true`。严格检查适合模板和字体在应用启动前已经就绪的部署方式；由外部系统稍后发布模板时，不要开启活动模板强制检查。

健康端点的访问控制、详情展示级别和探针分组仍由 Spring Boot Actuator 配置。即使框架详情不含业务数据，也不应把完整健康端点直接暴露到公网。

## 容量验收

容量基线不会混入普通单元测试，需要显式运行：

```powershell
mvn --% -P print-capacity -pl letool-starter-print-spring-boot -am -Dtest=PrintCapacityBaselineTest -Dsurefire.failIfNoSpecifiedTests=false test
```

报告写入 `letool-starter-print-spring-boot/target/print-capacity/capacity-baseline.md`。固定场景和当前参考结果见[容量基线](dynamic-print-capacity-baseline.md)。升级 JDK、PDF 依赖、字体、渲染参数或实例规格后应重新运行，并比较成功率、页数、字节数、中位数和 P95；机器相关耗时不作为单元测试硬阈值。

## 故障排查

| 现象 | 优先检查 |
|---|---|
| 启动报告没有活动模板 | 仓库初始化顺序、`require-active-template` 与模板发布时机 |
| 启动报告字体失败 | 字体 Bean 是否返回新流、字体文件是否为空或无读取权限 |
| 临时目录不可用 | 目录权限、磁盘配额、探针文件是否能创建和删除 |
| 模板发布失败 | XML 结构、上下文版本、include 图、重复 ID 和扩展注册 |
| 渲染失败且消息较少 | 先按打印错误码分类，再从受控日志查看保留的原因链 |
| 页数或字节数超限 | 模板分页、表格规模、业务集合大小和生产限制配置 |
| 缓存命中率低 | 模板版本变动频率、配置版本和两层缓存容量 |
| 中文或批注外观异常 | 字体覆盖范围、字体顺序、嵌入许可和配置版本 |

不要为了排查把模板正文、业务 JSON、绝对路径、URL、SQL 或第三方异常原文写进用户错误、指标标签或健康详情。需要更细日志时也应先脱敏，并限制日志保留和访问范围。

## 能力边界

当前生产主线是受控 XML 到 PDF 的同步输出。以下能力由宿主或独立扩展承担：

- 模板数据库结构、管理接口、审批与配置中心热切换；
- 业务查询、权限、字典和复杂计算；
- 异步任务、消息投递、对象存储上传和下载响应；
- 任意 URL、文件、classpath 或网络资源读取；
- 图片逻辑 ID 到可信资源的解析与 PDF 显示；
- JasperReports 等独立报表模型的适配管线。

JasperReports 适配应作为单独 Maven 模块实现 `PrintPipeline`，保留自身模板模型和导出器，不把它包装进 Letool XML 主链路，也不让第三方依赖进入只使用内置 PDF 的项目。
