package com.github.leyland.letool.security.context;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 当前登录用户信息模型，存储在 {@link org.springframework.security.core.context.SecurityContext} 中。
 *
 * <p>包含用户身份、角色和权限列表，提供 {@link #hasRole} / {@link #hasPermission} 便捷判断方法。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 用户名（登录名） */
    private String username;

    /** 昵称（显示名） */
    private String nickname;

    /** 角色列表 */
    private List<String> roles;

    /** 权限标识列表 */
    private List<String> permissions;

    /** 扩展数据（业务自定义） */
    private Object extra;

    public LoginUser() {}

    /**
     * 构造登录用户。
     *
     * @param userId      用户 ID
     * @param username    用户名
     * @param roles       角色列表，{@code null} 时初始化为空列表
     * @param permissions 权限列表，{@code null} 时初始化为空列表
     */
    public LoginUser(Long userId, String username, List<String> roles, List<String> permissions) {
        this.userId = userId;
        this.username = username;
        this.roles = roles != null ? roles : Collections.emptyList();
        this.permissions = permissions != null ? permissions : Collections.emptyList();
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    /** @return 不可变的角色列表 */
    public List<String> getRoles() { return Collections.unmodifiableList(roles); }
    public void setRoles(List<String> roles) { this.roles = roles; }
    /** @return 不可变的权限列表 */
    public List<String> getPermissions() { return Collections.unmodifiableList(permissions); }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
    public Object getExtra() { return extra; }
    public void setExtra(Object extra) { this.extra = extra; }

    /**
     * 判断用户是否拥有指定角色。
     *
     * @param role 角色标识
     * @return {@code true} 如果拥有
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * 判断用户是否拥有指定权限。
     *
     * @param permission 权限标识
     * @return {@code true} 如果拥有
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
