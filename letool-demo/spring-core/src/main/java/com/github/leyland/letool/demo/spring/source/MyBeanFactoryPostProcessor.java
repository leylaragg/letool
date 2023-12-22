package com.github.leyland.letool.demo.spring.source;

/**
 * @ClassName <h2>MyBeanFactoryPostProcessor</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/

import com.github.leyland.letool.demo.spring.source.ingoreInterface.IgnoreImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * 测试Ignore
 */
public class MyBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    /**
     * beanFactory的后置处理器
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        //开启这段代码时，即使设置了byName或者byType自动注入并且满足条件
        //也不会调用Ignore接口及其实现类中的定义的setter方法进行自动注入
        //beanFactory.ignoreDependencyInterface(Ignore.class);
        //这样也行
        //beanFactory.ignoreDependencyInterface(IgnoreImpl.class);

        //忽略该类型及其子类作为参数的setter自动注入设置（byName或者byType），不能忽略constructor的自动注入。
        beanFactory.ignoreDependencyType(IgnoreImpl.PoJoA.class);
        beanFactory.ignoreDependencyType(IgnoreImpl.PoJoB.class);
    }
}