package com.github.leyland.letool.demo.spring.mvc.controller.exception;

import com.github.leyland.letool.demo.spring.mvc.config.exception.SysException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @ClassName <h2>HandlerExceptionResolverController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Controller
public class HandlerExceptionResolverController {

    /**
     * 模拟抛出SysException
     */
    @RequestMapping("/err1")
    public void handlerExceptionResolver() throws SysException {
        throw new SysException("你的网络不好，请稍等…………");
    }

    /**
     * 模拟抛出其他异常
     */
    @RequestMapping("/err2")
    public void handlerExceptionResolver2() {
        throw new RuntimeException();
    }
}
