package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.convert.MyModel;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@RestController
public class CookieValueController {

    @GetMapping("/setCookie")
    public void handle(HttpServletResponse resp) throws IOException {
        //生成一个随机字符串
        String uuid = UUID.randomUUID().toString();
        System.out.println("setCookie: " + uuid);
        //创建Cookie对象，指定名字和值。Cookie类只有这一个构造器
        Cookie cookie = new Cookie("uuid", uuid);
        //在响应中添加Cookie对象
        resp.addCookie(cookie);
    }

    @GetMapping("/cookieValue")
    public void handle1(@CookieValue String uuid) {
        System.out.println("cookieValue: " + uuid);
        //…………
    }

    @GetMapping("/modelAttribute/{myModel}")
    public void handle2(@ModelAttribute MyModel myModel) {
        System.out.println(myModel);
    }
}