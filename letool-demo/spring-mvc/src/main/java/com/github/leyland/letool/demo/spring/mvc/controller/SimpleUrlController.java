package com.github.leyland.letool.demo.spring.mvc.controller;

import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @ClassName <h2>SimpleUrlController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@org.springframework.stereotype.Controller
public class SimpleUrlController {

    @org.springframework.stereotype.Controller("/simpleUrlController1")
    public class SimpleUrlController1 implements Controller {
        @Override
        public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
            System.out.println("simpleUrlController1");
            //创建ModelAndView对象
            ModelAndView mv = new ModelAndView();
            //跳转到哪个页面    mvc会走视图解析器
            mv.setViewName("index.jsp");
            return mv;
        }
    }

    @org.springframework.stereotype.Controller("/simpleUrlController2")
    public class SimpleUrlController2 implements Controller {
        @Override
        public ModelAndView handleRequest(HttpServletRequest request, HttpServletResponse response) throws Exception {
            System.out.println("simpleUrlController2");
            //创建ModelAndView对象
            ModelAndView mv = new ModelAndView();
            //跳转到哪个页面    mvc会走视图解析器
            mv.setViewName("index.jsp");
            return mv;
        }
    }


    /**
     * 基于实现HttpRequestHandler接口的Handler
     */
    @org.springframework.stereotype.Controller("/simpleUrlController3")
    public class SimpleUrlController3 implements HttpRequestHandler {
        @Override
        public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
            System.out.println("simpleUrlController3");
            response.sendRedirect("index.jsp");
        }
    }

}
