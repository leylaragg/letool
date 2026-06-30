package com.github.leyland.letool.sample.controller;

import com.github.leyland.letool.sample.entity.User;
import com.github.leyland.letool.tool.model.R;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 演示 letool-starter-sensitive 数据脱敏 —— Jackson 序列化自动脱敏.
 * <p>
 * 访问 /api/public/sensitive/user 查看手机号/邮箱/身份证自动脱敏效果.
 */
@RestController
@RequestMapping("/api/public/sensitive")
public class SensitiveController {

    @GetMapping("/user")
    public R<User> user() {
        User user = new User(
                1L, "zhangsan", "张三丰",
                "13812345678", "zhangsan@example.com",
                "320123199001011234"
        );
        // JSON 返回会自动脱敏:
        // realName→"张*", phone→"138****5678", email→"z***@example.com", idCard→"3201**********1234"
        return R.ok(user);
    }
}
