package com.github.leyland.letool.demo.spring.mvc.initializer;

/**
 * @ClassName <h2>MyWebAppInitializer</h2>
 * @Description TODO 父子工程下web.xml基于JavaConfig的配置 -- Spring Bean JavaConfig 配置
 * @Author Rungo
 * @Version 1.0
 **/
public class MyWebAppInitializer /*extends AbstractAnnotationConfigDispatcherServletInitializer*/ {
/*
    *//**
     * @return Root WebApplicationContext的配置类
     *//*
    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[]{RootConfig.class};
    }

    *//**
     * @return Servlet WebApplicationContext的配置类
     *//*
    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[]{ChildConfig.class};
    }

    *//**
     * @return 映射路径
     *//*
    @Override
    protected String[] getServletMappings() {
        return new String[]{"/"};
    }*/
}