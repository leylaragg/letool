package com.github.leyland.letool.demo.spring.source.smart;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @ClassName <h2>MyFactoryBean</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MyFactoryBean implements FactoryBean<Custom> {

    @Override
    public Custom getObject() throws Exception {
        return new Custom("自定义返回bean实例", ThreadLocalRandom.current().nextDouble());
    }

    @Override
    public Class<?> getObjectType() {
        return Custom.class;
    }
}