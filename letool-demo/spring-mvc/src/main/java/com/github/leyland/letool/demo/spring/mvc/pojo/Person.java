package com.github.leyland.letool.demo.spring.mvc.pojo;

import com.github.leyland.letool.demo.spring.mvc.config.fromatter.annotation.SexFormat;

/**
 * @ClassName <h2>Person</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class Person {
    private Long id;
    private String tel;
    private Integer age;

    @SexFormat
    private String sex;

    public Person(Long id, String tel, Integer age, String sex) {
        this.id = id;
        this.tel = tel;
        this.age = age;
        this.sex = sex;
    }
    //省略get/set...

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTel() {
        return tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }
}
