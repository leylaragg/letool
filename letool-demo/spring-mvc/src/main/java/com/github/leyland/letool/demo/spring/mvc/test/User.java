package com.github.leyland.letool.demo.spring.mvc.test;

import java.util.ArrayList;
import java.util.List;

/**
 * @ClassName <h2>User</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class User {

    private String name = "leyland";

    List<String> lsitStrs = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getLsitStrs() {
        return lsitStrs;
    }

    public void setLsitStrs(List<String> lsitStrs) {
        this.lsitStrs = lsitStrs;
    }

}
