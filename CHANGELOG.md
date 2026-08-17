# Changelog

All notable changes to letool will be documented in this file.

The format follows the spirit of Keep a Changelog, and this project uses
semantic versioning after the 2.0.0 stable line is released.

Each release uses the following fixed categories when applicable:
`Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`, and
`Known Gaps`.

Breaking changes must start with **BREAKING** and identify the affected Maven
coordinate, Java API, or configuration key. The same entry must show the old
usage, its replacement, and the version in which the old behavior or API is
removed. Mock, stub, fallback, and placeholder limitations belong in
`Known Gaps`; they must not be described as production integrations.

## [Unreleased]

### Added

- 新增 `letool-starter-oss-minio`、`letool-starter-oss-aliyun` 和
  `letool-starter-oss-tencent-cos`，分别通过官方 SDK 提供真实对象存储能力，
  并支持业务凭证、客户端配置、官方客户端和 `OssProvider` Bean 退让。
- `letool-starter-oss` 新增不可变 `OssUploadRequest`、`OssUploadResult`、
  可关闭 `OssObject`、稳定 `OSS_*` 错误码及字节数组、本地文件上传便利方法。
- `letool-starter-monitor` 新增基于应用 `MeterRegistry` 的业务指标便利门面、
  稳定 `MONITOR_*` 错误码，以及带防重入、失败隔离、执行报告和优雅关闭的
  用户 `CleanupTask` 调度能力。
- `letool-starter-security` 新增 Spring Security OAuth2 Resource Server JWT
  解码、原生 authority 映射，以及稳定的 `SECURITY_001` 至
  `SECURITY_003` 错误契约。
- Provider-neutral `JsonCodec` SPI, immutable configurable `Fastjson2JsonCodec`,
  and a replaceable Spring JSON codec bean in `letool-starter-tool`.
- Stable `TOOL_JSON_001`/`TOOL_JSON_002` error codes and
  `JsonCodecException` for JSON infrastructure failures.
- Starter auto-configuration governance docs under `docs/`.
- Context runner coverage for user-provided starter infrastructure beans.
- Sample-level starter context tests for common starter combinations.
- Sample-level default coexistence coverage for log and thread MDC infrastructure.
- Auto-configuration context tests for tool starter adapter bean boundaries.
- Auto-configuration context tests for distributed-lock Redis, backend, idempotent, and user override boundaries.
- Auto-configuration context tests for swagger, net, websocket, file, job, and mail starter boundaries.
- Auto-configuration context tests for log feature isolation and cache/rate-limiter/thread sub-feature switches.
- Auto-configuration context tests for sensitive, cipher-suite, and rule starter boundaries.
- Missing optional classpath context tests for cache Redis/AOP, rate-limiter AOP/cache/Redis, and log AOP/Web/Servlet boundaries.
- Auto-configuration context tests for MQ, monitor, and AI starter boundaries；AI 覆盖空模型、
  单/多模型默认选择、禁用、用户退让和定制器排序。
- `letool-starter-ai` 新增按 Bean 名称路由的不可变 `AiModelRegistry`、缓存原生
  `ChatClient` 的 `AiTemplate`、有序 `AiChatClientCustomizer`，以及稳定的
  `AI_CONFIGURATION_INVALID`、`AI_CHAT_MODEL_NOT_FOUND`、
  `AI_EMBEDDING_MODEL_NOT_FOUND`、`AI_CLIENT_CUSTOMIZATION_FAILED` 错误码。
- Real local Netty TCP integration tests for short, persistent, and pooled
  connections, concurrent single-flight safety, absolute request deadlines,
  close/cancel safety, mutable-payload isolation, heartbeat acknowledgement
  expiry, Pipeline ordering, overload, and EventLoop-safe shutdown.
- Local filesystem integration tests for file upload, download, list, delete, service streaming, and ZIP compression/decompression.
- Real XLSX round-trip tests for `ExcelUtil` using EasyExcel native metadata,
  date formatting, converters, caller-owned streams, batching, and row validation.
- Stable `EXCEL_001`/`EXCEL_002`/`EXCEL_003` error codes and
  `ExcelException` for Excel infrastructure failures.
- Stable `MAIL_001`/`MAIL_002`/`MAIL_003`/`MAIL_004` error codes and
  `MailException` for mail configuration, request, delivery, and async lifecycle failures.
