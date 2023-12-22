package com.github.leyland.letool.demo.spring.mvc.config.exception;

/**
 * @ClassName <h2>SysExceptionResolver</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 自定义异常处理器
 */
@Component
@Order(1)
public class SysExceptionResolver{} /*implements HandlerExceptionResolver {


    *//**
     * 处理异常业务逻辑
     *//*
    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 获取到异常对象
        SysException e;
        //异常类型区分
        if (ex instanceof SysException) {
            e = (SysException) ex;
        } else {
            e = new SysException("系统正在维护....");
        }
        *//*
         * 创建ModelAndView对象
         * 设置模型信息，即异常提示，以及需要跳转的异常视图名
         *
         *//*
        ModelAndView mv = new ModelAndView();
        mv.addObject("errorMsg", e.getMessage());
        mv.setViewName("error.jsp");
        return mv;
    }
}
*/