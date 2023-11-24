package com.github.rungo.rudrmboy.demo.spring.mvc.controller;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @PostMapping(path = "/requestBody")
    public void handle(@RequestBody User user) {
        System.out.println(user);
        //…………
    }

    @PostMapping(path = "/requestBody2")
    public void handle(@RequestBody String body) {
        System.out.println(body);
        //…………
    }

    @PostMapping(path = "/requestBody3")
    public void handle2(@RequestBody String body) {
        System.out.println(body);
        //…………
    }

    public static void main(String[] args) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        Thread.sleep(2000);
        long time = System.currentTimeMillis() - startTime;
        System.out.println(time);
    }
}
