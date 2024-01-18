package com.github.leyland.letool.demo.spring.util;

import org.springframework.context.ApplicationContext;

import java.sql.Timestamp;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @ClassName <h2>ApplicationContextUtil</h2>
 * @Description TODO 测试静态变量是否可以被 Spring 容器注入。
 * @Author Rungo
 * @Version 1.0
 **/
public class ApplicationContextUtil {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private static ApplicationContext applicationContext;

    public /*static*/ void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
    }

    public /*static*/ ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
