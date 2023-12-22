package com.github.leyland.letool.demo.spring.source;

/**
 * @ClassName <h2>FirstSpringSource</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class FirstSpringSource {
    public FirstSpringSource() {
        System.out.println("FirstSpringSource init");
    }

    public void methodCall(){
        System.out.println("methodCall");
    }

    public String str;

    public void setStr(String str) {
        this.str = str;
    }

    @Override
    public String toString() {
        return "FirstSpringSource{" +
                "str='" + str + '\'' +
                '}';
    }
}
