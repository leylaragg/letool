package com.github.leyland.letool.demo.spring.mvc.config.exception;

/**
 * @ClassName <h2>IONoMoneyException</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class IONoMoneyException extends Exception{

    private String message;

    public IONoMoneyException(String message){
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
