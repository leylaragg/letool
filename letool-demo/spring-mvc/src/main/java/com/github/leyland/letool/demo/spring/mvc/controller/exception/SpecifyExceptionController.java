package com.github.leyland.letool.demo.spring.mvc.controller.exception;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.FileNotFoundException;
import java.net.ConnectException;

/**
 * @ClassName <h2>SpecifyExceptionController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Controller
public class SpecifyExceptionController {

    @RequestMapping("/se1")
    public void se1() throws FileNotFoundException {
        throw new FileNotFoundException("se1");
    }

    @RequestMapping("/se2")
    public void se2() throws ConnectException {
        throw new ConnectException("se2");
    }


    @RequestMapping("/se3")
    public void se3() {
        throw new SecurityException("se3");
    }


    @RequestMapping("/se4")
    public void se4() {
        throw new IllegalStateException(new RuntimeException("se4"));
    }


    @RequestMapping("/se5")
    public void se5() throws ReflectiveOperationException {
        throw new ReflectiveOperationException("se5");
    }
}
