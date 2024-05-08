package com.github.leyland.letool.demo.spring.mvc.config.handler;

/**
 * @ClassName <h2>MyInterceptor1</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 自定义拦截器
 */
@Component
public class MyInterceptor2 implements HandlerInterceptor {
    /**
     * 预处理,controller方法执行前
     *
     * @return true表示, 执行下一个拦截器, 没有拦截器了就执行controller中的方法;false表示不放行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("MyInterceptor2 preHandle invoke ,true");

        //支持转发
//        RequestDispatcher requestDispatcher = request.getRequestDispatcher("/index.jsp");
//        requestDispatcher.forward(request, response);

        //支持包含
        RequestDispatcher requestDispatcher = request.getRequestDispatcher("/index.jsp");
        requestDispatcher.include(request, response);

        //支持重定向
//        response.sendRedirect("/mvc/index.jsp");
        return false;
    }

    /**
     * 后处理
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        System.out.println("MyInterceptor2 postHandle invoke");
    }

    /**
     * 请求处理完毕调用
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        System.out.println("MyInterceptor2 afterCompletion invoke");
    }
}
