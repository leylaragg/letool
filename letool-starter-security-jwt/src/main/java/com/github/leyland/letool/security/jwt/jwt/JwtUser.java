package com.github.leyland.letool.security.jwt.jwt;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * JWT 用户信息载体
 *
 * @author Rungo
 */
public class JwtUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码 (仅登录时使用, JWT中不存储)
     */
    private transient String password;

    /**
     * 角色列表
     */
    private List<String> roles;

    /**
     * 权限列表
     */
    private List<String> permissions;

    /**
     * 用户附加信息
     */
    private Map<String, Object> extra;

    /**
     * 用户状态
     */
    private Integer status;

    public JwtUser() {
    }

    public JwtUser(Long id, String username) {
        this.id = id;
        this.username = username;
    }

    public JwtUser(Long id, String username, List<String> roles, List<String> permissions) {
        this.id = id;
        this.username = username;
        this.roles = roles;
        this.permissions = permissions;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "JwtUser{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", roles=" + roles +
                ", permissions=" + permissions +
                ", extra=" + extra +
                ", status=" + status +
                '}';
    }
}
