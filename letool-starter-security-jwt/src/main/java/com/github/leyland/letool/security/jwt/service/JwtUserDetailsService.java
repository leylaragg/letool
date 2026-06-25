package com.github.leyland.letool.security.jwt.service;

import com.github.leyland.letool.security.jwt.jwt.JwtUser;

/**
 * 用户详情服务接口
 * <p>
 * 业务系统需要实现此接口来提供用户认证信息
 *
 * @author Rungo
 */
public interface JwtUserDetailsService {

    /**
     * 根据用户名加载用户信息
     *
     * @param username 用户名
     * @return JwtUser 用户信息
     * @throws UserNotFoundException 用户不存在
     */
    JwtUser loadUserByUsername(String username);

    /**
     * 根据用户ID加载用户信息
     *
     * @param userId 用户ID
     * @return JwtUser 用户信息
     * @throws UserNotFoundException 用户不存在
     */
    JwtUser loadUserById(Long userId);

    /**
     * 验证用户密码
     *
     * @param username 用户名
     * @param password 密码
     * @return 是否验证通过
     */
    boolean verifyPassword(String username, String password);

    /**
     * 检查用户是否被禁用
     *
     * @param userId 用户ID
     * @return 是否被禁用
     */
    boolean isUserDisabled(Long userId);
}