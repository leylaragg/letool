package com.github.leyland.letool.demo.spring.listener;

/**
 * @ClassName MyApplicationListener
 * @Description TODO
 * @Author Rungo
 * @Date 2023/4/16
 * @Version 1.0
 **/

import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 自定义事件监听器，监听MyApplicationEvent事件
 */
@Component
public class MyApplicationListener implements ApplicationListener<MyApplicationEvent> {

    /**
     * 使用@Async开启异步事件
     */
    @Async
    @Override
    public void onApplicationEvent(MyApplicationEvent event) {
        System.out.println("-------------MyApplicationListener事件处理线程: " + Thread.currentThread().getName() + "-" + Thread.currentThread().hashCode());
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
        System.out.println(event.getSource());
        //创建事件的时间毫秒值
        System.out.println(event.getTimestamp());
        System.out.println(event.getName());
    }

}
