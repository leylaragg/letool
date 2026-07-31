# letool-starter-net

`letool-starter-net` 是基于 Netty 的 TCP 客户端便捷封装。Netty 负责异步网络 I/O、
报文累积和连接池，Letool 负责不可变配置、类型化请求接口、生命周期、统一异常以及
适合业务项目的默认约束。

当前阶段只提供生产级 TCP 能力。HTTP、网关、负载均衡和熔断将在后续阶段基于成熟
框架建设，不保留旧版本中未接入真实执行链的伪实现。

## 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-net</artifactId>
    <version>${letool.version}</version>
</dependency>
```

Spring Boot 默认注册惰性的 `NetRuntime` 和 `TcpClientFactory`。在创建第一个客户端
前不会启动 Netty 线程，也不会自动连接任何远程地址。

## 异步请求

```java
TcpClientOptions options = TcpClientOptions.builder()
        .host("127.0.0.1")
        .port(9000)
        .connectionMode(ConnectionMode.PERSISTENT)
        .frameCodec(LengthFieldFrameCodec.int32())
        .connectTimeout(Duration.ofSeconds(3))
        .requestTimeout(Duration.ofSeconds(10))
        .build();

TcpClient<byte[], byte[]> client = tcpClientFactory.create(options);
client.request(requestBytes)
        .thenAccept(responseBytes -> handleResponse(responseBytes));
```

`TcpClient` 必须在应用停止或不再使用时关闭。通过 Spring 管理的 `NetRuntime` 会随
容器优雅关闭；手动创建的客户端仍由业务代码负责关闭。

## 同步便捷接口

```java
try (BlockingTcpClient<String, String> client =
             tcpClientFactory.createBlocking(options, PayloadCodecs.utf8())) {
    String response = client.request("hello");
}
```

同步门面内部仍使用异步客户端，并且禁止在 Netty EventLoop 线程中阻塞等待。误用时
会抛出 `NET_TCP_BLOCKING_ON_EVENT_LOOP`。同步门面不会创建第二套等待超时，它只
等待异步核心的同一个请求结果，因此返回超时后不会遗留仍可能发送的后台请求。

## 连接模式

| 模式 | 行为 | 适用场景 |
|---|---|---|
| `SHORT` | 每次请求新建连接，收到响应或失败后关闭；并发连接数受 `maxConnections` 限制 | 对端要求一问一断、低频调用 |
| `PERSISTENT` | 复用一条连接，每次只允许一个请求独占 | 无请求 ID、响应按请求顺序返回的私有协议 |
| `POOLED` | 使用 Netty 有界固定连接池，每条连接仍单请求独占 | 无请求 ID，但需要多连接并发 |

`PERSISTENT` 模式的 `maxConnections` 固定为 1；`SHORT` 和 `POOLED` 可显式配置并发
连接上限。复用连接模式的等待请求数量由 `maxPendingRequests` 限制，短连接超过并发
连接上限时不会隐式排队。任何模式超过容量都会立即返回
`NET_TCP_REQUEST_OVERLOADED`，不会无限堆积。

## 请求期限

`requestTimeout` 是从客户端受理请求开始计算的单一绝对期限，覆盖载荷编码、首次
建连、连接池等待、重试退避、报文写出和响应等待。客户端关闭、请求取消或期限到达
都会取消尚未完成的建连、连接获取和退避任务；实际写出前还会再次检查关闭状态与
绝对期限，防止超时结果返回后后台补发报文。

自定义 `PayloadCodec` 应保持非阻塞。框架无法中断调用线程中正在执行的同步编码，
但编码返回后若绝对期限已到达，报文不会进入网络通道。

## 报文边界与序列化

报文边界和业务对象序列化是两个独立扩展点：

```java
TcpClient<OrderRequest, OrderResponse> client = tcpClientFactory.create(
        options,
        new PayloadCodec<>() {
            @Override
            public byte[] encode(OrderRequest request) {
                return orderProtocol.encodeRequest(request);
            }

            @Override
            public OrderResponse decode(byte[] response) {
                return orderProtocol.decodeResponse(response);
            }
        });
