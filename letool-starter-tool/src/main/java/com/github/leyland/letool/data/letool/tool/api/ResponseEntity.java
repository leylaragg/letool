package com.github.leyland.letool.data.letool.tool.api;

import com.github.leyland.letool.data.letool.tool.constant.SystemConstant;
import com.github.leyland.letool.data.letool.tool.http.HttpStatus;

import java.io.Serializable;

/**
 * @ClassName <h2>ResponseEntity</h2>
 * @Description TODO 统一API响应结果封装
 * @Author Rungo
 * @Version 1.0
 **/
public class ResponseEntity<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 状态码
     */
    private int code;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 承载数据
     */
    private T data;

    /**
     * 返回消息
     */
    private String msg;

    /**
     * 根据业务代码返回信息，不承载数据
     *
     * @see SystemResultCode
     * @param resultCode    业务代码
     */
    private ResponseEntity(IResultCode resultCode) {
        this(resultCode, null, resultCode.getMessage());
    }

    /**
     * 根据业务代码返回信息，不承载数据，自定义返回信息
     *
     * @param resultCode    业务代码
     * @param msg           自定义信息
     */
    private ResponseEntity(IResultCode resultCode, String msg) {
        this(resultCode, null, msg);
    }

    /**
     * 根据业务代码返回信息
     *
     * @param resultCode    业务代码
     * @param data          承载数据
     */
    private ResponseEntity(IResultCode resultCode, T data) {
        this(resultCode, data, resultCode.getMessage());
    }

    /**
     * 根据业务代码返回信息，自定义返回信息
     *
     * @param resultCode    业务代码
     * @param data          承载数据
     * @param msg           自定义信息
     */
    private ResponseEntity(IResultCode resultCode, T data, String msg) {
        this(resultCode.getCode(), data, msg);
    }

    private ResponseEntity(int code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
        this.success = SystemResultCode.SUCCESS.code == code;
    }

    /**
     * 返回 ResponseEntity
     *
     * @param data 数据
     * @param <T>  T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> data(T data) {
        return data(data, SystemConstant.DEFAULT_SUCCESS_MESSAGE);
    }

    /**
     * 返回 ResponseEntity
     *
     * @param data 数据
     * @param msg  消息
     * @param <T>  T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> data(T data, String msg) {
        return data(HttpStatus.HTTP_OK, data, msg);
    }

    /**
     * 返回 ResponseEntity，此处会校验是否承载数据
     *
     * @param code 状态码
     * @param data 数据
     * @param msg  消息
     * @param <T>  T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> data(int code, T data, String msg) {
        return new ResponseEntity<>(code, data, data == null ? SystemConstant.DEFAULT_NULL_MESSAGE : msg);
    }

    /**
     * 返回 ResponseEntity
     *
     * @param msg 消息
     * @param <T> T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> success(String msg) {
        return new ResponseEntity<>(SystemResultCode.SUCCESS, msg);
    }

    /**
     * 返回 ResponseEntity
     *
     * @param resultCode 业务代码
     * @param <T>        T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> success(IResultCode resultCode) {
        return new ResponseEntity<>(resultCode);
    }

    /**
     * 返回 ResponseEntity
     *
     * @param resultCode 业务代码
     * @param msg        消息
     * @param <T>        T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> success(IResultCode resultCode, String msg) {
        return new ResponseEntity<>(resultCode, msg);
    }

    /**
     * 返回 ResponseEntity
     *
     * @param msg 消息
     * @param <T> T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> fail(String msg) {
        return new ResponseEntity<>(SystemResultCode.FAILURE, msg);
    }

    /**
     * 返回 ResponseEntity
     *
     * @param code 状态码
     * @param msg  消息
     * @param <T>  T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> fail(int code, String msg) {
        return new ResponseEntity<>(code, null, msg);
    }

    /**
     * 返回 ResponseEntity
     *
     * @param resultCode 业务代码
     * @param <T>        T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> fail(IResultCode resultCode) {
        return new ResponseEntity<>(resultCode);
    }

    /**
     * 返回 ResponseEntity
     *
     * @param resultCode 业务代码
     * @param msg        消息
     * @param <T>        T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> fail(IResultCode resultCode, String msg) {
        return new ResponseEntity<>(resultCode, msg);
    }

    /**
     * 返回 ResponseEntity
     * 根据 flag 来决定返回 "操作成功" 或者 "操作失败"
     *
     * @param flag 成功状态
     * @param <T>  T 泛型标记
     * @return ResponseEntity
     */
    public static <T> ResponseEntity<T> status(boolean flag) {
        return flag ? success(SystemConstant.DEFAULT_SUCCESS_MESSAGE) : fail(SystemConstant.DEFAULT_FAILURE_MESSAGE);
    }
}
