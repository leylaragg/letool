package com.github.leyland.letool.demo.spring.mvc.config.exception;

import com.github.leyland.letool.demo.spring.mvc.result.ResponseResult;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.FileNotFoundException;
import java.net.ConnectException;

/**
 * @ClassName <h2>ExceptionHandlerAdvice2</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestControllerAdvice
@Order(-1)
public class ExceptionHandlerAdvice2 {

    @ExceptionHandler({ConnectException.class, FileNotFoundException.class})
    public ResponseResult<String> handle1(Exception e) {
        return new ResponseResult<>("ExceptionHandlerAdvice2 - handle1", 500, e.getMessage());
    }

    @ExceptionHandler({SecurityException.class, IllegalStateException.class})
    public ResponseResult<String> handle2(Exception e) {
        return new ResponseResult<>("ExceptionHandlerAdvice2 - handle2", 500, e.getMessage());
    }
}