- Stable `RULE_001`/`RULE_002` error codes and `RuleException` for invalid
  rule-chain identifiers and LiteFlow execution failures.
- Stable `NET_*` error codes, typed asynchronous/blocking TCP clients, bounded
  connection modes, protocol framing, pre-write connect retry, custom heartbeat,
  and Pipeline extension points in `letool-starter-net`.
- Real LiteFlow XML rule-chain integration coverage for `RuleTemplate`, including
  official `@LiteflowComponent` and `NodeComponent` extension points.
- Stable `CACHE_001` through `CACHE_005` error codes and `CacheException` for cache
  configuration, lookup, loader, invalidation-message, and cache-type-conflict failures.
- Cache collection contract tests for complete L1 snapshots, authoritative Redis empty results,
  Redis recovery cleanup, configured member types, and Redis-compatible negative indexes.
- Local MIME construction, transport seam, multi-account, immutable request snapshot,
  builder, async failure, and auto-configuration tests for `letool-starter-mail`.
- Spring MVC integration tests for `@ApiVersion` routing on identical paths.
- Servlet filter-chain integration tests for XSS escaping and SQL-injection rejection.
- Servlet filter-chain integration tests for repeatable request body reads across pre-MVC filters and controllers.
- Servlet filter-chain integration tests for SQL-injection rejection in text request bodies.
- Local WebSocket room broadcast tests for `WsTemplate` and `WsRoomManager`.
- Local `DefaultWsHandler` lifecycle tests for principal binding, ping/pong, message dispatch, and close cleanup.
- Random-port WebSocket endpoint smoke test for configured path handshakes and welcome notification delivery.
- Random-port WebSocket endpoint smoke tests for registered handler message dispatch and token-less handshake rejection.
- Local `JobScheduler` execution lifecycle tests for manual trigger running-state tracking, same-name concurrent execution tracking, retry accounting, invalid Cron rollback, shutdown, and logging.
- Local `JobScheduler` coverage for `start/step` Cron periods and `JobDefinition` shard index validation.
- Local `JobScheduler` coverage for exact-hour, list, and range Cron expressions.

### Changed

- **BREAKING — `io.github.leylaragg:letool-starter-rule` Maven coordinate:**
  LiteFlow 薄封装模块重命名为
  `io.github.leylaragg:letool-starter-rule-liteflow`，以区别于新的通用规则决策框架。
  使用方需要替换依赖坐标；`io.github.leylaragg.letool.rule.*` Java API、自动配置和
  `RULE_001`/`RULE_002` 错误契约保持不变。旧坐标自当前未发布版本起不再由 Reactor 构建。

- **BREAKING — `io.github.leylaragg:letool-starter-oss` 生产化（自
  `2.0.0-beta.2` 起）：** `OssProvider` 与 `OssTemplate` 改为使用不可变上传结果、
  可关闭下载对象和 `URI` 预签名结果；`delete` 改为无返回值的幂等操作。配置从
  `letool.oss.default-provider` 与厂商独立 Bucket 迁移为
  `letool.oss.provider` 和统一 `letool.oss.bucket`。应用需按存储服务改为引入
  `letool-starter-oss-minio`、`letool-starter-oss-aliyun` 或
  `letool-starter-oss-tencent-cos`。
- **BREAKING — `fix(swagger)!` / `io.github.leylaragg:letool-starter-swagger`
  API 文档便利能力纠偏（自 `2.0.0-beta.2` 起）：** Springdoc 2.8.17 继续负责 OpenAPI
  引擎、扫描、分组和原生扩展，新增 Knife4j 4.5.0 纯 UI 并恢复 `/doc.html`。恢复真实生效的
  `letool.swagger.enabled` 统一开关，关闭后标准或自定义的 OpenAPI、Knife4j 和 Swagger UI
  入口返回 404，普通业务请求不受影响。Bearer 默认恢复为开启，默认方案名恢复为 `Bearer`，
  并新增 `letool.swagger.security.scheme-name` 自定义能力；空白名称在 Bearer 开启时快速失败。
  用户 `OpenAPI` Bean 仍按类型接管，`ApiGroup`、伪 `groups`、离线文档、页脚、自定义 Header
  和自动 `defaultGroupApi` 不恢复。27 项测试覆盖配置绑定、条件装配、用户退让、真实
  `/doc.html` 与 OpenAPI 文档、关闭语义、自定义上下文/Servlet 路径、自定义 Springdoc
  与真实分组入口，以及同前缀业务接口放行。
