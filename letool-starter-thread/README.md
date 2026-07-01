# letool-starter-thread

## 模块简介

线程管理模块，提供线程池注册/监控、MDC 和安全上下文自动传递、虚拟线程（Java 21+）支持。通过配置即可定义多个命名线程池，支持运行时动态调整，配合 Spring `@Async` 和注解实现上下文透传。

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
      io-executor:
        core-pool-size: 20
        max-pool-size: 200
        thread-name-prefix: "io-"
        virtual-threads: true          # Java 21+ 自动使用虚拟线程
```

**Step 2** — 在业务代码中异步执行：

```java
// 注解式：带上下文传播的异步执行
@AsyncWithContext("task-executor")
public CompletableFuture<Order> processOrder(Long orderId) {
    log.info("异步处理订单: {}", orderId);  // TraceId 自动从父线程传递
    return CompletableFuture.completedFuture(orderService.get(orderId));
}

// 编程式：工具类快捷异步提交
@Autowired
private ThreadPoolManager threadPoolManager;

ExecutorService pool = threadPoolManager.getOrCreate("task-executor", config);
ThreadUtil.runAsync(() -> log.info("异步任务"), pool);
```

## 配置属性

```yaml
letool:
  thread:
    enabled: true
    pools:
      <pool-name>:
        core-pool-size: 5             # 核心线程数，默认 5
        max-pool-size: 20             # 最大线程数，默认 20
        queue-capacity: 500           # 队列容量（LinkedBlockingQueue），默认 500
        thread-name-prefix: "letool-" # 线程名前缀，默认 "letool-"
        keep-alive-seconds: 60        # 保活时间（秒），默认 60
        virtual-threads: false        # 是否使用虚拟线程（Java 21+），默认 false
    monitoring:
      enabled: true                   # 监控开关，默认 true
      metrics-export: true            # Micrometer 指标导出，默认 true
    context-propagation:
      mdc: true                       # 传递 MDC 日志上下文，默认 true
      security: true                  # 传递安全上下文，默认 true
```

## 核心 API 示例

### 1. 注解式：@AsyncWithContext

将 Spring `@Async` 的能力与 MDC 上下文传播结合，异步方法自动继承父线程的 TraceId 和用户信息：

```java
@Service
public class OrderService {

    @AsyncWithContext("task-executor")
    public CompletableFuture<String> processAsync(Long orderId) {
        // 此处 log 输出的 TraceId 与调用方一致
        log.info("开始处理订单 {}", orderId);
        String result = doHeavyWork(orderId);
        return CompletableFuture.completedFuture(result);
    }

    @AsyncWithContext  // 未指定线程池名，默认使用 "task-executor"
    public void sendNotification(Long orderId) {
        // 无需返回值的异步方法
        mailService.send(orderId);
    }
}
```

底层通过 Spring `AnnotationAsyncExecutionInterceptor` 和 `MdcTaskDecorator` 配合工作：任务提交时捕获 MDC 快照，工作线程执行前还原上下文，执行后自动清理避免污染。

### 2. 编程式：ThreadPoolManager 线程池管理

```java
@Autowired
private ThreadPoolManager threadPoolManager;

// 获取或创建线程池
ThreadPoolProperties.PoolConfig config = new ThreadPoolProperties.PoolConfig();
config.setCorePoolSize(10);
config.setMaxPoolSize(50);
ExecutorService pool = threadPoolManager.getOrCreate("orderPool", config);

// 运行时动态调整
threadPoolManager.resize("orderPool", 20, 100);

// 按名称获取线程池
ThreadPoolExecutor executor = threadPoolManager.get("orderPool");

// 关闭单个或全部
threadPoolManager.shutdown("orderPool");
threadPoolManager.shutdownAll();
```

虚拟线程支持：配置 `virtual-threads: true` 后，Java 21+ 通过反射调用 `Executors.newVirtualThreadPerTaskExecutor()` 创建虚拟线程；低版本 JDK 自动降级为平台线程池。

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

在自定义 `TaskExecutor` Bean 上设置 `MdcTaskDecorator`，确保 `@Async` 方法中能获取父线程的 TraceId：

```java
@Bean("taskExecutor")
public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new MdcTaskDecorator());  // 关键配置
    executor.initialize();
    return executor;
}
```

`MdcTaskDecorator` 在 `decorate()` 时捕获 `MDC.getCopyOfContextMap()`，任务完成后 `MDC.clear()` 避免上下文在工作线程间泄漏。
