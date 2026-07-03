# letool-starter-net

> 网络通信框架，提供 HTTP 客户端 + TCP Socket（短连接/长连接）、自定义协议编解码、网关路由、连接池能力。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-net</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始（3 分钟上手）

### 1. 添加依赖并配置

```yaml
letool:
  net:
    http:
      enabled: true
      connect-timeout: 5s
      read-timeout: 30s
    tcp:
      enabled: true
      default-connect-timeout: 10s
      no-delay: true
    gateway:
      enabled: true
      routes:
        - route-id: user-service
          type: http
          servers:
            - host: localhost
              port: 8080
              weight: 1
```

### 2. HTTP 调用

```java
@Autowired
private NetHttpTemplate httpTemplate;

// GET 请求
String result = httpTemplate.get("http://api.example.com/users/1");

// POST JSON
String created = httpTemplate.post("http://api.example.com/users", userDto);
```

### 3. 网关路由—统一 TCP/HTTP 入口

```java
@Autowired
private NetGateway gateway;

// HTTP 路由
NetHttpTemplate http = gateway.http("user-service");
String resp = http.get("http://user-addr/api/health");

// TCP 路由（带协议编解码）
TcpClient tcpClient = gateway.tcp("icbc-pay");
byte[] response = tcpClient.sendAndReceive(requestBytes);
```

## 配置属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `letool.net.http.enabled` | boolean | false | 是否启用 HTTP 客户端 |
| `letool.net.http.connect-timeout` | String | 5s | 连接超时 |
| `letool.net.http.read-timeout` | String | 30s | 读取超时 |
| `letool.net.http.max-total` | int | 200 | 连接池最大连接数 |
| `letool.net.http.max-per-route` | int | 50 | 单路由最大连接数 |
| `letool.net.tcp.enabled` | boolean | false | 是否启用 TCP 客户端 |
| `letool.net.tcp.default-connect-timeout` | String | 10s | TCP 连接超时 |
| `letool.net.tcp.no-delay` | boolean | true | 禁用 Nagle 算法 |
| `letool.net.tcp.keep-alive` | boolean | false | 启用 TCP KeepAlive |
| `letool.net.gateway.enabled` | boolean | false | 是否启用网关 |
| `letool.net.gateway.routes[].route-id` | String | - | 路由唯一标识 |
| `letool.net.gateway.routes[].type` | String | http | 路由类型：tcp / http |
| `letool.net.gateway.routes[].protocol` | String | - | 协议：json / length-field / fixed-length |
| `letool.net.gateway.routes[].connection-mode` | String | short | 连接模式：short / long |

## 核心 API

### 注解声明式——YAML 网关路由配置

```yaml
letool:
  net:
    gateway:
      routes:
        - route-id: icbc-pay
          type: tcp
          protocol: length-field
          connection-mode: long
          servers:
            - host: 192.168.1.10
              port: 8088
              weight: 1
          health-check:
            type: tcp-connect
            interval: 30s
          circuit-break:
            error-threshold: 0.5
            window-seconds: 60
            recovery-seconds: 30
          failover:
            strategy: retry-next
            max-retries: 3
        - route-id: user-service
          type: http
          servers:
            - host: localhost
              port: 8080
              weight: 1
            - host: localhost
              port: 8081
              weight: 2
```

### 编程式——NetHttpTemplate HTTP 请求

```java
@Autowired
private NetHttpTemplate http;

// 基础请求
String users = http.get("http://api.example.com/users");
String created = http.post("http://api.example.com/users", userDto);
String updated = http.put("http://api.example.com/users/1", userDto);
String deleted = http.delete("http://api.example.com/users/1");

// Builder 模式——自定义请求
String resp = http.request()
        .method("POST")
        .url("http://api.example.com/users")
        .header("Authorization", "Bearer token123")
        .header("X-Trace-Id", traceId)
        .body(userDto)
        .execute();

// 高级 execute（完整控制）
Map<String, String> headers = Map.of("Content-Type", "application/json");
String result = http.execute("POST", url, headers, bodyBytes);

// 集成负载均衡与熔断
http.setLoadBalancer(new RoundRobinLoadBalancer(servers));
http.setCircuitBreaker(new HttpCircuitBreaker(0.5, 60, 30));
```

### 编程式——TCP 客户端

```java
// 自定义协议编解码
ProtocolCodec codec = new LengthFieldCodec(2, 4, 0, 10 * 1024);

// 短连接模式
TcpClient shortClient = new TcpShortClient("192.168.1.10", 8088, codec, 10000, 60000);
shortClient.connect();
byte[] response = shortClient.sendAndReceive(requestBytes);
shortClient.disconnect();

// 长连接模式（内置连接池）
TcpClient longClient = new TcpLongClient("192.168.1.10", 8088, codec, 2, 10);
longClient.connect();
byte[] resp1 = longClient.sendAndReceive(req1);
byte[] resp2 = longClient.sendAndReceive(req2);
longClient.disconnect();
```

### 编程式——NetGateway 统一网关

```java
NetGateway gateway = NetGateway.builder()
    .route("icbc-pay", GatewayRoute.tcp("icbc-pay", lengthFieldCodec)
            .server("192.168.1.10", 8088, 1)
            .connectionMode(GatewayRoute.ConnectionMode.LONG)
            .healthCheck("tcp-connect", null, 30)
            .circuitBreak(0.5, 60, 30)
            .build())
    .route("user-service", GatewayRoute.http("user-service")
            .server("localhost", 8080, 1)
            .server("localhost", 8081, 2)
            .build())
    .build();

// 通过路由发送（自动识别 TCP/HTTP）
Object response = gateway.send("icbc-pay", requestMessage);

// 路由管理
gateway.registerRoute(newRoute);
gateway.removeRoute("old-route");
GatewayRoute route = gateway.getRoute("user-service");

// 关闭网关
gateway.close();
```
