package com.github.leyland.letool.demo.spring.mvc.config.exception;

import com.github.leyland.letool.demo.spring.mvc.result.ResponseResult;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @ClassName <h2>ExceptionHandlerAdvice1</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestControllerAdvice
@Order(1)
public class ExceptionHandlerAdvice1 {


    @ExceptionHandler
    public ResponseResult<String> handle(Exception e) {
        return new ResponseResult<>("ExceptionHandlerAdvice1 -- Exception异常", 500, e.getMessage());
    }

    @ExceptionHandler
    public ResponseResult<String> handle(IllegalStateException e) {
        return new ResponseResult<>("ExceptionHandlerAdvice1 -- IllegalStateException异常", 500, e.getMessage());
    }

    @ExceptionHandler
    public ResponseResult<String> handle(RuntimeException e) {
        return new ResponseResult<>("ExceptionHandlerAdvice1 -- RuntimeException异常", 500, e.getMessage());
    }
}
