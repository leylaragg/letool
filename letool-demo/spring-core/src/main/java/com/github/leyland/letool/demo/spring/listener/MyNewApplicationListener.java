package com.github.leyland.letool.demo.spring.listener;

/**
 * @ClassName MyNewApplicationListener
 * @Description TODO
 * @Author Rungo
 * @Date 2023/4/16
 * @Version 1.0
 **/

import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.core.PriorityOrdered;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 自定义事件监听器，监听PayloadApplicationEvent<String>事件
 * PayloadApplicationEvent的泛型就是传递的参数的类型
 */
@Component
//注解排序
//@Order(1)
//@Priority(1)
public class MyNewApplicationListener implements ApplicationListener<PayloadApplicationEvent<String>>, PriorityOrdered {
    /**
     * 使用@Async开启异步事件
     */
    @Async
    @Override
    public void onApplicationEvent(PayloadApplicationEvent<String> event) {
        System.out.println("-------------MyNewApplicationListener事件处理线程: " + Thread.currentThread().getName() + "-" + Thread.currentThread().hashCode());
        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
        //传递的参数
        System.out.println(event.getPayload());
        System.out.println(event.getResolvableType());
        System.out.println(event.getSource());
        System.out.println(event.getTimestamp());
    }

    /**
     * 实现了PriorityOrdered接口的优先级最高，其次才会比较order值
     */
    @Override
    public int getOrder() {
        return 10;
    }
}
