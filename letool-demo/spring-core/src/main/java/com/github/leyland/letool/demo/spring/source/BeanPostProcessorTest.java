package com.github.leyland.letool.demo.spring.source;

/**
 * @ClassName <h2>BeanPostProcessorTest</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class BeanPostProcessorTest {
    private String string;

    public BeanPostProcessorTest() {
        System.out.println("无参构造器调用");
    }

    public BeanPostProcessorTest(String string) {
        System.out.println("带参构造器调用");
        this.string = string;
    }

    public void setString(String string) {
        System.out.println("setter调用");
        this.string = string;
    }

    public void init(){
        System.out.println("+++++++++++++++ init-method +++++++++++++++++++");
    }

    @Override
    public String toString() {
        return "BeanPostProcessorTest{" +
                "string='" + string + '\'' +
                '}';
    }
}
