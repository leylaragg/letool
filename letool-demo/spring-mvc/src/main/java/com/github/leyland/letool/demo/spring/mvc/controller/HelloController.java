package com.github.leyland.letool.demo.spring.mvc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @ClassName HelloController
 * @Description TODO 第一个控制器类
 * @Author Rungo
 * @Date 2023/4/20
 * @Version 1.0
 **/
@Controller
public class HelloController {

    /**
     * 请求路径映射到某个方法
     * /hello路径映射到sayHello()方法
     */
    @RequestMapping(path = "/hello")
    public String sayHello() {
        System.out.println("Hello SpringMVC");
        //转发到success.jsp的视图
        return "success.jsp";
    }
}
