package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.validator.Odevity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @ClassName <h2>MyValidationController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
@Validated
public class MyValidationController {

    /**
     * 自定义约束注解测试案例，要求参数必须是奇数
     */
    @GetMapping("/odevity/{num}")
    public Long odevity(@PathVariable @Odevity(Odevity.OdevityMode.ODD) Long num) {
        System.out.println(num);
        return num;
    }

}
