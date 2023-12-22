package com.github.leyland.letool.demo.spring.test.pojo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @ClassName <h2>PHTest</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Component
@Lazy(value = false)
public class PHTest {

    @Resource(name = "${config}")
    private PH ph2;

    @Value(value = "${config}")
    private String ph3;

    @Override
    public String toString() {
        return "PHTest{" +
                "ph2=" + ph2 +
                ", ph3='" + ph3 + '\'' +
                '}';
    }

    @Component("ph")
    public static class PH {

    }
}
