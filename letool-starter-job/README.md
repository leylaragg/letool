# letool-starter-job

`letool-starter-job` 是 Spring Boot Quartz 的便捷封装。Quartz 负责线程池、调度、持久化、集群协调、Misfire 和优雅停机；Letool 提供注解注册、分片映射、手动触发门面、失败重试、执行上下文、日志扩展和统一异常，让业务项目不必重复编写 Quartz 配置代码。

## 引入依赖

```xml
<dependency>
    <groupId>io.github.leylaragg</groupId>
    <artifactId>letool-starter-job</artifactId>
    <version>${letool.version}</version>
</dependency>
```

Starter 会自动引入 `spring-boot-starter-quartz`。线程池、JobStore、数据源、集群和停机策略继续使用 Spring Boot 原生 `spring.quartz.*` 配置。

## 注解任务

```java
import io.github.leylaragg.letool.job.annotation.JobHandler;
import io.github.leylaragg.letool.job.annotation.LetoolJob;
import io.github.leylaragg.letool.job.core.JobContext;
import io.github.leylaragg.letool.job.core.MisfirePolicy;
import org.springframework.stereotype.Component;

@Component
@LetoolJob(
        name = "dailyReportJob",
        cron = "0 0 6 * * ?",
        zone = "Asia/Shanghai",
        description = "每日报表生成",
        shardTotal = 4,
        maxRetries = 3,
        backoffMs = 2_000,
        backoffMultiplier = 2.0,
        maxBackoffMs = 60_000,
        concurrent = false,
        misfirePolicy = MisfirePolicy.DO_NOTHING,
        requestRecovery = true)
public class DailyReportJob {

    @JobHandler
    public void execute(JobContext context) {
        int shardIndex = context.getShardIndex();
        int shardTotal = context.getShardTotal();
        // 根据分片索引读取并处理本分片数据。
    }
}
```

一个任务类必须且只能声明一个公开 `void @JobHandler` 方法。该方法可以无参数，也可以只接收一个 `JobContext`。代理 Bean 会按目标类注解正确解析。

## 编程式注册

需要动态参数时，可以将处理器注册为所有节点都存在的 Spring Bean，再通过 `JobScheduler` 注册定义：

```java
@Component("cleanupJobHandler")
public class CleanupJobHandler implements JobHandler {

    @Override
    public void execute(JobContext context) {
        String tenant = context.getParam("tenant");
        // 执行清理业务。
    }
}

@Configuration
public class JobRegistrationConfiguration {

    @Bean
    ApplicationRunner registerCleanupJob(JobScheduler scheduler) {
        return arguments -> scheduler.register(
                JobDefinition.builder()
                        .jobName("cleanupJob")
                        .cron("0 0 3 * * ?")
                        .zone("Asia/Shanghai")
                        .param("tenant", "default")
                        .build(),
                "cleanupJobHandler");
    }
}
```

同名、同处理器、同定义的重复注册是幂等操作；同名但定义不同会抛出 `JOB_002`。需要主动覆盖时调用 `replace(definition, handlerBeanName)`。替换会先成功写入新定义，再清理减少的旧分片，避免写入失败时先删除线上任务。

`registerLocal(definition, lambda)` 只适用于默认 RAMJobStore 的单 JVM 便利场景。JDBC JobStore 或集群模式会抛出 `JOB_007`，因为 Lambda 无法跨节点持久化和恢复。

## 管理任务

```java
List<JobTriggerReceipt> receipts = scheduler.trigger("cleanupJob");
JobTriggerReceipt shardReceipt = scheduler.trigger("cleanupJob", 0);

scheduler.pause("cleanupJob");
scheduler.resume("cleanupJob");

Optional<JobDefinition> definition = scheduler.getJob("cleanupJob");
List<JobDefinition> allJobs = scheduler.getAllJobs();
boolean paused = scheduler.isPaused("cleanupJob");
boolean runningOnCurrentNode = scheduler.isRunning("cleanupJob");

scheduler.unregister("cleanupJob");
```

`JobTriggerReceipt` 只表示 Quartz 已接受触发请求，不表示业务执行成功。需要更底层的 Trigger、Calendar 或固定间隔能力时，可以直接注入原生 `org.quartz.Scheduler`，Letool 不限制原生 Quartz API。

## 分片、并发与重试

