package com.github.leyland.letool.demo.spring.mvc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @ClassName <h2>InterceptorController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Controller
public class InterceptorController {


    public InterceptorController() {
        System.out.println("InterceptorController create");
    }

    @RequestMapping(path = "/a/b/c")
    public String interceptor1() {
        System.out.println("---interceptor1 Controller invoke---");
        return "/index.jsp";
    }


    @RequestMapping(path = "/a/bbb/c")
    public String interceptor2() {
        System.out.println("---interceptor2 Controller invoke---");
        return "/index.jsp";

    }


    @RequestMapping(path = "/b")
    public String interceptor3() {
        System.out.println("---interceptor3 Controller invoke---");
        return "/index.jsp";
    }
}
