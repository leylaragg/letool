package com.github.leyland.letool.letool.tool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * @ClassName <h2>SpringBootApplicationStart</h2>
 * @Description
 * @Author rungo
 * @Date 2/28/2025
 * @Version 1.0
 **/
@SpringBootApplication
public class SpringBootApplicationStart {

    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(SpringBootApplicationStart.class, args);
        System.out.println(applicationContext.getBean("restTemplate"));
    }
}
