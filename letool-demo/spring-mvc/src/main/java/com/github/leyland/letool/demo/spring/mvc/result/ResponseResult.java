package com.github.leyland.letool.demo.spring.mvc.result;

/**
 * @ClassName <h2>ResponseResult</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class ResponseResult<T> {
    private String msg;
    private int code;
    private T data;

    public ResponseResult(String msg, int code, T data) {
        this.msg = msg;
        this.code = code;
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public int getCode() {
        return code;
    }

    public T getData() {
        return data;
    }
}
