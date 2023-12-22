package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.service.OtherValidationService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @ClassName <h2>OtherValidationController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
@Validated
public class OtherValidationController {

    @Resource
    private OtherValidationService otherValidationService;

    @GetMapping("/parameterValidation")
    public void user1() {
        otherValidationService.parameterValidation(null);
    }

    @GetMapping("/returnValidation")
    public String returnTest() {
        return otherValidationService.returnValidation();
    }
}