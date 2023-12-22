package com.github.leyland.letool.demo.spring.mvc.controller;

import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @ClassName <h2>InitBinderController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
public class InitBinderController {

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        System.out.println("----initBinder------");
        //注册一个自定义的PropertyEditor
        //第一个参数表示转换后属性的类型，第二个参数是自定义的PropertyEditor的实例
        //这个CustomDateEditor是Spring内置的专门用于格式化时间的PropertyEditor，我们只需要设置时间字符串的模式即可
        binder.registerCustomEditor(Date.class, new CustomDateEditor(new SimpleDateFormat("yyyy-MM-dd"), false));
    }

    @GetMapping("/initBinder")
    public void handle(Date date) {
        System.out.println(date);
    }
}
