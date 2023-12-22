package com.github.leyland.letool.demo.spring.listener;

/**
 * @ClassName EventService
 * @Description TODO
 * @Author Rungo
 * @Date 2023/4/16
 * @Version 1.0
 **/

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 一个服务，内部获取了ApplicationEventPublisher，可以发布事件
 */
@Component
public class EventService implements ApplicationEventPublisherAware {
    /**
     * 1 可以直接注入ApplicationEventPublisher
     */
    @Resource
    private ApplicationEventPublisher applicationEventPublisher1;

    /**
     * 2 自己接收ApplicationEventPublisher
     */
    private ApplicationEventPublisher applicationEventPublisher2;

    /**
     * 实现ApplicationEventPublisherAware接口，将会自动回调setApplicationEventPublisher方法
     *
     * @param applicationEventPublisher Spring传递的applicationEventPublisher参数
     */
    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        applicationEventPublisher2 = applicationEventPublisher;
    }

    /**
     * 通用发布ApplicationEvent事件
     */
    public void pushEvent(ApplicationEvent applicationEvent) {
        applicationEventPublisher1.publishEvent(applicationEvent);
        applicationEventPublisher2.publishEvent(applicationEvent);
    }

    /**
     * Spring 4.2的新功能，发布任意事件，且不需要是ApplicationEvent类型
     * 将会自动封装为一个PayloadApplicationEvent事件类型
     */
    public void pushEvent(Object applicationEvent) {
        applicationEventPublisher1.publishEvent(applicationEvent);
        applicationEventPublisher2.publishEvent(applicationEvent);
    }


    /**
     * 测试，通过这两个方式获取的applicationEventPublisher是否就是同一个并且就是当前上下文容器
     */
    public void applicationEventPublisherTest() {
        System.out.println(applicationEventPublisher1 instanceof AnnotationConfigApplicationContext);
        System.out.println(applicationEventPublisher2 instanceof AnnotationConfigApplicationContext);
        System.out.println(applicationEventPublisher1 == applicationEventPublisher2);
    }
}
