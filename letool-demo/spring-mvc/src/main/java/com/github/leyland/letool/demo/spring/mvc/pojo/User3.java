package com.github.leyland.letool.demo.spring.mvc.pojo;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;
import java.util.Map;

/**
 * @ClassName <h2>User3</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Data
public class User3 {

    /**
     * list至少包含两个元素
     * 元素不能为null且进行级联校验
     */
    @Size(min = 2)
    @NotNull
    @Valid
    private List<@NotNull InnerClass> user1s;

    /**
     * map不能为空
     * key的长度至少为2个字符且不是空白字符，value不能为null且进行级联校验
     */
    @NotEmpty
    private Map<@NotBlank @Size(min = 2) String, @NotNull @Valid InnerClass> stringUser1Map;

    /**
     * 数组不能为空，且对元素进行级联校验
     * 校验似乎有一定的限制，比如目前测试无法通过@NotNull校验null元素
     */
    @NotEmpty
    @Valid
    @NotNull
    private @NotNull InnerClass[] user2s;

    @Data
    public static class InnerClass {
        @NotNull
        @Min(1)
        private Long id;

        @NotBlank
        @Size(min = 5)
        private String name;
    }
}