- `shardTotal=N` 会创建 N 个稳定的 JobDetail 和 CronTrigger；内存模式在当前 JVM 执行，JDBC 集群由 Quartz 保证每次触发只被一个节点获取。
- 默认 `concurrent=false`，同一分片使用 `@DisallowConcurrentExecution` 串行执行，不同分片仍可并行。
- `maxRetries` 表示首次失败后的额外重试次数。重试使用一次性 Quartz Trigger，并按 `backoffMs × multiplier^retryCount` 计算，结果不超过 `maxBackoffMs`。
- 首次执行及其重试共享 `executionId`。业务处理应使用该标识或业务唯一键实现幂等，框架不能替业务事务自动去重。
- `requestRecovery=true` 只请求 Quartz 的节点故障恢复能力，不等价于业务事务“恰好一次”。

## 配置

Letool 自有配置只有以下四项：

| 属性 | 默认值 | 说明 |
|---|---:|---|
| `letool.job.enabled` | `true` | Letool Job 总开关；不影响原生 Quartz |
| `letool.job.group` | `letool` | Letool 管理的 Quartz Job 组名 |
| `letool.job.error-summary-max-length` | `1024` | 执行记录错误摘要最大字符数 |
| `letool.job.logging.enabled` | `true` | 是否输出默认 `letool.job` 结构化摘要 |

单 JVM 默认使用 RAMJobStore：

```yaml
spring:
  quartz:
    wait-for-jobs-to-complete-on-shutdown: true
    properties:
      org.quartz.threadPool.threadCount: 8

letool:
  job:
    group: letool
    error-summary-max-length: 1024
```

## JDBC 持久化与集群

生产集群应先通过数据库迁移工具创建 Quartz 官方表，再启用 JDBC JobStore：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/app
    username: app
    password: change-me
  quartz:
    job-store-type: jdbc
    jdbc:
      initialize-schema: never
    scheduler-name: letool-job
    wait-for-jobs-to-complete-on-shutdown: true
    properties:
      org.quartz.scheduler.instanceId: AUTO
      org.quartz.jobStore.isClustered: true
      org.quartz.jobStore.clusterCheckinInterval: 15000
      org.quartz.jobStore.useProperties: true
      org.quartz.jobStore.driverDelegateClass: org.quartz.impl.jdbcjobstore.StdJDBCDelegate
      org.quartz.threadPool.threadCount: 16
```

Letool 写入 JobDataMap 的值全部是字符串，因此兼容 `useProperties=true`，不依赖 Java 对象序列化。不要在生产环境长期使用 `spring.quartz.jdbc.initialize-schema=always`；请将 Quartz 对应数据库脚本纳入 Flyway、Liquibase 或其他变更流程。所有集群节点必须使用相同 `letool.job.group`，并部署相同名称的任务处理器 Bean。

Spring Boot 配置说明见 [Quartz 自动配置文档](https://docs.spring.io/spring-boot/reference/io/quartz.html)，数据库表脚本见 [Quartz 官方发行仓库](https://github.com/quartz-scheduler/quartz/tree/main/quartz/src/main/resources/org/quartz/impl/jdbcjobstore)。

## 执行日志扩展

默认实现只向 `letool.job` Logger 输出不含业务参数的摘要。需要数据库或其他存储时，实现 `JobLogService` 即可；扩展异常会被隔离，不改变业务任务结果。

```java
@Component
public class DatabaseJobLogService implements JobLogService {

    @Override
    public void record(JobExecutionRecord record) {
        // 使用 JdbcTemplate、MyBatis-Plus 或消息队列保存 record。
    }
}
```

MySQL 8 参考表位于 `META-INF/letool/job/schema/mysql-job-log.sql`。该脚本不会自动执行，用户可以直接采用、调整字段，或完全替换为自己的持久化方案。执行记录提供执行 ID、任务名、分片、节点、触发来源、重试次数、状态、计划/开始/结束时间、耗时和截断错误摘要。

## 破坏性变更

- 删除旧自研线程池调度器、`JobResult`、固定间隔伪实现及未接入调度链的 `ShardStrategy`/`ConsistentHashShard`。
- `JobDefinition` 改为建造器创建的不可变对象，不再保存 Handler、具体分片索引或任意对象参数。
- `trigger` 返回接受回执，而不是同步执行结果。
- Quartz Cron 使用 6/7 段语法；非法 Cron、时区、重试和分片配置会在注册前失败。

## 错误码

| 错误码 | 含义 |
|---|---|
| `JOB_001` | 任务定义不合法 |
| `JOB_002` | 任务名称或持久化定义冲突 |
| `JOB_003` | 任务不存在 |
| `JOB_004` | 任务处理方法不合法 |
| `JOB_005` | 当前节点缺少任务处理器 |
| `JOB_006` | Quartz 调度操作失败 |
| `JOB_007` | 当前 JobStore 不允许本地 Lambda |
| `JOB_008` | 任务业务执行失败，原始异常作为 cause 保留 |
| `JOB_009` | 重试 Trigger 安排失败，Quartz 异常作为 cause 保留 |
