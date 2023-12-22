package com.github.leyland.letool.demo.spring.mvc.pojo;

import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

/**
 * @ClassName <h2>User1</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Data
public class User1 {

    @Positive
    @NotNull
    private Long id;

    @Range(min = 0, max = 1)
    @NotNull
    private Byte sex;

    @Size(min = 1, max = 10)
    @NotBlank
    private String name;
}
