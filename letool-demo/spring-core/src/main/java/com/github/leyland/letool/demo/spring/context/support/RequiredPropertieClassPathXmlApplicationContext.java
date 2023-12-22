package com.github.leyland.letool.demo.spring.context.support;

/**
 * @ClassName <h2>RequiredPropertieClassPathXmlApplicationContext</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/

import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 测试必备属性
 */
public class RequiredPropertieClassPathXmlApplicationContext extends ClassPathXmlApplicationContext {
    public RequiredPropertieClassPathXmlApplicationContext(String... configLocations) {
        super(configLocations);
    }

    /**
     * 子类容器的扩展
     */
    @Override
    protected void initPropertySources() {
        //获取环境
        ConfigurableEnvironment environment = getEnvironment();
        //设置必须属性名集合
        environment.setRequiredProperties("testA", "testB");
    }
}