- **BREAKING — `io.github.leylaragg:letool-starter-ai` Spring AI 迁移
  （自 `2.0.0-beta.2` 起）：** 模块基于 Spring AI 1.1.8 重建为 Provider 中立薄封装，
  应用必须显式选择 Spring AI Provider Starter，并把 API Key、端点、模型参数、重试等
  迁移到 `spring.ai.<provider>.*`。旧 `AiTemplate.chat()` / `embedding()` 改为
  `chatClient(...)`、`chatModel(...)`、`embeddingModel(...)`，多模型默认项使用
  `letool.ai.default-chat-model` 与 `letool.ai.default-embedding-model` Bean 名称配置。
- **BREAKING — `io.github.leylaragg:letool-starter-monitor` 指标与清理 API
  （自 `2.0.0-beta.2` 起）：**
  `MetricsCollector` 改为 Micrometer `MeterRegistry` 的无私有存储门面，耗时使用
  `Duration`；旧占位清理类改为必须由应用实现的 `CleanupTask` SPI。Prometheus
  等导出后端改用 Spring Boot 标准 `management.*` 配置和对应 registry 依赖。
  `long increment(String)` 改为 `void increment(String, String...)`，
  `getCounterValue` 改为 `counterValue`，`recordTime(String, long)` 改为
  `recordTime(String, Duration, String...)`，`getTimerStats` 改为
  `timerSnapshot`；名称集合与全量 Map 导出改为直接查询 `MeterRegistry`。
- **BREAKING — `io.github.leylaragg:letool-starter-security` 认证链：**
  Bearer JWT 改由 Spring Security Resource Server 处理；公开路径应通过
  `letool.security.exclude-paths` 或业务 `SecurityFilterChain` 配置，
  AccessToken 与 RefreshToken 不再混用，JWT 密钥必须显式配置且至少
  32 个 UTF-8 字节。
- `letool-starter-security` 不再向使用方传递完整 Web/MVC/Tomcat Starter；
  生产依赖收敛为 Spring Web 与 provided Servlet API。
- `letool-starter-security` 公开路径匹配迁移到 Spring Security
  `PathPatternRequestMatcher`，不再依赖已标记待删除的 `AntPathRequestMatcher`；
  `exclude-paths` 按 Spring `PathPattern` 语法解析。
- `JsonUtil` keeps its existing static methods and compact-output defaults, while
  adding explicit per-call `JsonCodec` overloads instead of mutable global state.
- **BREAKING — `io.github.leylaragg:letool-starter-tool` JSON failure contract:**
  code that previously caught Fastjson2 `JSONException` from `JsonUtil` must catch
  `JsonCodecException` instead. Direct provider exception leakage is removed in
  the next 2.0 prerelease; method names and successful JSON output remain compatible.
- The Fastjson2 Redis serializer now validates its target type, serializes directly
  to UTF-8 bytes, and wraps provider failures in Spring `SerializationException`
  without including raw Redis values in exception messages.
- `DecisionChainBuilder` now rejects `null` conditions and rules added after
  `otherwise`, so invalid or unreachable decision rules fail during
  construction.
- Tool, web, cache, rate limiter, job, mail, SMS, security, thread, and log starters now back off more consistently when users provide their own beans.
- Tool auto-configuration now uses explicit adapter beans instead of broad component scanning; `RedisUtil` is created only when the named object `redisTemplate` bean exists.
- Mail runtime infrastructure beans are explicit opt-in. Net registers only a
  lazy `NetRuntime` and `TcpClientFactory` by default and can be disabled with
  `letool.net.tcp.enabled=false`; it never connects to a remote service automatically.
- Mail requests can select a configured SMTP account per send, use finite connection/read/write
  timeouts, and are validated and snapshotted before synchronous or asynchronous delivery.
  Async delivery now uses a configurable bounded queue and rejects overload with `MAIL_004`.
- The default mail sender now builds Jakarta Mail MIME messages directly, while the retained
  `MailSender` extension lets custom gateway implementations replace SMTP without requiring accounts.
