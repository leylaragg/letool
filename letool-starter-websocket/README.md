# letool-starter-websocket

WebSocket 模块，提供消息路由（类似 Spring MVC 的 `@MessageMapping` 模式）、房间管理、在线状态追踪、心跳检测和分布式会话支持。

## Maven 坐标

```xml
<dependency>
    <groupId>com.github.leyland</groupId>
    <artifactId>letool-starter-websocket</artifactId>
    <version>${letool.version}</version>
</dependency>
```

## 快速开始

**1. 配置 WebSocket 端点（可选，使用默认值即可）：**

```yaml
letool:
  websocket:
    enabled: true
    path: /ws
    allowed-origins: "*"
```

**2. 实现消息处理器，注入 `WsTemplate` 发送消息：**

```java
@Component
public class ChatMessageHandler implements WsMessageHandler {

    @Override
    public String getMessageType() {
        return "chat";
    }

    @Override
    public void handle(WsSession session, WsMessage message) {
        // 处理聊天消息
    }
}
```

## 配置属性

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `letool.websocket.enabled` | `true` | 模块开关 |
| `letool.websocket.path` | `/ws` | WebSocket 连接端点 |
| `letool.websocket.allowed-origins` | `*` | 跨域来源 |
| `letool.websocket.max-session-per-user` | `5` | 单用户最大会话数 |
| `letool.websocket.max-frame-size` | `65536` | 最大帧大小（字节） |
| `letool.websocket.heartbeat.enabled` | `true` | 心跳检测开关 |
| `letool.websocket.heartbeat.interval` | `30` | 心跳检测间隔（秒） |
| `letool.websocket.heartbeat.timeout` | `90` | 心跳超时时间（秒） |
| `letool.websocket.auth.enabled` | `true` | 连接鉴权开关 |
| `letool.websocket.auth.token-param` | `token` | Token 查询参数名 |

## 核心 API 示例

### 1. 消息路由（类似 Spring MVC）

**注解式（`@WsMessageMapping`）：**

```java
@Component
public class GameMessageHandler {

    @WsMessageMapping("chat")
    public void handleChat(WsSession session, WsMessage message) {
        // 处理聊天消息
    }

    @WsMessageMapping("move")
    public void handleMove(WsSession session, WsMessage message) {
        // 处理移动操作
    }
}
```

**编程式（实现 `WsMessageHandler` 接口）：**

```java
@Component
public class NotificationHandler implements WsMessageHandler {

    @Override
    public String getMessageType() {
        return "notification";
    }

    @Override
    public void handle(WsSession session, WsMessage message) {
        String payload = message.getPayloadAsString();
        // 处理通知消息
    }
}
```

### 2. 消息发送（WsTemplate）

```java
@Autowired
private WsTemplate wsTemplate;

// 定向推送给用户（该用户所有活跃会话）
wsTemplate.sendToUser("user123", "您的订单已发货");

// 定向推送给指定会话
wsTemplate.sendToSession("sessionId", WsMessage.text("Hello"));

// 房间广播
wsTemplate.sendToRoom("room_1", "欢迎新成员");

// 房间广播（排除发送者）
wsTemplate.sendToRoom("room_1", chatMessage, senderSessionId);

// 全量广播
wsTemplate.sendToAll("系统将于 23:00 维护");

// 全量广播（条件过滤，只发给管理员）
wsTemplate.sendToAll(msg, session -> {
    WsPrincipal p = session.getAttribute("principal");
    return p != null && p.hasRole("admin");
});
```

### 3. 会话管理（WsSessionManager）

```java
@Autowired
private WsSessionManager sessionManager;

// 查询用户的所有会话
Set<WsSession> sessions = sessionManager.getUserSessions("user123");

// 查询单个会话
WsSession session = sessionManager.getSession("sessionId");

// 在线统计
long sessionCount = sessionManager.getSessionCount();
long onlineUsers = sessionManager.getOnlineUserCount();
Set<String> onlineIds = sessionManager.getOnlineUserIds();

// 强制踢出
sessionManager.kickOut("sessionId");   // 会先发通知，再断开

// 检查会话状态
boolean alive = sessionManager.isSessionAlive("sessionId");
```
