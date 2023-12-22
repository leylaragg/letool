package com.github.leyland.letool.demo.spring.mvc.controller.exception;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * @ClassName <h2>ExceptionHandlerController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Controller
public class ExceptionHandlerController {


    @RequestMapping("/eh1")
    public void eh1() {
        throw new RuntimeException("eh测试");
    }


    /**
     * 异常处理方法
     *
     * @param e 抛出的异常
     */
    //@ExceptionHandler
    public ModelAndView exceptionHandler1(Exception e) {
        System.out.println("----exceptionHandler1-----");
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.addObject("errorMsg", e.getMessage());
        modelAndView.setViewName("/eh/error.jsp");
        return modelAndView;
    }
}