- Distributed-lock Redis defaults now back off for user lock infrastructure, respect the configured backend, and honor `letool.lock.idempotent.enabled=false`.
- Distributed-lock documentation now marks auto-renewal and fair-lock settings as reserved until implemented.
- Log trace, web log, and audit auto-configuration are now independently switchable under the `letool.log` module switch; audit stores are also gated by `letool.log.audit.enabled`.
- Log AOP auto-configuration now isolates method/web aspects behind nested AspectJ and Servlet classpath guards, preventing optional logging aspects from breaking unrelated starters.
- Cache and rate-limiter annotation aspects can now be disabled, or skipped when AspectJ is absent, while keeping their programmatic APIs available.
- Thread MDC propagation now honors `letool.thread.context-propagation.mdc=false`.
- Thread starter now exclusively owns the default `mdcTaskDecorator`; log and
  thread starters can be imported together without bean-definition collisions.
- WebSocket auto-configuration is servlet web-only and backs off for user `webSocketConfigurer` beans.
- File upload/download services now back off when users provide their own implementations.
- Sensitive Jackson/log integration beans now back off when users provide their own infrastructure.
- Starter dependency scopes were tightened for tool, sensitive, cipher-suite, rule, cache, rate-limiter, log, security, web, swagger, websocket, file, excel, job, and distributed-lock modules.
- AI 自动配置现在只创建可退让的 `AiModelRegistry` 与 `AiTemplate`；没有模型时允许启动，
  多模型未配置默认 Bean 名称时拒绝歧义，Provider 与 Spring AI 原生 Bean 由应用负责。
- Monitor now creates its Micrometer facade only when a `MeterRegistry` exists,
  keeps alert and cleanup switches independent, and fails fast when cleanup is
  enabled without user tasks.
- MQ 重建为 Spring Cloud Stream 便利门面；RabbitMQ、Kafka、RocketMQ 由独立
  Binder 模块按需提供，发送委托给 `StreamOperations`，消费直接使用 Spring
  Cloud Function。
- 支付核心保持厂商中立，支付宝和微信支付由独立官方 SDK Provider 模块提供；
  内置 Mock 仅在开发或测试中显式启用，真实 Provider 强制执行回调验签。
- Unused Lombok declarations were removed from all starter POMs; unused AI Web,
  MQ log, and monitor log/thread dependencies were removed. Monitor later added
  Actuator back intentionally as its mature metrics engine.
- Data starter now detects H2 JDBC URLs, skips null generated IDs during insert, and resolves Lambda getter references through `SerializedLambda`.
- `letool-starter-net` now delegates NIO, framing, and fixed connection pooling
  to fine-grained Netty components managed by the Spring Boot BOM; it no longer
  uses `netty-all`, blocking `Socket`, or a custom generic connection pool.
- Net TCP request timeout is now one absolute deadline covering encoding,
  connect/acquire, retry backoff, write, and response. Client close, cancellation,
  and timeout cancel pending work and prevent later business writes.
- Net TCP heartbeat now has configurable acknowledgement windows and a maximum
  missed-window threshold. Only one heartbeat is in flight; the connection
  remains isolated while waiting for the same acknowledgement, so a delayed
  acknowledgement cannot complete a business request. A failed heartbeat
  discards the connection; a business request that has not started writing may
  safely reacquire within its original deadline, while written requests are
  never replayed.
- Net TCP Pipeline customization now separates wire-level handlers installed
  before framing from payload-level handlers installed after framing. Business
  and heartbeat writes traverse both documented outbound layers.
- `LocalFileStorage` now normalizes paths and rejects local path traversal outside the configured storage root.
- `ZipUtil.decompress` now rejects ZIP entries that escape the extraction target directory.
- **BREAKING — `io.github.leylaragg:letool-starter-excel` mapping contract:**
  `@ExcelColumn`, `ExcelConverter`, `DateConverter`, and `EnumConverter` are
  removed in the next 2.0 prerelease. Use EasyExcel `@ExcelProperty`,
  `@DateTimeFormat`, `@NumberFormat`, `@ColumnWidth`, and `Converter<T>`;
  read models must follow EasyExcel JavaBean mapping conventions.
- `ExcelUtil` is now a thin EasyExcel wrapper with explicit argument checks,
  caller-owned stream handling, immutable batch snapshots, native metadata,
  unified exceptions, and actual worksheet row numbers during validation.
