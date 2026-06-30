package com.github.leyland.letool.sample.controller;

import com.github.leyland.letool.tool.model.R;
import com.github.leyland.letool.tool.util.IdUtil;
import com.github.leyland.letool.tool.util.JsonUtil;
import com.github.leyland.letool.tool.util.StrUtil;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示 letool-starter-tool 核心工具.
 */
@RestController
@RequestMapping("/api/public/tool")
public class ToolController {

    /**
     * 统一响应体 R -> {"code":"S001","message":"操作成功","data":"hello","timestamp":...,"traceId":"..."}
     */
    @GetMapping("/response")
    public R<String> response() {
        return R.ok("hello");
    }

    /**
     * JSON 工具 -> 序列化 / 反序列化 / Map 转换
     */
    @GetMapping("/json")
    public R<Map<String, Object>> json(@RequestParam(defaultValue = "{\"name\":\"张三\",\"age\":25}") String raw) {
        // 字符串转对象
        Object obj = JsonUtil.parseObject(raw, Object.class);
        // 字符串转 Map
        Map<String, Object> map = JsonUtil.toMap(obj);
        return R.ok(map);
    }

    /**
     * 字符串工具 -> 占位符格式化 / 驼峰下划线互转
     */
    @GetMapping("/string")
    public R<Map<String, String>> string(@RequestParam String word) {
        String formatted = StrUtil.format("Hello, {}!", word);
        String camel = StrUtil.toCamelCase(word);
        String snake = StrUtil.toSnakeCase(word);
        return R.ok(Map.of("formatted", formatted, "camel", camel, "snake", snake));
    }

    /**
     * ID 生成器 -> 雪花算法 / UUID / NanoId
     */
    @GetMapping("/id")
    public R<Map<String, String>> id() {
        return R.ok(Map.of(
                "snowflake", String.valueOf(IdUtil.nextId()),
                "uuid", IdUtil.simpleUUID(),
                "nanoId", IdUtil.nanoId()
        ));
    }
}
