# letool-starter-thread

## 模块简介

线程管理模块，提供线程池原子注册、运行指标、MDC 上下文传播和虚拟线程（Java 21+）支持。默认异步执行器复用 Spring `AsyncTaskExecutor`、`TaskExecutorAdapter` 与 `TaskDecorator`，模块只保留轻量的线程池管理能力。

与 `letool-starter-log` 同时使用时，日志模块负责在请求线程建立 TraceId，
线程模块负责把 MDC 自动传播到异步任务。MDC 装饰器只由线程模块注册，
业务无需编写额外配置，也不会出现多个 Starter 注册同名 Bean 的问题。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-thread</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

**Step 1** — 在 `application.yml` 中定义线程池：

```yaml
letool:
  thread:
    enabled: true
    pools:
      task-executor:
        core-pool-size: 10
        max-pool-size: 50
        queue-capacity: 500
        thread-name-prefix: "task-"
        allow-core-thread-timeout: true
      io-executor:
        core-pool-size: 20
        max-pool-size: 200
        thread-name-prefix: "io-"
        virtual-threads: true          # Java 21+ 使用虚拟线程，低版本按本配置降级
    monitoring:
      enabled: true
    context-propagation:
      mdc: true
```

**Step 2** — 在业务代码中异步执行：

```java
// 注解式：带上下文传播的异步执行
@AsyncWithContext
public CompletableFuture<Order> processOrder(Long orderId) {
    log.info("异步处理订单: {}", orderId);  // TraceId 自动从父线程传递
    return CompletableFuture.completedFuture(orderService.get(orderId));
}

// 编程式：工具类快捷异步提交
@Autowired
private ThreadPoolManager threadPoolManager;

ExecutorService pool = threadPoolManager.getOrCreate("orderPool", config);
ThreadUtil.runAsync(() -> log.info("异步任务"), pool);
```

`@AsyncWithContext` 默认使用 Spring Bean `letoolTaskExecutor`，也可以显式指定 `letoolIoExecutor`。配置 Map 的 key（如 `task-executor`）用于读取线程池参数，不等同于执行器 Bean 名称。Spring Boot 自带的 `applicationTaskExecutor` / `taskExecutor` 继续服务于 MVC 和普通 `@Async`，不会被本模块覆盖。

## 配置属性

```yaml
letool:
  thread:
    enabled: true
    pools:
      task-executor:                  # 内置 letoolTaskExecutor Bean 的配置键
        core-pool-size: 5             # 核心线程数，默认 5
        max-pool-size: 20             # 最大线程数，默认 20
        queue-capacity: 500           # 队列容量（LinkedBlockingQueue），默认 500
        thread-name-prefix: "letool-" # 线程名前缀，默认 "letool-"
        keep-alive-seconds: 60        # 保活时间（秒），默认 60
        allow-core-thread-timeout: true # 是否允许核心线程超时回收，默认 true
        virtual-threads: false        # 是否使用虚拟线程（Java 21+），默认 false
      io-executor:                    # 内置 letoolIoExecutor Bean 的配置键
        core-pool-size: 20
        max-pool-size: 200
    monitoring:
      enabled: true                   # 监控开关，默认 true
    context-propagation:
      mdc: true                       # 传递 MDC 日志上下文，默认 true
```

> `security` 和 `metrics-export` 曾经只是没有实现路径的预留配置，现已删除。安全上下文或其他业务上下文请通过自定义 Spring `TaskDecorator` 扩展；模块会按 Spring 顺序规则组合用户装饰器与默认 `MdcTaskDecorator`。

`pools` 当前只消费 `task-executor` 和 `io-executor` 两个内置配置键。其他 key 不会自动创建 Spring Bean；自定义线程池请通过 `ThreadPoolManager` 显式创建，避免配置存在但运行时没有对应执行器的误解。

## 核心 API 示例

### 1. 注解式：@AsyncWithContext

`@AsyncWithContext` 是 Spring `@Async` 的组合注解。默认执行器会通过 `MdcTaskDecorator` 继承提交线程的 MDC：

