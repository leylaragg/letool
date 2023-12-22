package com.github.leyland.letool.demo.spring.source.smart;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * @ClassName <h2>MySmartFactoryBeanIsEagerInit</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class MySmartFactoryBeanIsEagerInit {

    /**
     * 非延迟单例的SmartFactoryBean，isEagerInit返回false
     */
    @Component
    public static class MySmartFactoryBeanA implements SmartFactoryBean {

        @Override
        public Object getObject() throws Exception {
            System.out.println("MySmartFactoryBeanA getObject");
            return null;
        }

        @Override
        public Class<?> getObjectType() {
            return Object.class;
        }

        @Override
        public boolean isEagerInit() {
            return false;
        }

    }

    /**
     * 非延迟单例的SmartFactoryBean，isEagerInit返回true
     */
    @Component
    public static class MySmartFactoryBeanB implements SmartFactoryBean {

        @Override
        public Object getObject() throws Exception {
            System.out.println("MySmartFactoryBeanB getObject");
            return null;
        }

        @Override
        public Class<?> getObjectType() {
            return Object.class;
        }

        @Override
        public boolean isEagerInit() {
            return true;
        }
    }

    /**
     * prototype的SmartFactoryBean，isEagerInit返回true
     */
    @Scope("prototype")
    @Component
    public static class MySmartFactoryBeanC implements SmartFactoryBean {

        @Override
        public Object getObject() throws Exception {
            System.out.println("MySmartFactoryBeanC getObject");
            return null;
        }

        @Override
        public Class<?> getObjectType() {
            return Object.class;
        }

        @Override
        public boolean isEagerInit() {
            return true;
        }

    }

    /**
     * 非延迟单例的普通FactoryBean
     */
    @Component
    public static class MySmartFactoryBeanE implements FactoryBean {
        @Override
        public Object getObject() throws Exception {
            System.out.println("MySmartFactoryBeanC getObject");
            return null;
        }

        @Override
        public Class<?> getObjectType() {
            return Object.class;
        }
    }
}
