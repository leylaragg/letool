package com.github.leyland.letool.demo.spring.mvc.initializer;

/**
 * @ClassName MyXmlWebApplicationInitializer
 * @Description TODO
 * @Author Rungo
 * @Date 2023/4/19
 * @Version 1.0
 **/
public class MyXmlWebApplicationInitializer /*implements WebApplicationInitializer*/ {

    /*@Override
    public void onStartup(ServletContext servletCxt) {

        *//*
         * 创建Spring web应用容器，加载AppConfig配置类
         *//*
        XmlWebApplicationContext ac = new XmlWebApplicationContext();
        ac.setConfigLocation("classpath:spring-mvc-config.xml");
        *//*
         * 创建并注册DispatcherServlet
         *//*
        DispatcherServlet servlet = new DispatcherServlet(ac);
        //命名并注册
        ServletRegistration.Dynamic registration = servletCxt.addServlet("dispatcherServlet", servlet);
        registration.setLoadOnStartup(1);
        registration.addMapping("/");
    }*/
}