```java
@Service
public class OrderService {

    @AsyncWithContext
    public CompletableFuture<String> processAsync(Long orderId) {
        // 此处 log 输出的 TraceId 与调用方一致
        log.info("开始处理订单 {}", orderId);
        String result = doHeavyWork(orderId);
        return CompletableFuture.completedFuture(result);
    }

    @AsyncWithContext("letoolIoExecutor")
    public void sendNotification(Long orderId) {
        // 无需返回值的异步方法
        mailService.send(orderId);
    }
}
```

模块会启用 Spring 异步代理。调用必须经过 Spring Bean 代理，同一个对象内部的自调用不会触发异步。默认可用的执行器 Bean 为 `letoolTaskExecutor` 和 `letoolIoExecutor`；通过 `ThreadPoolManager` 创建的自定义线程池不会自动注册为 Spring Bean。业务若要替换内置执行器，应使用这两个稳定 Bean 名称声明自己的执行器。

默认执行器 Bean 的公开类型是 `AsyncTaskExecutor`，不要按 `ExecutorService` 注入：

```java
@Resource(name = "letoolTaskExecutor")
private AsyncTaskExecutor letoolTaskExecutor;
```

### 2. 编程式：ThreadPoolManager 线程池管理

```java
@Autowired
private ThreadPoolManager threadPoolManager;

// 获取或创建线程池
ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
config.setCorePoolSize(10);
config.setMaxPoolSize(50);
ExecutorService pool = threadPoolManager.getOrCreate("orderPool", config);

// 获取任意类型执行器（包括纯虚拟线程执行器）
ExecutorService registered = threadPoolManager.getExecutor("orderPool");

// 运行时动态调整
threadPoolManager.resize("orderPool", 20, 100);

// 按名称获取线程池
ThreadPoolExecutor executor = threadPoolManager.get("orderPool");

// 关闭单个或全部
threadPoolManager.shutdown("orderPool");
threadPoolManager.shutdownAll();
```

`getOrCreate` 对同名线程池采用原子获取或创建语义，并发调用只返回一个实例。`create` 用于强制新建，同名实例已经存在时抛出 `THREAD_002`，不会覆盖或泄漏旧线程池。`getPools()` 返回平台线程池的不可修改快照。

虚拟线程支持：配置 `virtual-threads: true` 后，Java 21+ 使用 `Executors.newVirtualThreadPerTaskExecutor()`；纯虚拟线程执行器不支持 `resize` 和平台线程池指标。低版本 JDK 使用同一份容量、命名与保活配置降级为平台线程池。

### 3. 编程式：ThreadUtil 快捷工具

```java
// 异步任务提交
CompletableFuture<Void> f1 = ThreadUtil.runAsync(() -> {
    doWork();
}, executor);

CompletableFuture<String> f2 = ThreadUtil.supplyAsync(() -> {
    return computeResult();
}, executor);

// 线程休眠（被中断自动恢复状态，不抛异常）
ThreadUtil.sleep(2000);
ThreadUtil.sleep(30, TimeUnit.SECONDS);

// 检测虚拟线程支持
boolean vt = ThreadUtil.isVirtualThreadsSupported();  // Java 21+ 返回 true
```

### 4. MDC 上下文传递配置

模块默认注册 `MdcTaskDecorator`。业务可以额外注册自己的 `TaskDecorator`，默认执行器会通过 Spring `CompositeTaskDecorator` 组合所有装饰器：

```java
@Bean
public TaskDecorator tenantTaskDecorator() {
    return runnable -> {
        String tenantId = TenantContext.getTenantId();
        return () -> {
            String previous = TenantContext.getTenantId();
            try {
                TenantContext.setTenantId(tenantId);
                runnable.run();
            } finally {
                TenantContext.restore(previous);
            }
        };
    };
}
```

如果业务自行提供名为 `mdcTaskDecorator` 的 Bean，模块会退让。`MdcTaskDecorator` 会在提交时捕获快照、执行前清除工作线程残留值，并在任务完成后恢复工作线程原上下文。

## 异常契约

| 错误码 | 含义 |
|---|---|
| `THREAD_001` | 线程池名称、容量、保活时间或线程名前缀配置不合法 |
| `THREAD_002` | 显式创建时同名线程池已经存在 |
| `THREAD_003` | 调整的线程池不存在或不是可调整的平台线程池 |

上述错误统一以 `ThreadException` 暴露，不在消息中携带业务线程池名称或任务内容。
