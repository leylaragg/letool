package com.github.leyland.letool.sample.entity;

import com.github.leyland.letool.sensitive.annotation.Sensitive;
import com.github.leyland.letool.sensitive.core.SensitiveType;

/**
 * 用户实体 —— 演示 {@link Sensitive} 脱敏注解.
 */
public class User {

    private Long id;
    private String username;

    @Sensitive(type = SensitiveType.NAME)
    private String realName;

    @Sensitive(type = SensitiveType.PHONE)
    private String phone;

    @Sensitive(type = SensitiveType.EMAIL)
    private String email;

    @Sensitive(type = SensitiveType.ID_CARD)
    private String idCard;

    public User() {}

    public User(Long id, String username, String realName, String phone, String email, String idCard) {
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.phone = phone;
        this.email = email;
        this.idCard = idCard;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
}
