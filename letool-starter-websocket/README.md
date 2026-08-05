# letool-starter-websocket

面向 Spring Boot Servlet 应用的单节点 WebSocket 开发框架，提供安全握手、消息路由、会话限流、房间管理、可靠发送和心跳清理等常用能力。业务只需实现鉴权和消息处理，不必在每个项目重复编写连接生命周期代码。

本模块只管理当前应用实例内的连接，不声明分布式会话能力。多实例广播需要业务结合 Redis Pub/Sub、MQ 等基础设施，把消息转发到各节点后再调用 `WsTemplate`。

## 引入依赖

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-websocket</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始

默认端点为 `/ws`，默认启用鉴权、心跳和同源校验。生产环境应实现自己的 `WsAuthenticator`：

```java
@Configuration
public class WebSocketConfiguration {

    @Bean
    public WsAuthenticator wsAuthenticator(TokenService tokenService) {
        return request -> {
            String token = request.getHeaders().getFirst("Authorization");
            UserInfo user = tokenService.verify(token);
            if (user == null) {
                throw WsException.authenticationFailed();
            }
            return new WsPrincipal(
                    user.id(), user.name(), user.roles(), Map.of("tenantId", user.tenantId()));
        };
    }
}
```

默认 `PrincipalWsAuthenticator` 只信任 HTTP 层已经建立的 `Principal`，不会自行解析查询参数或伪造身份。若应用使用 Spring Security，可以复用握手请求中的认证主体；若使用 JWT、Cookie 等业务凭据，请提供上面的扩展 Bean。原始 Token 不应写入主体扩展属性或日志。

声明消息处理方法后，starter 会在 Spring 启动阶段自动注册路由：

```java
@Component
public class ChatEndpoint {

    private final WsTemplate wsTemplate;

    public ChatEndpoint(WsTemplate wsTemplate) {
        this.wsTemplate = wsTemplate;
    }

    @WsMessageMapping("chat:send")
    @WsAuth(roles = "member")
    public void send(WsSession session, WsMessage message) {
        wsTemplate.sendToRoom("chat-room", message, session.getSessionId());
    }
}
```

注解方法签名必须为 `public void method(WsSession, WsMessage)`。重复消息类型、非法签名会让应用在启动阶段失败，避免问题延迟到线上首条消息才暴露。

也可以通过接口注册程序化处理器：

```java
@Component
public class NotificationHandler implements WsMessageHandler {

    @Override
    public String getMessageType() {
        return "notification:ack";
    }

    @Override
    public void handle(WsSession session, WsMessage message) {
        // 处理业务确认消息
    }
}
```

## 配置属性

```yaml
letool:
  websocket:
    enabled: true
    path: /ws
    # 默认不配置，沿用 Spring 的同源策略；确需跨域时再声明可信来源
    allowed-origins:
      - https://app.example.com
    max-session-per-user: 5
    max-frame-size: 64KB
    send-time-limit: 10s
    send-buffer-size: 512KB
    heartbeat:
      enabled: true
      interval: 30s
      timeout: 90s
    auth:
      enabled: true
```

| 属性 | 默认值 | 说明 |
| --- | --- | --- |
| `letool.websocket.enabled` | `true` | 模块总开关 |
| `letool.websocket.path` | `/ws` | WebSocket 端点路径 |
| `letool.websocket.allowed-origins` | 空列表 | 可信跨域来源；空列表使用同源策略 |
| `letool.websocket.max-session-per-user` | `5` | 单用户在当前节点的最大连接数 |
| `letool.websocket.max-frame-size` | `64KB` | 单个文本帧大小上限 |
| `letool.websocket.send-time-limit` | `10s` | 单次发送时间上限 |
| `letool.websocket.send-buffer-size` | `512KB` | 单连接待发送缓冲区上限 |
| `letool.websocket.heartbeat.enabled` | `true` | 是否清理无活动连接 |
| `letool.websocket.heartbeat.interval` | `30s` | 超时扫描间隔 |
| `letool.websocket.heartbeat.timeout` | `90s` | 无活动超时时间，必须大于扫描间隔 |
| `letool.websocket.auth.enabled` | `true` | 是否执行握手鉴权；关闭后连接为匿名主体 |