- `MailTemplate` now implements `AutoCloseable` so its async executor can be shut down by Spring
  or standalone callers; delivery failures are exposed without leaking recipient, subject,
  credential, host, or provider error text.
- **BREAKING — `io.github.leylaragg:letool-starter-cache` exception contract:**
  arbitrary-text `CacheException` constructors are removed in the next 2.0 prerelease.
  Catch `CacheException` and branch on stable `CACHE_001` through `CACHE_005` codes instead.
- Native List, Hash, Set, and ZSet caches now create L1 entries only from complete Redis reads
  or during explicit L2 degradation, treat healthy Redis empty results as authoritative,
  and clear degraded local snapshots after Redis recovery.
- Collection default factories now honor `CacheConfig.valueType(...)`; Set no longer assumes
  that an unspecified member type is `Long`.
- ZSet range reads now obtain members and scores in one Redis operation instead of issuing
  one `ZSCORE` request per member.
- Cache names are now globally unique across KV/List/Hash/Set/ZSet registrations, preventing
  name-only invalidation messages from being routed to the wrong local cache type.
- Cross-JVM invalidation now matches serialized key representations against actual L1 keys,
  so Long and custom collection keys are evicted instead of being incorrectly treated as String keys.
- KV、List、Hash、Set、ZSet 统一使用带缓存名称的隔离键空间；缓存名称中的分隔符会编码，
  一致性版本元数据与业务数据分离。五种缓存区域清理统一使用 `SCAN + UNLINK`，
  Redis Cluster 会扫描可用主节点，不再执行阻塞式 `KEYS`。
- 根 POM 补充 SCM、问题跟踪元数据和显式 `release` profile；该 profile 只生成
  sources、Javadoc 与 GPG 签名制品，不内置 Central Portal 凭据或远端发布动作。
- Web starter no longer depends on log starter transitively.
- Web SQL-injection filtering now runs before XSS escaping, avoiding false positives from generated HTML entity separators.
- Web SQL-injection filtering now checks common text request bodies while preserving repeatable reads through the filter chain.
- `ApiVersionRequestMapping` now treats invalid version values as no-match instead of throwing during MVC request matching.
- `WsTemplate.sendToRoom` now delegates to `WsRoomManager` so room messages are scoped to room members.
- `JobResult.success(String, int)` records retry count for executions that eventually succeed.
- `JobDefinition` now rejects out-of-range shard indexes during build.
- `JobScheduler` now separates scheduled Cron futures from actively executing jobs, tracks concurrent same-name executions, rolls back failed Cron registrations, delegates Cron semantics to Spring `CronExpression`, and exposes an explicit shutdown lifecycle.
- **BREAKING — `io.github.leylaragg:letool-starter-rule` Java API and configuration:**
  the self-maintained rule engine is replaced by a LiteFlow 2.12.4 thin wrapper.
  Replace `RuleEngine#execute(...)` with `RuleTemplate#execute(...)` for the
  common path or inject LiteFlow `FlowExecutor` for advanced execution. Replace
  Letool rule components, contexts, results, parsers, stores, hot reload, scripts,
  monitoring, and management APIs with LiteFlow native extension points. Replace
  all `letool.rule.*` settings with the applicable `liteflow.*` settings.
- **BREAKING — `io.github.leylaragg:letool-starter-rule` exception contract:**
  the public arbitrary-text `RuleException` constructors are removed. Replace
  `new RuleException(code, message, ...)` with `invalidChainId()` or
  `executionFailed(chainId, cause)`. `getErrorCode()` now returns the structured
  `RuleErrorCode`; use `getCode()` for the string code, and replace
  `getChainName()` with `getChainId()`. `RuleException` is now final and cannot
  be subclassed; replace custom exception subclasses with composition around
  `RuleTemplate`, or use LiteFlow's native exception extension strategy.

### Removed

- **BREAKING — `io.github.leylaragg:letool-starter-oss` Stub Provider：** 删除
  `MinioProvider`、`AliyunOssProvider`、`TencentCosProvider` 三个模拟实现及
  `letool.oss.stub-enabled`。启用 OSS 后必须存在官方 SDK Provider 模块或业务自定义
  `OssProvider`，不再返回模拟 URL、模拟下载流或伪成功结果。
