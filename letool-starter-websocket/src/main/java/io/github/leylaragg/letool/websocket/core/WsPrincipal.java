package io.github.leylaragg.letool.websocket.core;

import java.security.Principal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * WebSocket 不可变用户主体。
 *
 * <p>主体只能由握手认证器创建。角色和扩展属性在构造时复制，连接建立后不能被
 * 外部代码修改，避免授权判断受到共享可变状态影响。</p>
 */
public final class WsPrincipal implements Principal {

    private final String userId;
    private final String username;
    private final Set<String> roles;
    private final Map<String, Object> attributes;
    private final boolean authenticated;

    /**
     * 创建完整用户主体。
     *
     * @param userId 用户唯一标识，不允许为空
     * @param username 用户显示名称；为空时使用用户标识
     * @param roles 用户角色集合
     * @param attributes 不包含凭据的业务扩展属性
     */
    public WsPrincipal(
            String userId,
            String username,
            Collection<String> roles,
            Map<String, Object> attributes) {
        this(userId, username, roles, attributes, true);
    }

    /**
     * 创建用户主体并明确认证状态。
     *
     * @param userId 用户唯一标识
     * @param username 用户显示名称
     * @param roles 用户角色集合
     * @param attributes 不包含凭据的业务扩展属性
     * @param authenticated 是否已经通过身份认证
     */
    private WsPrincipal(
            String userId,
            String username,
            Collection<String> roles,
            Map<String, Object> attributes,
            boolean authenticated) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        this.userId = userId;
        this.username = username == null || username.isBlank() ? userId : username;
        this.roles = roles == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(roles));
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        this.authenticated = authenticated;
    }

    /**
     * 创建不包含扩展属性的用户主体。
     *
     * @param userId 用户唯一标识
     * @param username 用户显示名称
     * @param roles 用户角色集合
     */
    public WsPrincipal(String userId, String username, Collection<String> roles) {
        this(userId, username, roles, Map.of());
    }

    /**
     * 创建只包含用户标识的主体。
     *
     * @param userId 用户唯一标识
     */
    public WsPrincipal(String userId) {
        this(userId, userId, Set.of(), Map.of());
    }

    /**
     * 创建匿名主体。
     *
     * @param identifier 匿名连接随机标识
     * @return 未认证的匿名主体
     */
    public static WsPrincipal anonymous(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        return new WsPrincipal("anonymous:" + identifier, "anonymous", Set.of(), Map.of(), false);
    }

    /**
     * 获取标准主体名称。
     *
     * @return 用户唯一标识
     */
    @Override
    public String getName() {
        return userId;
    }

    /**
     * 获取用户唯一标识。
     *
     * @return 用户唯一标识
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取用户显示名称。
     *
     * @return 用户显示名称
     */
    public String getUsername() {
        return username;
    }

    /**
     * 获取不可变角色集合。
     *
     * @return 不可变角色集合
     */
    public Set<String> getRoles() {
        return roles;
    }

    /**
     * 判断主体是否已经通过身份认证。
     *
     * @return 已认证时返回 {@code true}
     */
    public boolean isAuthenticated() {
        return authenticated;
    }

    /**
     * 判断主体是否拥有指定角色。
     *
     * @param role 角色名称
     * @return 拥有该角色时返回 {@code true}
     */
    public boolean hasRole(String role) {
        return role != null && roles.contains(role);
    }

    /**
     * 判断主体是否拥有全部指定角色。
     *
     * @param requiredRoles 必需角色
     * @return 拥有全部角色时返回 {@code true}
     */
    public boolean hasAllRoles(String... requiredRoles) {
        if (requiredRoles == null || requiredRoles.length == 0) {
            return true;
        }
        for (String requiredRole : requiredRoles) {
            if (!hasRole(requiredRole)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取不可变扩展属性。
     *
     * @return 不可变扩展属性
     */
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    /**
     * 获取指定扩展属性。
     *
     * @param key 属性名称
     * @param <T> 属性类型
     * @return 属性值，不存在时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 按用户唯一标识比较主体。
     *
     * @param object 待比较对象
     * @return 用户标识相同时返回 {@code true}
     */
    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof WsPrincipal that)) {
            return false;
        }
        return userId.equals(that.userId);
    }

    /**
     * 计算主体哈希值。
     *
     * @return 用户标识哈希值
     */
    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }

    /**
     * 返回不包含扩展属性的安全摘要。
     *
     * @return 安全主体摘要
     */
    @Override
    public String toString() {
        return "WsPrincipal{userId='" + userId + "', username='" + username + "', roles=" + roles + '}';
    }
}
