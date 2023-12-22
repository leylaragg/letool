package com.github.leyland.letool.demo.spring.mvc.config.exception;

/**
 * @ClassName <h2>SysException</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
/**
 * 自定义异常类
 */
public class SysException extends Exception {


    private String message;

    @Override
    public String getMessage() {
        return message;
    }


    public SysException(String message) {
        this.message = message;
    }
}
