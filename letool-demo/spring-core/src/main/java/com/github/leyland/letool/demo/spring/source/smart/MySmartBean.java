package com.github.leyland.letool.demo.spring.source.smart;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * @ClassName <h2>MySmartBean</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Component
public class MySmartBean {

    @Component
    public static class MySmartBeanA implements SmartInitializingSingleton {
        @Override
        public void afterSingletonsInstantiated() {
            System.out.println("MySmartBeanA SmartInitializingSingleton");
        }

        //依赖注入完成后，在执行 init-method 方法之前被调用
        @PostConstruct
        public void init() {
            System.out.println("MySmartBeanA initMethod ");
        }

        public MySmartBeanA() {
            System.out.println("MySmartBeanA constructor");
        }

    }

    @Component
    public static class MySmartBeanB implements SmartInitializingSingleton {
        @Override
        public void afterSingletonsInstantiated() {
            System.out.println("MySmartBeanB SmartInitializingSingleton");
        }

        //依赖注入完成后，在执行 init-method 方法之前被调用
        @PostConstruct
        public void init() {
            System.out.println("MySmartBeanB initMethod ");
        }

        public MySmartBeanB() {
            System.out.println("MySmartBeanB constructor");
        }

    }

    @Component
    public static class MySmartBeanC  {

        //依赖注入完成后，在执行 init-method 方法之前被调用
        @PostConstruct
        public void init() {
            System.out.println("MySmartBeanC initMethod ");
        }

        public MySmartBeanC() {
            System.out.println("MySmartBeanC constructor");
        }
    }
}
