package io.github.leylaragg.letool.security.context;

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

    /**
     * 创建空登录用户，供序列化框架使用。
     */
    public LoginUser() {
    }

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
        this.roles = immutableCopy(roles);
        this.permissions = immutableCopy(permissions);
    }

    /**
     * 获取用户 ID。
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置用户 ID。
     *
     * @param userId 用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取用户名。
     *
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名。
     *
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取昵称。
     *
     * @return 昵称
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * 设置昵称。
     *
     * @param nickname 昵称
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 获取角色快照。
     *
     * @return 不可变的角色列表
     */
    public List<String> getRoles() {
        return immutableCopy(roles);
    }

    /**
     * 设置角色快照。
     *
     * @param roles 角色列表；传入 {@code null} 时规范为空列表
     */
    public void setRoles(List<String> roles) {
        this.roles = immutableCopy(roles);
    }

    /**
     * 获取权限快照。
     *
     * @return 不可变的权限列表
     */
    public List<String> getPermissions() {
        return immutableCopy(permissions);
    }

    /**
     * 设置权限快照。
     *
     * @param permissions 权限列表；传入 {@code null} 时规范为空列表
     */
    public void setPermissions(List<String> permissions) {
        this.permissions = immutableCopy(permissions);
    }
    /**
     * 获取业务扩展数据。
     *
     * @return 业务扩展数据
     */
    public Object getExtra() {
        return extra;
    }

    /**
     * 设置业务扩展数据。
     *
     * @param extra 业务扩展数据
     */
    public void setExtra(Object extra) {
        this.extra = extra;
    }

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

    /**
     * 将外部集合规范为不可变快照。
     *
     * @param values 外部集合
     * @return 非空不可变列表
     */
    private static List<String> immutableCopy(List<String> values) {
        return values == null ? Collections.emptyList() : List.copyOf(values);
    }
}
