package com.github.leyland.letool.security.context;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String nickname;
    private List<String> roles;
    private List<String> permissions;
    private Object extra;

    public LoginUser() {}

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
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
    public Object getExtra() { return extra; }
    public void setExtra(Object extra) { this.extra = extra; }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }
}
