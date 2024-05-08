package com.github.leyland.letool.demo.spring.mvc.initializer;

import com.github.leyland.letool.demo.spring.mvc.config.WebConfig;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

/**
 * @ClassName MyWebApplicationInitializer
 * @Description TODO
 * @Author Rungo
 * @Date 2023/4/19
 * @Version 1.0
 **/
public class MyWebApplicationInitializer /*implements WebApplicationInitializer*/ {

    /*@Override
    public void onStartup(ServletContext servletCxt) {

        *//**
         * 创建一个Spring web应用容器，加载AppConfig配置类
         *//*
        AnnotationConfigWebApplicationContext ac = new AnnotationConfigWebApplicationContext();
        ac.register(WebConfig.class);
        ac.refresh();

        *//**
         * 创建并注册DispatcherServlet，关联上的容器
         *//*

        DispatcherServlet servlet = new DispatcherServlet(ac);
        //命名并注册
        ServletRegistration.Dynamic registration = servletCxt.addServlet("dispatcherServlet", servlet);
        registration.setLoadOnStartup(1);
        registration.addMapping("/");
    }*/
}
