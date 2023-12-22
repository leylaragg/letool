package com.github.leyland.letool.demo.spring.listener;

/**
 * @ClassName MyApplicationEvent
 * @Description TODO
 * @Author Rungo
 * @Date 2023/4/16
 * @Version 1.0
 **/

import org.springframework.context.ApplicationEvent;

/**
 * 自定义事件
 */
public class MyApplicationEvent extends ApplicationEvent {
    private String name;


    /**
     * @param source 事件发生或与之关联的对象（从不为null）
     */
    public MyApplicationEvent(Object source, String name) {
        super(source);
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
