package com.github.leyland.data.letool.security.jwt.jwt;

import java.util.List;

/**
 * @ClassName <h2>JwtUser</h2>
 * @Description TODO 用户信息
 * @Author Rungo
 * @Version 1.0
 **/
public class JwtUser {

    /**
     * id
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 角色
     */
    private List<String> roles;

    /**
     * 权限
     */
    private List<String> permissions;

}