- **BREAKING — `io.github.leylaragg:letool-starter-ai` 重复实现：** 删除自研
  `AiProvider`、请求响应模型、Provider/HTTP/SSE 协议栈、Embedding Service、
  Function Calling、ChatSession、PromptTemplate、RAG/VectorStore 与旧
  `letool.ai` Provider 配置。对应能力迁移到 Spring AI 1.1.8 原生
  `ChatModel`、`EmbeddingModel`、`ChatClient`、`@Tool`、Advisor 和 VectorStore。
- **BREAKING — `io.github.leylaragg:letool-starter-mq` 自研消息 API：** 删除
  `InMemoryMqProvider`、`@MqListener`、订阅 API 和自维护 Broker 配置；发送迁移到
  `MqTemplate` + Provider Binder 模块，消费迁移到 Spring Cloud Function。
- **BREAKING — `io.github.leylaragg:letool-starter-monitor`
  （自 `2.0.0-beta.2` 起）：** 删除自研
  `JvmMetrics`、`JvmMetricsSnapshot`、`HttpMetrics`、`ApiStatsCollector`、
  `ApiStatsAggregator`、`ApiStatsSummary` 与 `ApiErrorCollector`。JVM/HTTP
  指标迁移至 Actuator/Micrometer，业务 API 指标迁移至 `Counter`/`Timer`。
- **BREAKING — `io.github.leylaragg:letool-starter-net` legacy Java API:**
  removed `TcpShortClient`, `TcpLongClient`, mutable `TcpConfig`, the old
  `ProtocolCodec` hierarchy, `GenericConnectionPool`, `NetHttpTemplate`,
  `HttpLoadBalancer`, `HttpCircuitBreaker`, and gateway route classes. Replace
  TCP usage with `TcpClientFactory`, immutable `TcpClientOptions`,
  `FrameCodec`, and `PayloadCodec`. HTTP and gateway behavior will be rebuilt
  separately on mature frameworks; no deprecated shell or fake execution path
  is retained.
- **BREAKING — `io.github.leylaragg:letool-starter-log` MDC task decorator:**
  `io.github.leylaragg.letool.log.trace.MdcTaskDecorator` and the log starter's
  automatic `mdcTaskDecorator` Bean are removed in the next 2.0 prerelease.
  Add `letool-starter-thread` for automatic async MDC propagation, instantiate
  `io.github.leylaragg.letool.thread.propagation.MdcTaskDecorator` explicitly,
  or provide a Spring `TaskDecorator`. The removed class was a duplicate concrete
  implementation, not a user-reserved extension interface.
- **BREAKING — `io.github.leylaragg:letool-starter-data` 模块：**
  删除自研的 `LetoolTemplate`、实体注解、Lambda 查询 DSL、分页模型、反射
  RowMapper 和数据库方言。项目应直接选择 MyBatis-Plus、Spring Data
  JDBC/JPA，或 Spring Framework 原生 `JdbcClient` / `JdbcTemplate`；
  不提供 Deprecated 空壳或替代包装模块。
- `letool-starter-rule` 不再声明未使用的可选 `letool-starter-data` 依赖。
- **BREAKING — `io.github.leylaragg:letool-starter-rule` self-maintained facilities:**
  removed `RuleEngine`, `ChainManager`, `ChainParser`, `ChainDefinition`, Letool
  `NodeComponent`, `RuleContext`, `RuleResult`, `GroovyScriptEngine`, `FileWatcher`,
  `RuleHotReloadListener`, `RuleStore`, `FileRuleStore`, `RuleMonitor`,
  `RuleMetrics`, `RuleController`, and Letool rule annotations. These were
  duplicate implementations rather than user-reserved placeholder interfaces;
  user extensions must use LiteFlow native component, rule-source, script,
  refresh, and monitoring contracts.
- Removed all `letool.rule.*` configuration metadata and the unused tool, Boot
  starter aggregate, configuration processor, SLF4J, and Spring Web dependencies
  from `letool-starter-rule`; the redundant `commons-logging` implementation is
  also excluded in favor of Spring's `spring-jcl`. LiteFlow and the shared
  exception module remain compile dependencies; Spring Boot auto-configuration
  APIs remain provided.
