package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.pojo.MyModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/**
 * @ClassName <h2>SessionAttributesController2</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
public class SessionAttributesController2 {

    /**
     * @param myModel 尝试获取通过@ModelAttribute存入的数据
     * @param model   尝试获取手动存入的model数据
     * @param id      尝试获取手动存入session的数据
     */
    @GetMapping("/getsessionAttributes")
    public void handle1(@SessionAttribute(name = "myModel", required = false) MyModel myModel,
                        @SessionAttribute(name = "model", required = false) MyModel model,
                        @SessionAttribute String id) {
        System.out.println(myModel);
        System.out.println(model);
        System.out.println(id);
    }
}