```

内置分帧器：

| 分帧器 | 说明 |
|---|---|
| `LengthFieldFrameCodec` | 报文头部使用 1/2/3/4/8 字节长度字段 |
| `DelimiterFrameCodec` | 使用非空字节序列分隔报文 |
| `FixedLengthFrameCodec` | 每个业务报文保持固定长度 |

JSON 只能作为 `PayloadCodec` 的实现，不能单独解决 TCP 粘包和拆包。特殊报文头可实现
`FrameCodec`。Netty 流水线提供两个有明确顺序的扩展锚点：

```java
TcpClientOptions options = TcpClientOptions.builder()
        // 省略远程地址、分帧器等配置
        .wirePipelineCustomizer(pipeline -> {
            // 位于分帧器之前，适合安装 TLS 等处理线上字节流的处理器
        })
        .pipelineCustomizer(pipeline -> {
            // 位于分帧器之后，适合安装完整载荷压缩、加密或协议适配处理器
        })
        .build();
```

入站顺序为“线级扩展器 → 分帧器 → 载荷级扩展器 → 心跳 → 业务响应”，出站顺序
相反。业务报文和心跳都从通道尾部写出，因此会完整经过用户提供的出站处理器。

自定义 `PayloadCodec`、`HeartbeatStrategy` 和 Pipeline 处理器会被多个事件线程
调用，必须线程安全并且不能执行阻塞操作。

## 应用层心跳

心跳语义属于具体业务协议，框架不会硬编码 `PING`：

```java
HeartbeatStrategy heartbeat = new HeartbeatStrategy() {
    @Override
    public Duration idleInterval() {
        return Duration.ofSeconds(30);
    }

    @Override
    public Duration responseTimeout() {
        return Duration.ofSeconds(10);
    }

    @Override
    public int maxMissedResponses() {
        return 3;
    }

    @Override
    public byte[] heartbeatPayload() {
        return new byte[]{0x00, 0x01};
    }

    @Override
    public boolean isHeartbeatResponse(byte[] response) {
        return Arrays.equals(response, new byte[]{0x00, 0x02});
    }
};

TcpClientOptions options = TcpClientOptions.builder()
        // 省略远程地址等配置
        .heartbeatStrategy(heartbeat)
        .build();
```

心跳响应会在业务载荷解码前被消费，不会错误完成正在等待的业务请求。客户端不会在
业务响应等待期间插入心跳；如果业务请求到达时上一轮心跳仍在等待应答，业务写出会
暂缓。无关联 ID 的协议在任意时刻只允许一个心跳在途；每经过一个
`responseTimeout` 应答窗口便累计一次漏答，但连接会继续隔离并等待同一个 ACK，
不会并行发送探测报文。累计窗口达到 `maxMissedResponses`，或心跳生成、识别、
写入失败时，框架会淘汰当前连接。此时尚未写出的业务请求可以在其绝对期限和建连
重试次数内安全换连接，已经开始写出的业务请求绝不会重放。心跳载荷必须至少包含
一个字节。

## 建连重试安全边界

默认最多尝试三次连接，失败间隔使用有上限的指数退避和 20% 随机抖动：

```java
ConnectRetryPolicy retryPolicy = new ConnectRetryPolicy(
        3,
        Duration.ofMillis(100),
        Duration.ofSeconds(2),
        0.2);
```

重试只发生在业务报文写出前。报文一旦开始写出，断连、超时等失败不会自动重放，
避免非幂等交易被重复执行。建连失败以及业务写出前发现的失效心跳连接使用同一有界
策略，并且始终受 `requestTimeout` 绝对期限约束。业务级重试必须由调用方根据幂等
语义显式决定。

## Spring 配置

```yaml
letool:
  net:
    tcp:
      enabled: true
      event-loop-threads: 4
      shutdown-quiet-period: 100ms
      shutdown-timeout: 5s
```

设置 `letool.net.tcp.enabled=false` 后不会创建 `NetRuntime` 和
`TcpClientFactory`。业务项目也可以注册自己的 `NetRuntime` Bean 接管线程资源；
Letool 不会关闭用户传入的 `EventLoopGroup`。

## 重要限制

- 当前请求响应模型没有伪造通用请求关联协议，每条连接同一时间只处理一个业务请求。
- 需要单连接多路复用时，应在后续关联协议扩展中明确提供请求 ID 提取和匹配规则。
- 请求期限覆盖编码、建连、连接获取、退避、写入和响应；超时、解码失败和未知入站
  报文都会关闭当前连接，防止迟到响应污染下一次请求。
- 默认异常和日志不包含完整业务报文，也不会拼接底层异常消息。
- 本阶段不提供 HTTP、网关、熔断器或服务发现 API。
