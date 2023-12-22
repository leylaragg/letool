package com.github.rungo.rudrmboy.demo.spring.mvc.initializer;

import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.XmlWebApplicationContext;
import org.springframework.web.servlet.support.AbstractDispatcherServletInitializer;

/**
 * @ClassName <h2>MyXmlWebAppInitializer</h2>
 * @Description TODO 父子工程下web.xml基于JavaConfig的配置 -- Spring Bean XML 配置
 * @Author Rungo
 * @Version 1.0
 **/
public class MyXmlWebAppInitializer /*extends AbstractDispatcherServletInitializer*/ {

    /*@Override
    protected WebApplicationContext createRootApplicationContext() {
        XmlWebApplicationContext cxt = new XmlWebApplicationContext();
        cxt.setConfigLocation("/WEB-INF/spring-config.xml");
        return cxt;
    }

    @Override
    protected WebApplicationContext createServletApplicationContext() {
        XmlWebApplicationContext cxt = new XmlWebApplicationContext();
        cxt.setConfigLocation("/WEB-INF/spring-mvc-config.xml");
        return cxt;
    }

    @Override
    protected String[] getServletMappings() {
        return new String[]{"/"};
    }*/
}
