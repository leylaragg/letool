package com.github.leyland.letool.demo.spring.mvc.controller.exception;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.FileNotFoundException;

/**
 * @ClassName <h2>OrderController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Controller
public class OrderController {

    @RequestMapping("/ehorder1")
    public void eh1() throws FileNotFoundException {
        throw new FileNotFoundException("ehorder1测试");
    }

    @RequestMapping("/ehorder2")
    public void eh2() {
        throw new RuntimeException("ehorder2测试");
    }


    @RequestMapping("/ehorder3")
    public void eh3() {
        throw new IllegalStateException(new RuntimeException("ehorder3测试"));
    }
}
