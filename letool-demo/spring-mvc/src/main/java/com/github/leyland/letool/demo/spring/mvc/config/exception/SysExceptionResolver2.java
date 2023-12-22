package com.github.leyland.letool.demo.spring.mvc.config.exception;

/**
 * @ClassName <h2>SysExceptionResolver2</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 自定义第二个异常处理器
 */
@Component
@Order(2)
public class SysExceptionResolver2{} /*implements HandlerExceptionResolver {

    *//**
     * 处理异常业务逻辑
     *//*
    @Override
    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        //直接设置ModelAndView
        ModelAndView mv = new ModelAndView();
        mv.addObject("errorMsg", "第二个异常处理器");
        mv.setViewName("error.jsp");
        return mv;
    }
}
*/
