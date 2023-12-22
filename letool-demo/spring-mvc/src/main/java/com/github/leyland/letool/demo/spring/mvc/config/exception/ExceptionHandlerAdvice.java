package com.github.leyland.letool.demo.spring.mvc.config.exception;

import com.github.leyland.letool.demo.spring.mvc.result.ResponseResult;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @ClassName <h2>ExceptionHandlerAdvice</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
/*@ControllerAdvice
public class ExceptionHandlerAdvice {

    *//**
     * 异常处理方法
     *//*
    @ExceptionHandler
    public ModelAndView exceptionHandler(Exception e) {
        System.out.println("----ExceptionHandlerAdvice-----");
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("errorMsg", "ExceptionHandlerAdvice");
        modelAndView.setViewName("/eh/error.jsp");
        return modelAndView;
    }

    *//**
     * 返回json
     *//*
    @ExceptionHandler
    @ResponseBody
    public ResponseResult<String> exceptionHandler2(RuntimeException e) {
        return new ResponseResult<>("系统内部异常", 500, e.getMessage());
    }

}*/

@RestControllerAdvice
@Order(0)
public class ExceptionHandlerAdvice {

    @ExceptionHandler
    public ResponseResult<String> handle1(Exception e) {
        return new ResponseResult<>("ExceptionHandlerAdvice -- Exception", 500, e.getMessage());
    }

    @ExceptionHandler
    public ResponseResult<String> handle2(RuntimeException e) {
        return new ResponseResult<>("ExceptionHandlerAdvice -- RuntimeException", 500, e.getMessage());
    }
}
