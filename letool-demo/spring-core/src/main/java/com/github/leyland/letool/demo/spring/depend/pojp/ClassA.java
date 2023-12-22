package com.github.leyland.letool.demo.spring.depend.pojp;

import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @ClassName <h2>ClassA</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Component
public class ClassA {

    @Resource
    private ClassB classB;


}
