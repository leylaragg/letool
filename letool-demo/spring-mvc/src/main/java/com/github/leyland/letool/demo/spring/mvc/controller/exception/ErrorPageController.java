package com.github.leyland.letool.demo.spring.mvc.controller.exception;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @ClassName <h2>ErrorPageController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Controller
public class ErrorPageController {


    /**
     * 404响应码
     */
    @RequestMapping("/errorpage1")
    public void errorpage1(HttpServletResponse httpServletResponse) throws IOException {
        httpServletResponse.sendError(404);
    }

    /**
     * 500响应码
     */
    @RequestMapping("/errorpage2")
    public void errorpage2(HttpServletResponse httpServletResponse) throws IOException {
        httpServletResponse.sendError(500);
    }

    /**
     * 抛出异常
     */
    @RequestMapping("/errorpage3")
    public void errorpage3() {
        throw new RuntimeException();
    }
}
