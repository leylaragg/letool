package com.github.leyland.letool.demo.spring.source;

/**
 * @ClassName <h2>MyRDBeanFactoryPostProcessor</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/

import com.github.leyland.letool.demo.spring.source.registerResolvable.RDImpl;
import com.github.leyland.letool.demo.spring.source.registerResolvable.ResolvableDependency;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 测试ResolvableDependency
 */
public class MyRDBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    /**
     * beanFactory的后置处理器
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        RDImpl.RDImplA rdImplA = new RDImpl.RDImplA();
        System.out.println(rdImplA);
        beanFactory.registerResolvableDependency(ResolvableDependency.class, rdImplA);
    }
}
