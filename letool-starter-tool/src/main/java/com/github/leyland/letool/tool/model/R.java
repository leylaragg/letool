package com.github.leyland.letool.tool.model;

import java.io.Serializable;
import java.time.Instant;

/**
 * 统一响应体——所有 Controller 返回值推荐包装为此类型.
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>统一前后端数据交互格式，前端可无差别解析所有接口响应</li>
 *   <li>成功码固定为 {@code "00000"}，减少前端对成功语义的判断逻辑</li>
 *   <li>携带 {@code timestamp}，方便排查问题时按时间定位日志</li>
 *   <li>实现 {@link Serializable}，支持 RPC 序列化和缓存存储</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <h4>声明式（推荐）</h4>
 * <p>引入 {@code letool-starter-web} 模块后，{@code ResponseBodyAdvice} 自动将
 * 非 {@code R} 类型的返回值包装为 {@code R<T>}，Controller 可直接返回业务对象.</p>
 *
 * <h4>编程式</h4>
 * <pre>{@code
 * // 成功（无数据）
 * return R.ok();
 *
 * // 成功（带数据）
 * return R.ok(user);
 *
 * // 失败
 * return R.fail("USER_001", "用户名不能为空");
 *
 * // 失败（带数据，如校验失败的字段信息）
 * return R.fail("VALID_001", "参数校验失败", errorFields);
 * }</pre>
 *
 * @param <T> 业务数据类型，无数据时可为 {@code Void}
 */
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 成功状态码 */
    private static final String SUCCESS_CODE = "00000";
    /** 成功默认消息 */
    private static final String SUCCESS_MSG = "ok";

    /** 状态码 */
    private String code;
    /** 提示消息 */
    private String message;
    /** 业务数据 */
    private T data;
    /** 响应时间戳（毫秒） */
    private long timestamp;

    private R() {
        this.timestamp = Instant.now().toEpochMilli();
    }

    // ======================== 静态工厂方法 ========================

    /**
     * 成功响应（无数据）.
     *
     * @param <T> 数据类型
     * @return code="00000", message="ok", data=null
     */
    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.code = SUCCESS_CODE;
        r.message = SUCCESS_MSG;
        return r;
    }

    /**
     * 成功响应（带数据）.
     *
     * @param data 业务数据
     * @param <T>  数据类型
     * @return code="00000", message="ok", data=传入数据
     */
    public static <T> R<T> ok(T data) {
        R<T> r = ok();
        r.data = data;
        return r;
    }

    /**
     * 失败响应（无数据）.
     *
     * @param code    错误码（全局唯一，如 "USER_001"）
     * @param message 面向用户的错误描述
     * @param <T>     数据类型
     * @return 失败响应体
     */
    public static <T> R<T> fail(String code, String message) {
        R<T> r = new R<>();
        r.code = code;
        r.message = message;
        return r;
    }

    /**
     * 失败响应（带数据，如各字段的校验错误信息）.
     *
     * @param code    错误码
     * @param message 错误描述
     * @param data    附加数据（如 {@code Map<String, String>} 的字段错误映射）
     * @param <T>     数据类型
     * @return 失败响应体
     */
    public static <T> R<T> fail(String code, String message, T data) {
        R<T> r = fail(code, message);
        r.data = data;
        return r;
    }

    // ======================== getter / setter ========================

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    /**
     * 获取响应时间戳（毫秒）.
     *
     * @return 响应体创建时刻的 epoch 毫秒数
     */
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    /**
     * 判断是否为成功响应.
     *
     * @return {@code true} 如果 code 为 "00000"
     */
    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }
}
