package com.github.leyland.letool.demo.spring.mvc.pojo;

import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * @ClassName <h2>MyDate</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class MyDate {
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date date;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }
}
