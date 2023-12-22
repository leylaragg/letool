package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.pojo.User1;
import com.github.leyland.letool.demo.spring.mvc.pojo.User2;
import com.github.leyland.letool.demo.spring.mvc.pojo.User3;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * @ClassName <h2>GlobalMvcValidationController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
public class GlobalMvcValidationController {

    /**
     * 支持application/json请求的参数校验
     */
    @PostMapping("/pv1")
    @ResponseBody
    public User1 pv1(@Validated @RequestBody User1 user1) {
        System.out.println(user1);
        user1.setId(0L);
        return user1;
    }

    /**
     * 支持普通请求的参数校验
     */
    @GetMapping("/pv2")
    @ResponseBody
    public void pv2(@Valid User1 user1) {
        System.out.println(user1);
    }


    /**
     * 级联校验
     */
    @PostMapping("/pv3")
    @ResponseBody
    public User2 pv3(@RequestBody @Valid User2 user2) {
        System.out.println(user2);
        return user2;
    }

    /**
     * 容器元素校验
     */
    @PostMapping("/pv4")
    @ResponseBody
    public User3 pv4(@RequestBody @Valid User3 user3) {
        System.out.println(user3);
        return user3;
    }
}