- **BREAKING — `io.github.leylaragg:letool-starter-excel` auto configuration:**
  the no-op `ExcelAutoConfiguration` and undocumented `letool.excel.enabled`
  setting are removed in the next 2.0 prerelease. `ExcelUtil` remains available
  directly and no Spring Bean replacement is required.
- **BREAKING — `io.github.leylaragg:letool-starter-mail` template API and async setting:**
  `MailRequest.templateName`, `MailRequest.variables`, builder `template(...)`/
  `variable(...)`, and `letool.mail.async` are removed in the next 2.0 prerelease
  because they never performed template rendering or selected the send method.
  Render content before calling `html(...)`, and choose `send()` or `sendAsync()` explicitly.
- Unused Thymeleaf, Freemarker, tool-starter, Spring Boot starter, and SLF4J dependencies
  were removed from `letool-starter-mail`; Jakarta Mail and the shared exception module
  remain direct runtime API dependencies.
- The Redis starter aggregate in `letool-starter-cache` was replaced by the direct optional
  Spring Data Redis API; unused Spring Boot starter, Fastjson2, and Testcontainers declarations
  were removed, while the tool and shared exception modules remain direct dependencies.

### Fixed

- 统一管理 `commons-io` 2.20.0，修复 `commons-compress` 1.28.0 与 EasyExcel/POI
  传递版本不一致导致的 `NoSuchMethodError`。
- Corrected `JsonUtil` documentation that advertised a fixed date format which
  the implementation did not actually configure.
- Corrected the data-structure quick-start example to build a
  `DecisionChain` before executing it, and documented the fail-fast behavior
  when no rule matches and no `otherwise` rule exists.
- Refreshed module inventory and verification guidance for the exception
  starter and the current Maven reactor.
- Corrected cache invalidation payload parsing for keys containing commas, pipes, and backslashes,
  and reject invalid message flags and escape sequences.
- Corrected List and ZSet local negative-range handling to match Redis `LRANGE`/`ZRANGE`.

### Security

- Redis polymorphic JSON continues to use a dedicated Fastjson2
  `AutoTypeBeforeHandler` allow list; the general-purpose `JsonCodec` does not
  enable or inherit Redis type metadata settings and rejects the deprecated
  `SupportAutoType` reader feature.
- Redis auto-type entries are normalized to Java package boundaries and denied
  types now fail closed, preventing an allowed package such as `com.example`
  from also accepting look-alike packages such as `com.exampleevil`.
- Cache warning logs no longer expose business keys, raw invalidation payloads, or provider
  exception text; detailed causes remain available only at DEBUG level.

### Known Gaps

- Aggregate Javadoc still fails on legacy DocLint issues such as skipped HTML
  heading levels, tables without captions, and undocumented public members.
  Each module governance batch must make its own Javadoc pass before the
  aggregate release gate can be enabled.
- `letool-starter-tool` module Javadoc still has 37 legacy DocLint errors in
  pre-existing HTTP, annotation, model, Redis utility, and general utility APIs.
  The new JSON APIs add no errors, but the module-wide cleanup remains a separate batch.
- SMS 与 Pay 核心仍提供显式 Mock，但不会默认启用，也不能替代真实外部服务。
- Redis、真实 SMTP、OSS/SMS/Pay 官方 Provider、MQ Broker 和高级 Webhook 投递控制
  仍需针对应用实际采用的环境执行 profile-gated 契约测试。
- Cache 集合写入和 TTL 刷新仍是两个 Redis 命令；要求事务级原子性的高写入路径应使用
  业务 Lua/事务或成熟分布式数据结构。区域级 `evictAll()` 已改为 `SCAN + UNLINK`，
  但超大键空间仍不应高频执行。

## [2.0.0-beta.1] - 2026-07-02

### Added

- Multi-module Spring Boot 3 starter layout.
- Core modules for tool, sensitive, log, cache, cipher, web, security, data, thread, swagger, file, excel, mail, distributed lock, rule, net, pay, MQ, rate limiter, OSS, SMS, AI, data structure, websocket, job, monitor, and sample.

[Unreleased]: https://github.com/leylaragg/letool/compare/v2.0.0-beta.1...HEAD
[2.0.0-beta.1]: https://github.com/leylaragg/letool/releases/tag/v2.0.0-beta.1
