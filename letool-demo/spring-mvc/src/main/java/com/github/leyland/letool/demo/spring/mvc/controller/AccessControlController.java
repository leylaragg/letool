package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.pojo.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * @ClassName <h2>AccessControlController</h2>
 * @Description TODO0
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
public class AccessControlController {

    @GetMapping("/accessControl/{id}")
    public User accessControl(@PathVariable Integer id, String name) {
        System.out.println(id);
        System.out.println(name);
        return new User(id, name);
    }
}