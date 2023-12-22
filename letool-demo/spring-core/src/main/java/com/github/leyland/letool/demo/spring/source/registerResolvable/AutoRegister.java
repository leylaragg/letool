package com.github.leyland.letool.demo.spring.source.registerResolvable;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ResourceLoader;

/**
 * @ClassName <h2>AutoRegister</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class AutoRegister {
    private BeanFactory beanFactory;
    private ResourceLoader resourceLoader;
    private ApplicationEventPublisher applicationEventPublisher;
    private ApplicationContext applicationContext;

    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public String toString() {
        return "AutoRegister{" +
                "  \n\tbeanFactory=" + beanFactory +
                ", \n\tresourceLoader=" + resourceLoader +
                ", \n\tapplicationEventPublisher=" + applicationEventPublisher +
                ", \n\tapplicationContext=" + applicationContext +
                "  \n}";
    }
}
