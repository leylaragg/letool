package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.pojo.Person;
import com.github.leyland.letool.demo.spring.mvc.config.fromatter.annotation.PersonFormat;
import com.github.leyland.letool.demo.spring.mvc.config.fromatter.annotation.SexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;

/**
 * @ClassName <h2>AnnotationFormatterFactoryController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
public class AnnotationFormatterFactoryController {

    /*用于测试DataBinder的数据转换*/


    @RequestMapping("/annotationFormatterFactory/{person}")
    @ResponseBody
    public Person annotationFormatterFactory(@PersonFormat Person person, @SexFormat String sex) {
        System.out.println(sex);
        return person;
    }

    /*用于测试BeanWrapper的数据转换*/

    @SexFormat("2")
    @Value("1")
    private String sex;

    @PostConstruct
    public void test() {
        System.out.println(sex);
    }
}
