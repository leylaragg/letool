package com.github.leyland.letool.demo.spring.listener;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName StartConfig
 * @Description TODO
 * @Author Rungo
 * @Date 2023/4/16
 * @Version 1.0
 **/
@ComponentScan
@Configuration
//注解开启异步任务支持，没有这个注解无法开启异步任务
//@EnableAsync
public class StartConfig {
}
