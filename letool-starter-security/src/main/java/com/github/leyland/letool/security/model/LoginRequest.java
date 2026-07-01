package com.github.leyland.letool.security.model;

import java.io.Serializable;

/**
 * 登录请求模型。
 *
 * @author leyland
 * @since 2.0.0
 */
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名 */
    private String username;

    /** 密码 */
    private String password;

    /** 验证码（可选） */
    private String captcha;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getCaptcha() { return captcha; }
    public void setCaptcha(String captcha) { this.captcha = captcha; }
}
