package com.github.leyland.letool.demo.spring.mvc.controller.exception;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @ClassName <h2>ExceptionHandlerController2</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Controller
public class ExceptionHandlerController2 {


    @RequestMapping("/eh2")
    public void eh2() {
        throw new RuntimeException("eh2测试");
    }


}
