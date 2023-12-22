package com.github.leyland.letool.demo.spring.mvc.pojo;

import lombok.Data;
import org.hibernate.validator.constraints.Range;

import javax.validation.Valid;
import javax.validation.constraints.*;

/**
 * @ClassName <h2>User2</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Data
public class User2 {

    @Positive
    @NotNull
    private Long id;

    @Range(min = 0, max = 1)
    @NotNull
    private Byte sex;

    @Size(min = 1, max = 10)
    @NotBlank
    private String name;

    /**
     * 标注@Valid注解，对对象类型的属性进行级联校验
     */
    @Valid
    @NotNull
    private Address address;

    @Data
    public class Address {
        @NotBlank
        @Pattern(regexp = "\\d{6}")
        private String postcode;
        @NotBlank
        @Size(min = 10, max = 100)
        private String workAddress;
        @NotBlank
        @Size(min = 10, max = 100)
        private String homeAddress;

    }

}
