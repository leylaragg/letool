package com.github.leyland.letool.demo.spring.listener;

/**
 * @ClassName AnnoApplicationListener
 * @Description TODO
 * @Author Rungo
 * @Date 2023/4/16
 * @Version 1.0
 **/

import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Spring 4.2新的@EventListener注解
 * 这里的AnnoApplicationListener没有实现ApplicationListener接口
 */
@Component
public class AnnoApplicationListener {

    /**
     * 使用@Async开启异步事件
     * <p>
     * 这里表示监听PayloadApplicationEvent<String>类型的事件
     */
    @Async
    @EventListener
    //排序
    @Order(5)
    public void listen(PayloadApplicationEvent<String> event) {
        System.out.println("-------------listen事件处理线程: " + Thread.currentThread().getName() + "-" + Thread.currentThread().hashCode());
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
        //传递的参数
        System.out.println(event.getPayload());
        System.out.println(event.getResolvableType());
        System.out.println(event.getSource());
        System.out.println(event.getTimestamp());
    }

    /**
     * 使用@Async开启异步事件
     * <p>
     * 这里表示监听PayloadApplicationEvent<String>类型的事件
     */
    @Async
    @EventListener
    public void listen1(PayloadApplicationEvent<String> event) {
        System.out.println("-------------listen1事件处理线程: " + Thread.currentThread().getName() + "-" + Thread.currentThread().hashCode());
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
        //传递的参数
        System.out.println(event.getPayload());
        System.out.println(event.getResolvableType());
        System.out.println(event.getSource());
        System.out.println(event.getTimestamp());
    }

    /**
     * 使用@Async开启异步事件
     * <p>
     * 该方法具有String类型的参数，表示监听参数数据类型为String类型的事件
     */
    @Async
    @EventListener
    public void listen2(String event) {
        System.out.println("-------------listen2事件处理线程: " + Thread.currentThread().getName() + "-" + Thread.currentThread().hashCode());
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
        //传递的参数
        System.out.println(event);
    }

    /**
     * 使用@Async开启异步事件
     * <p>
     * 该方法没有参数，但是classes指定为String和Integer，表示监听参数数据类型为String和Integer类型的事件
     */
    @Async
    @EventListener(classes = {String.class, Integer.class})
    public void listen3() {
        System.out.println("-------------listen3事件处理线程: " + Thread.currentThread().getName() + "-" + Thread.currentThread().hashCode());
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
    }
}