`max-frame-size` 和 `send-buffer-size` 支持 Spring Boot 数据大小写法。危险容量、非法路径、非正时长和错误的心跳时间关系会在应用启动时直接失败。

## 消息发送

统一通过 `WsTemplate` 发送，框架会使用应用中的 `JsonCodec`、执行并发发送保护，并返回投递统计：

```java
WsDeliveryResult userResult = wsTemplate.sendToUser("user-123", orderChanged);
WsDeliveryResult sessionResult = wsTemplate.sendToSession("session-id", WsMessage.text("hello"));
WsDeliveryResult roomResult = wsTemplate.sendToRoom("room-id", chatMessage, senderSessionId);
WsDeliveryResult allResult = wsTemplate.sendToAll(systemNotice);

if (roomResult.getFailureCount() > 0) {
    // 记录或补偿未成功投递的连接
}
```

`WsDeliveryResult` 区分目标数、成功数、失败数和失效连接数。单个连接发送失败不会中断对其他连接的投递。

## 房间与会话

```java
WsRoom room = roomManager.create("room-id", "业务房间");
roomManager.join(room.getRoomId(), session); // 会话必须已经由框架注册
roomManager.leave(room.getRoomId(), session);

Set<WsSession> userSessions = sessionManager.getUserSessions("user-123");
Set<String> roomIds = roomManager.getSessionRooms(session.getSessionId());
boolean kicked = sessionManager.kickOut(session.getSessionId());
```

连接关闭、传输异常、帧超限或心跳超时时，框架会同步清理会话索引和房间反向索引。公开查询方法返回不可变快照，调用方不能修改内部并发状态。

## 自定义 JSON

Spring 容器中存在 `JsonCodec` Bean 时，消息信封、负载、错误帧和模板发送都会复用该方案；未提供时使用 letool 默认 Fastjson2 实现：

```java
@Bean
public JsonCodec jsonCodec() {
    return new MyJsonCodec();
}
```

业务代码需要解析消息负载时，建议注入 `WsMessageCodec`：

```java
OrderCommand command = messageCodec.readPayload(message, OrderCommand.class);
```

这样结构化负载的写入和读取始终复用同一个应用级 JSON 方案。

## 客户端消息格式

```json
{
  "messageId": "客户端消息唯一标识",
  "type": "chat:send",
  "payload": "对象负载对应的 JSON 字符串",
  "timestamp": 1754391600000
}
```

框架仍兼容应用层 `ping` 消息并返回 `pong`，同时支持标准 WebSocket Pong 帧刷新活动时间。业务异常会转换为稳定的 `WS_001` 至 `WS_008` 错误码，客户端错误帧不会暴露底层异常堆栈或原始凭据。

## 扩展点

- `WsAuthenticator`：接入 Spring Security、JWT、Cookie 或业务身份系统。
- `WsMessageRouter`：替换精确类型匹配的默认路由器。
- `WsErrorHandler`：自定义安全错误帧格式和审计策略。
- `JsonCodec`：统一应用 JSON 序列化方案。
- `WsMessageHandler` / `@WsMessageMapping`：实现业务消息处理。
- `letoolWebSocketConfigurer`：使用同名 Bean 完全接管 starter 端点注册。

## 兼容性说明

本次生产化调整包含破坏性变化：删除查询参数伪 Token 配置和连接成功欢迎帧；`WsSession` 不再直接使用固定 JSON 方案发送结构化消息，业务应改用 `WsTemplate`；`WsMessage.of` 和 Builder 只接受已经编码的文本负载，结构化对象统一通过 `WsMessageCodec`；模块只承诺当前节点内的会话和房间一致性。
