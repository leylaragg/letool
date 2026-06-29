package com.github.leyland.letool.websocket.core;

import java.security.Principal;
import java.util.*;

/**
 * WebSocket 用户主体，代表建立 WebSocket 连接的已验证用户身份。
 *
 * <p>在握手阶段通过 {@code WsHandshakeInterceptor} 解析 Token 生成此对象，
 * 并将其存入 WebSocket Session 的 attributes 中，供后续消息处理使用。</p>
 *
 * <p>实现了 {@link Principal} 接口，可无缝对接 Spring Security 体系。</p>
 *
 * <pre>{@code
 * // 在消息处理器中获取当前用户
 * WsPrincipal principal = session.getAttribute("principal");
 * String userId = principal.getUserId();
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class WsPrincipal implements Principal {

    // ======================== 字段 ========================

    /** 用户唯一标识 */
    private final String userId;

    /** 用户显示名称 */
    private final String username;

    /** 用户拥有的角色列表 */
    private final List<String> roles;

    /** 扩展属性（可存放租户 ID、部门 ID 等业务信息） */
    private final Map<String, Object> attributes;

    // ======================== 构造 ========================

    /**
     * 创建 WebSocket 用户主体。
     *
     * @param userId   用户唯一标识，不可为 {@code null}
     * @param username 用户显示名称
     * @param roles    用户角色列表
     */
    public WsPrincipal(String userId, String username, List<String> roles) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.username = username != null ? username : userId;
        this.roles = roles != null ? Collections.unmodifiableList(new ArrayList<>(roles)) : Collections.emptyList();
        this.attributes = new HashMap<>();
    }

    /**
     * 创建仅包含 userId 的匿名用户主体。
     *
     * @param userId 用户唯一标识
     */
    public WsPrincipal(String userId) {
        this(userId, userId, Collections.emptyList());
    }

    // ======================== Principal 实现 ========================

    /**
     * 获取用户名称（Principal 接口规范）。
     *
     * @return userId，与 Spring Security 的 Principal.getName() 行为一致
     */
    @Override
    public String getName() {
        return userId;
    }

    // ======================== 扩展方法 ========================

    /**
     * 获取用户 ID。
     *
     * @return 用户唯一标识
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取用户显示名称。
     *
     * @return 用户名，可能为空字符串
     */
    public String getUsername() {
        return username;
    }

    /**
     * 获取用户角色列表（不可修改）。
     *
     * @return 角色列表，永不为 {@code null}，可能为空列表
     */
    public List<String> getRoles() {
        return roles;
    }

    /**
     * 判断用户是否拥有指定角色。
     *
     * @param role 角色名
     * @return {@code true} 如果拥有该角色
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * 判断用户是否拥有所有指定角色。
     *
     * @param requiredRoles 需要的角色列表
     * @return {@code true} 如果拥有所有指定角色
     */
    public boolean hasAllRoles(String... requiredRoles) {
        if (requiredRoles == null || requiredRoles.length == 0) return true;
        for (String role : requiredRoles) {
            if (!roles.contains(role)) return false;
        }
        return true;
    }

    /**
     * 获取所有扩展属性。
     *
     * @return 扩展属性 Map，可修改
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 设置扩展属性。
     *
     * @param key   属性名
     * @param value 属性值
     */
    public void setAttribute(String key, Object value) {
        this.attributes.put(key, value);
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
        return (T) this.attributes.get(key);
    }

    // ======================== Object 方法 ========================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WsPrincipal that = (WsPrincipal) o;
        return userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return userId.hashCode();
    }

    @Override
    public String toString() {
        return "WsPrincipal{userId='" + userId + "', username='" + username + "', roles=" + roles + "}";
    }
}
