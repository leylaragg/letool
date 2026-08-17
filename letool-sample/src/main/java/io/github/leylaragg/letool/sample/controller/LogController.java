package io.github.leylaragg.letool.sample.controller;

import io.github.leylaragg.letool.log.annotation.AuditLog;
import io.github.leylaragg.letool.log.annotation.MethodLog;
import io.github.leylaragg.letool.log.audit.AuditType;
import io.github.leylaragg.letool.tool.model.R;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示 letool-starter-log 核心功能：方法日志（{@code @MethodLog}）和
 * 审计日志（{@code @AuditLog}）。
 */
@RestController
@RequestMapping("/api/public/log")
public class LogController {

    /**
     * 基础方法日志 —— 自动记录入参、出参、耗时。
     *
     * @param name 问候对象名称
     * @return 问候结果
     */
    @MethodLog
    @GetMapping("/method")
    public R<Map<String, Object>> logMethod(@RequestParam String name) {
        return R.ok(Map.of("greeting", "Hello, " + name + "!"));
    }

    /**
     * 自定义方法日志 —— 设置标题、限制输出长度。
     *
     * @return 自定义方法日志示例结果
     */
    @MethodLog(value = "自定义日志标题", maxResultLength = 100)
    @GetMapping("/method-custom")
    public R<String> logMethodCustom() {
        return R.ok("Custom @MethodLog demo");
    }

    /**
     * 不记录入参和出参，适合含敏感参数的方法。
     *
     * @param password 模拟的敏感密码
     * @return 不包含敏感信息的执行结果
     */
    @MethodLog(logArgs = false, logResult = false)
    @GetMapping("/method-no-args")
    public R<String> logMethodNoArgs(@RequestParam(defaultValue = "secret-password") String password) {
        return R.ok("Method executed (in/out params NOT logged)");
    }

    /**
     * 审计日志 —— 自动记录操作人、IP、操作类型、业务单号等信息。
     *
     * @param userId 用户标识
     * @return 审计示例结果
     */
    @AuditLog(operation = "查看审计示例", type = AuditType.BUSINESS, bizNo = "#userId")
    @GetMapping("/audit")
    public R<String> audit(@RequestParam String userId) {
        return R.ok("Audit log for user: " + userId);
    }

    /**
     * 管理类审计日志。
     *
     * @return 管理审计示例结果
     */
    @AuditLog(operation = "系统配置查询", type = AuditType.ADMIN)
    @GetMapping("/audit-admin")
    public R<String> auditAdmin() {
        return R.ok("Admin audit log recorded");
    }
}
