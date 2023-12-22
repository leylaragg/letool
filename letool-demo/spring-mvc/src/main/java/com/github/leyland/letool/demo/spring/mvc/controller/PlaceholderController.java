package com.github.leyland.letool.demo.spring.mvc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @ClassName <h2>PlaceholderController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@RestController
@RequestMapping("/${uri1}")
class PlaceholderController {

    @GetMapping("/${uri2}/{${uri3}}/{id}")
    public void handle(@PathVariable Long id, @PathVariable String xxx) {
        System.out.println(id);
        System.out.println(xxx);
    }
}
