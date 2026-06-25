package com.github.leyland.letool.security.jwt.service.impl;

import com.github.leyland.letool.security.jwt.jwt.JwtUser;
import com.github.leyland.letool.security.jwt.service.JwtUserDetailsService;
import com.github.leyland.letool.security.jwt.service.UserNotFoundException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的用户详情服务实现（仅用于演示）
 * <p>
 * 实际使用时应替换为业务系统的实现
 *
 * @author Rungo
 */
public class DefaultJwtUserDetailsService implements JwtUserDetailsService {

    // 演示用的内存用户存储
    private final Map<String, JwtUser> userStore = new ConcurrentHashMap<>();

    // 演示用的密码存储
    private final Map<String, String> passwordStore = new ConcurrentHashMap<>();

    public DefaultJwtUserDetailsService() {
        // 初始化演示用户
        JwtUser admin = new JwtUser(1L, "admin");
        admin.setRoles(List.of("ADMIN", "USER"));
        admin.setPermissions(List.of("user:read", "user:write", "system:manage"));
        admin.setStatus(1);
        userStore.put("admin", admin);
        passwordStore.put("admin", "admin123");

        JwtUser user = new JwtUser(2L, "user");
        user.setRoles(List.of("USER"));
        user.setPermissions(List.of("user:read"));
        user.setStatus(1);
        userStore.put("user", user);
        passwordStore.put("user", "user123");
    }

    @Override
    public JwtUser loadUserByUsername(String username) {
        JwtUser user = userStore.get(username);
        if (user == null) {
            throw new UserNotFoundException(username);
        }
        return user;
    }

    @Override
    public JwtUser loadUserById(Long userId) {
        for (JwtUser user : userStore.values()) {
            if (user.getId().equals(userId)) {
                return user;
            }
        }
        throw new UserNotFoundException(userId);
    }

    @Override
    public boolean verifyPassword(String username, String password) {
        String storedPassword = passwordStore.get(username);
        return storedPassword != null && storedPassword.equals(password);
    }

    @Override
    public boolean isUserDisabled(Long userId) {
        JwtUser user = loadUserById(userId);
        return user.getStatus() != null && user.getStatus() == 0;
    }

    /**
     * 添加演示用户
     */
    public void addUser(JwtUser user, String password) {
        userStore.put(user.getUsername(), user);
        passwordStore.put(user.getUsername(), password);
    }

    /**
     * 移除演示用户
     */
    public void removeUser(String username) {
        userStore.remove(username);
        passwordStore.remove(username);
    }
}