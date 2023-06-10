package com.github.rungo.test;

import com.github.rungo.rudrmboy.demo.spring.context.support.RequiredPropertieClassPathXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.testng.annotations.Test;

/**
 * @ClassName <h2>initPropertySources</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class initPropertySourcesTest {

    @Test
    public void initPropertySources() {
        System.setProperty("config", "config");

        //前面讲过，也可以通过JVM属性设置要激活的profile
        System.setProperty("spring.profiles.active", "dev");

        //必备属性，如果注释掉这两个属性，那么启动报错
        System.setProperty("testA", "aaa");
        System.setProperty("testB", "bbb");

        //初始化容器,加入多套环境
        RequiredPropertieClassPathXmlApplicationContext ac = new RequiredPropertieClassPathXmlApplicationContext("spring-${config}.xml",
                "spring-${config}-dev.xml", "spring-${config}-uat.xml");
        System.out.println(ac.getBean("firstSpringSource"));

    }


    @Test
    public void destroy() {
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config.xml");
        ac.refresh();

    }
}
