package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.pojo.User;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @ClassName <h2>HttpEntityController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
public class HttpEntityController {

    @PostMapping("/httpEntity")
    public void handle(HttpEntity<User> httpEntity) {
        //获取请求体
        User body = httpEntity.getBody();
        System.out.println(body);
        //获取请求头
        HttpHeaders headers = httpEntity.getHeaders();
        System.out.println(headers);
        //…………
    }
}
