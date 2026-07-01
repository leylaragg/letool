# letool-starter-job

分布式任务调度模块，支持 Cron 表达式定时调度、任务分片、失败重试（指数退避）和任务执行日志记录。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-job</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始

**1. 在 `application.yml` 中配置（全部可选，均有默认值）：**

```yaml
letool:
  job:
    enabled: true
    thread-pool-size: 8
```

**2. 编写任务类并标注注解：**

```java
@LetoolJob(
    name = "dailyReportJob",
    cron = "0 0 6 * * ?",
    description = "每日报表生成",
    shardTotal = 4,
    maxRetries = 3
)
@Component
public class DailyReportJob {

    @JobHandler
    public void execute(JobContext context) {
        int shardIndex = context.getShardIndex();
        int shardTotal = context.getShardTotal();
        System.out.println("执行分片 " + (shardIndex + 1) + "/" + shardTotal);
    }
}
```

启动应用后，任务自动注册并开始按 Cron 调度。

## 配置属性

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `letool.job.enabled` | `true` | 调度模块总开关 |
| `letool.job.thread-pool-size` | `4` | 执行线程池大小 |
| `letool.job.shard.enabled` | `true` | 分片功能开关 |
| `letool.job.shard.default-total` | `1` | 默认总分片数 |
| `letool.job.retry.max-retries` | `3` | 最大重试次数 |
| `letool.job.retry.backoff-ms` | `1000` | 退避基础时间（毫秒） |
| `letool.job.retry.backoff-multiplier` | `2.0` | 退避倍率（指数） |
| `letool.job.log.enabled` | `true` | 任务日志开关 |
| `letool.job.log.retention-days` | `30` | 日志保留天数 |

## 核心 API 示例

### 1. 注解式（`@LetoolJob` + `@JobHandler`）

```java
@LetoolJob(
    name = "dataSyncJob",
    cron = "0 0/5 * * * ?",        // 每5分钟执行
    description = "数据同步任务",
    shardTotal = 4,
    maxRetries = 5,
    backoffMs = 2000
)
@Component
public class DataSyncJob {

    @JobHandler
    public void execute(JobContext context) {
        String executionId = context.getExecutionId();
        int shardIndex = context.getShardIndex();
        int shardTotal = context.getShardTotal();

        // 根据分片索引处理不同数据段
        int totalRecords = 10000;
        int batchSize = totalRecords / shardTotal;
        int start = shardIndex * batchSize;
        int end = start + batchSize;
        syncData(start, end);
    }
}
```

### 2. 编程式（JobScheduler API）

```java
@Autowired
private JobScheduler jobScheduler;

// 定义并注册任务
JobDefinition job = new JobDefinition();
job.setJobName("manualJob");
job.setCron("0 0/10 * * * ?");
job.setHandler(context -> doSomething());

jobScheduler.register(job);

// 手动触发任务（无论是否有 Cron）
JobResult result = jobScheduler.trigger("manualJob");

// 暂停 / 恢复调度
jobScheduler.pause("manualJob");
jobScheduler.resume("manualJob");

// 查询任务状态
boolean running = jobScheduler.isRunning("manualJob");
boolean paused = jobScheduler.isPaused("manualJob");
JobDefinition def = jobScheduler.getJob("manualJob");
List<JobDefinition> allJobs = jobScheduler.getAllJobs();
List<String> runningJobs = jobScheduler.getRunningJobs();

// 注销任务
jobScheduler.unregister("manualJob");
```

### 3. JobContext 使用

```java
@JobHandler
public void execute(JobContext context) {
    // 获取执行信息
    String jobName = context.getJobName();
    String executionId = context.getExecutionId();
    int shardIndex = context.getShardIndex();
    int shardTotal = context.getShardTotal();
    LocalDateTime startTime = context.getStartTime();

    // 获取自定义参数
    Object userId = context.getParam("userId");
    Optional<String> region = context.getParam("region", String.class);
}
```
