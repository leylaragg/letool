package com.github.leyland.letool.demo.spring.mvc.service;

import com.github.leyland.letool.demo.spring.mvc.pojo.User1;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * @ClassName <h2>OtherValidationService</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public interface OtherValidationService {

    void parameterValidation(@Valid @NotNull User1 user1);

    @Size(min = 5) String returnValidation();
}
