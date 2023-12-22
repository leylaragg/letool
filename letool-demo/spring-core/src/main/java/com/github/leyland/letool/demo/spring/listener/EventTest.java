package com.github.leyland.letool.demo.spring.listener;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * @ClassName EventTest
 * @Description TODO
 * @Author Rungo
 * @Date 2023/4/16
 * @Version 1.0
 **/
public class EventTest {

    private static EventService eventService;

    static {
        AnnotationConfigApplicationContext ac = new AnnotationConfigApplicationContext(StartConfig.class);
        eventService = ac.getBean(EventService.class);
    }


    /**
     * 测试获取的applicationEventPublisher是否就是同一个发布者，并且就是当前上下文容器
     */
    public static void applicationEventPublisherTest() {
        eventService.applicationEventPublisherTest();
    }

    /**
     * 发布ApplicationEvent事件
     */
    public static void pushEvent() {
        //新建一个事件
        MyApplicationEvent myApplicationEvent = new MyApplicationEvent("ApplicationEvent事件", "MyApplicationEvent");
        //发布事件
        System.out.println("---------发布事件-----------");
        eventService.pushEvent(myApplicationEvent);
    }

    /**
     * Spring 4.2的新功能，发布任意事件，且不需要是ApplicationEvent类型
     * 将会自动封装为一个PayloadApplicationEvent事件类型
     */
    public static void pushEventNew() {
        //发布事件
        System.out.println("---------Spring 4.2发布事件-----------");
        eventService.pushEvent("PayloadApplicationEvent事件");
    }

    /**
     * 如果事件类型不符合，那么不会被监听到
     */
    public static void pushEventNotlisten() {
        //发布事件
        System.out.println("---------发布不会被监听到的事件-----------");
        eventService.pushEvent(111111);
    }

    public static void main(String[] args) throws InterruptedException {
        /*
         * 1 测试获取的applicationEventPublisher是否就是同一个发布者，并且就是当前上下文容器
         */
        applicationEventPublisherTest();

        long start = System.currentTimeMillis();
        System.out.println(start);

        /*
         * 2 发布ApplicationEvent事件
         */
        pushEvent();
        /*
         * 3 Spring 4.2的新功能，发布任意事件，不需要是ApplicationEvent类型
         * 将会自动封装为一个PayloadApplicationEvent事件类型
         */
        pushEventNew();

        /*
         * 4 如果事件类型不符合，那么不会被监听到
         */
        pushEventNotlisten();


        /*
         * 如果我们取消异步任务的支持，我们会发现，这些事件都是通过主线程同步执行的，到最后才会输出"事件发布返回"
         * 而如果开启异步任务，那么事件的处理就不需要发布事件的线程执行了，提升了速度，主线程将很快返回
         */
        System.out.println("---------事件发布返回，用时: " + (System.currentTimeMillis() - start));
    }
}
