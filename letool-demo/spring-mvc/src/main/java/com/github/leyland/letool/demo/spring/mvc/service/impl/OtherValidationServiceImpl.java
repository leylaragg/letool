package com.github.leyland.letool.demo.spring.mvc.service.impl;

import com.github.leyland.letool.demo.spring.mvc.pojo.User1;
import com.github.leyland.letool.demo.spring.mvc.service.OtherValidationService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * @ClassName <h2>OtherValidationServiceImpl</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Service
@Validated
public class OtherValidationServiceImpl implements OtherValidationService {

    @Override
    public void parameterValidation(User1 user1) {
        System.out.println(user1);
    }

    @Override
    public @NotBlank @Size(min = 4) String returnValidation() {
        return "";
    }

}
