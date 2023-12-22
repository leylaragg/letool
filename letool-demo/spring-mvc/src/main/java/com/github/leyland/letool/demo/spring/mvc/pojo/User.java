package com.github.leyland.letool.demo.spring.mvc.pojo;

import org.springframework.util.PropertyPlaceholderHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName <h2>User</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class User {

    private int age;
    private String name;

    public User() {
    }
    public User(int age, String name) {
        this.age = age;
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "User{" +
                "age=" + age +
                ", name='" + name + '\'' +
                '}';
    }

    public static void main(String[] args) {
        User [] users = new User[]{
                new User(111, "asdas"),
                new User(222, "asdas")
        };
        for (User user : users) {
            System.out.println(user);
        }

        String value = "Hello ${name}! Today is ${day}";
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", "Alice");
        placeholders.put("day", "Monday");

        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("${", "}");

        PropertyPlaceholderHelper.PlaceholderResolver placeholderResolver = new PropertyPlaceholderHelper.PlaceholderResolver() {
            @Override
            public String resolvePlaceholder(String placeholderName) {
                return placeholders.getOrDefault(placeholderName, ""); // 返回对应的值，如果不存在则返回空字符串
            }
        };


        String result = helper.replacePlaceholders(value, placeholderResolver);

        System.out.println(result); // 输出：Hello Alice! Today is Monday


    }
}