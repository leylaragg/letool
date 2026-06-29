package com.github.leyland.letool.websocket.room;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 房间/频道模型，用于将多个会话组织到一个逻辑分组中进行组播通信。
 *
 * <p>典型使用场景：</p>
 * <ul>
 *   <li>聊天室房间 —— roomId = "chat:room_1001"</li>
 *   <li>直播间 —— roomId = "live:stream_2024"</li>
 *   <li>业务通知频道 —— roomId = "order:user_001"</li>
 *   <li>临时协作组 —— roomId = "collab:doc_abc"</li>
 * </ul>
 *
 * <p>线程安全：成员集合使用 {@link ConcurrentHashMap#newKeySet()} 实现。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class WsRoom {

    // ======================== 字段 ========================

    /** 房间唯一标识 */
    private final String roomId;

    /** 房间显示名称 */
    private final String name;

    /** 房间内的成员会话 ID 集合（线程安全） */
    private final Set<String> sessionIds;

    /** 房间创建时间 */
    private final LocalDateTime createdAt;

    /** 扩展属性（可存放房间类型、容量上限等业务数据） */
    private final Map<String, Object> attributes;

    // ======================== 构造 ========================

    /**
     * 创建房间。
     *
     * @param roomId 房间唯一标识，不可为 {@code null}
     * @param name   房间显示名称，{@code null} 时使用 roomId
     */
    public WsRoom(String roomId, String name) {
        this.roomId = roomId;
        this.name = name != null ? name : roomId;
        this.sessionIds = ConcurrentHashMap.newKeySet();
        this.createdAt = LocalDateTime.now();
        this.attributes = new ConcurrentHashMap<>();
    }

    // ======================== 成员管理 ========================

    /**
     * 添加成员到房间。
     *
     * @param sessionId 会话 ID
     * @return {@code true} 如果成员是新加入的，{@code false} 如果已在房间中
     */
    public boolean addMember(String sessionId) {
        return sessionIds.add(sessionId);
    }

    /**
     * 从房间移除成员。
     *
     * @param sessionId 会话 ID
     * @return {@code true} 如果成员被成功移除，{@code false} 如果成员本来就不在房间中
     */
    public boolean removeMember(String sessionId) {
        return sessionIds.remove(sessionId);
    }

    /**
     * 判断指定会话是否在房间中。
     *
     * @param sessionId 会话 ID
     * @return {@code true} 如果在房间中
     */
    public boolean containsMember(String sessionId) {
        return sessionIds.contains(sessionId);
    }

    // ======================== Getter ========================

    public String getRoomId() { return roomId; }

    public String getName() { return name; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * 获取房间成员数量。
     *
     * @return 当前成员数
     */
    public int getMemberCount() {
        return sessionIds.size();
    }

    /**
     * 获取所有成员会话 ID（不可修改的视图）。
     *
     * @return 成员 sessionId 集合
     */
    public Set<String> getMembers() {
        return Collections.unmodifiableSet(sessionIds);
    }

    /**
     * 判断房间是否为空（无成员）。
     *
     * @return {@code true} 如果房间为空
     */
    public boolean isEmpty() {
        return sessionIds.isEmpty();
    }

    // ======================== 扩展属性 ========================

    /**
     * 设置扩展属性。
     *
     * @param key   属性名
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取扩展属性。
     *
     * @param key 属性名
     * @param <T> 属性值类型
     * @return 属性值，不存在返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 获取全部扩展属性。
     *
     * @return 扩展属性 Map
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    // ======================== Object 方法 ========================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WsRoom room = (WsRoom) o;
        return roomId.equals(room.roomId);
    }

    @Override
    public int hashCode() {
        return roomId.hashCode();
    }

    @Override
    public String toString() {
        return "WsRoom{roomId='" + roomId + "', name='" + name + "', members=" + getMemberCount() + "}";
    }
}
