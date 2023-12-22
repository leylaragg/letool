package com.github.leyland.letool.demo.spring.mvc.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * @ClassName <h2>RequestAttributeController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Controller
public class RequestAttributeController {

    @GetMapping("/requestAttribute1")
    public String handle1(HttpServletRequest request) {
        request.setAttribute("ra", "requestAttributeTest");
        return "/requestAttribute2";
    }


    @GetMapping("/requestAttribute2")
    @ResponseBody
    public void handle2(@RequestAttribute String ra) {
        System.out.println(ra);
    }

}
