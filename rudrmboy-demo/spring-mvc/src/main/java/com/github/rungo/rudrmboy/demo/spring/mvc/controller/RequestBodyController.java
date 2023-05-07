package com.github.rungo.rudrmboy.demo.spring.mvc.controller;

import com.github.rungo.rudrmboy.demo.spring.mvc.pojo.User;
import org.springframework.web.bind.annotation.*;

/**
 * @ClassName <h2>RequestBodyController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
public class RequestBodyController {

    @PostMapping(path = "/requestBody", produces = "application/json;charset=UTF-8",
            consumes = "application/x-www-form-urlencoded;charset=UTF-8")
    public void handle(@RequestBody User user) {
        System.out.println(user);
        //…………
    }
}
