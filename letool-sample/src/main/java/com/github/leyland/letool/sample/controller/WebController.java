package com.github.leyland.letool.sample.controller;

import com.github.leyland.letool.tool.exception.BusinessException;
import com.github.leyland.letool.tool.model.R;
import com.github.leyland.letool.web.annotation.ExcludeWrapper;
import com.github.leyland.letool.web.version.ApiVersion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示 letool-starter-web 核心功能：统一响应包装、异常处理、API 版本管理.
 */
@RestController
@RequestMapping("/api/public/web")
public class WebController {

    /**
     * 自动包装为 R 格式 —— 方法实际返回 String，ResponseWrapperAdvice 自动包装成 R 结构.
     * 返回: {"code":"S001","message":"操作成功","data":"Hello Web!","timestamp":...,"traceId":"..."}
     */
    @GetMapping("/hello")
    public String hello() {
        return "Hello Web!";
    }

    /**
     * 自动包装 —— 返回对象结构.
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of("module", "letool-starter-web",
                "features", new String[]{"ResponseWrapper", "GlobalExceptionHandler", "ApiVersion", "ExcludeWrapper"});
    }

    /**
     * 排除包装 —— 标注 @ExcludeWrapper 后直接返回原始字符串，不包装为 R.
     */
    @ExcludeWrapper
    @GetMapping("/raw")
    public String raw() {
        return "This is raw output, not wrapped by R!";
    }

    /**
     * 业务异常演示 —— 抛出 BusinessException 会被 GlobalExceptionHandler 捕获并转为 R.fail().
     * 返回: {"code":"BIZ_001","message":"演示业务异常：订单不存在","data":null,...}
     */
    @GetMapping("/error")
    public R<Void> triggerError(@RequestParam(defaultValue = "BIZ_001") String code) {
        throw new BusinessException(code, "演示业务异常：订单不存在");
    }

    /**
     * API 版本 v1 —— 通过 X-API-Version: 1 请求头路由到此方法.
     */
    @ApiVersion(1)
    @GetMapping("/version")
    public R<String> versionV1() {
        return R.ok("API v1 response");
    }

    /**
     * API 版本 v2 —— 通过 X-API-Version: 2 请求头路由到此方法.
     */
    @ApiVersion(2)
    @GetMapping("/version")
    public R<String> versionV2() {
        return R.ok("API v2 response (enhanced)");
    }
}
