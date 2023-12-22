package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.pojo.User1;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.Date;
import java.util.List;

/**
 * @ClassName <h2>GlobalValidationController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Validated
@RestController
public class GlobalValidationController {

    /**
     * str参数不能是空字符串，且长度为2到5个字符
     */
    @GetMapping("/pv5")
    @ResponseBody
    public String pv5(@Valid @Size(min = 2, max = 5) @NotBlank String str) {
        System.out.println(str);
        return str;
    }

    /**
     * id参数不能为null，且最小值为333
     */
    @GetMapping("/pv6/{id}")
    @ResponseBody
    public Long pv6(@PathVariable @Valid @Min(333) @NotNull Long id) {
        System.out.println(id);
        return id;
    }

    /**
     * User1参数本身不能为null
     */
    @PostMapping("/pv7")
    @ResponseBody
    public User1 pv7(@RequestBody(required = false) @Valid @NotNull User1 user1) {
        System.out.println(user1);
        return user1;
    }

    /**
     * List<User1>参数本身不能为null，且至少包括两个元素，且内部元素不能为null
     */
    @PostMapping("/pv8")
    @ResponseBody
    public List<User1> pv7(@RequestBody @Valid @Size(min = 2) List<@NotNull User1> user1List) {
        System.out.println(user1List);
        return user1List;
    }

    @GetMapping("/pv9")
    @ResponseBody
    public @NotNull Date pv7() {
        System.out.println("-----业务逻辑-----");
        return null;
    }
}
