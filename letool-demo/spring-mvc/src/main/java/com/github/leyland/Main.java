package com.github.leyland;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.leyland.letool.demo.spring.mvc.config.exception.IONoMoneyException;
import com.github.leyland.letool.demo.spring.mvc.convert.MyModel;
import com.github.leyland.letool.demo.spring.mvc.pojo.Child;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.testng.annotations.Test;

import java.io.IOException;

import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

public class Main {
    public static void main(String[] args) throws IONoMoneyException {
        System.out.println("Hello world!");


        System.out.println(Arrays.asList("NWXA,NWXS".split(",")).contains("NWXS"));

        System.out.println(System.currentTimeMillis());


        ObjectMapper mapper = new ObjectMapper();
        MyModel myModel = null;
        try {
            myModel = mapper.readValue("{\"modelId\":\"9999\",\"modelType\":\"999\",\"modelName\":\"666\"}", MyModel.class);
            System.out.println("-----------------");
            System.out.println(myModel);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("转换异常！");
        }


        ClassPathXmlApplicationContext application = new ClassPathXmlApplicationContext("spring-config.xml");

    }

    @Test
    public void test1() throws IONoMoneyException {


        throw new IONoMoneyException("KFC Crazy Thursday need 50RMB");
    }

    public class MyClassLoader extends ClassLoader {

        @Override
        public synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // 判断需要特殊处理的类名
            if (name.startsWith("com.example.foo") || name.startsWith("com.example.bar")) {
                // 不加载指定类，直接返回空，即控制该类不被加载
                return null;
            }
            return super.loadClass(name, resolve);
        }
    }


    @Test
    public void firstSpringSource() {//初始化容器
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-config-${dev}.xml");
    }


    @Test
    public void test2(){
        //系统环境属性
        Map<String, String> map = System.getenv();
        System.out.println(map);
        //JVM系统属性
        Properties properties = System.getProperties();
        System.out.println(properties);
    }


    @Test
    public void test3(){
        Child child = new Child();
    }


    @Test
    public void setProp() {
        System.setProperty("x","config");
        //初始化容器
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-${x}.xml");
    }


    @Test
    public void circularPlaceholderReference() {
        //config key 对的value为${config}，在解析value时又会找到config key，造成循环变量引用…………
        System.setProperty("config", "${config}");
        //初始化容器
        ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("spring-${config}.xml");
    }


    @Test
    public void ExceTest(){


        try {
            try {
                try {
                    System.out.println("这个产生的异常");
                    throw new RuntimeException("3异常");
                } catch (Exception e) {
                    //这里会输出《"3异常"》的方法调用栈
                    e.printStackTrace();
                    throw new RuntimeException("2异常");
                }
            } catch (Exception e) {
                //异常被重新定义了
                throw new RuntimeException("1异常");
            }
        } catch (Exception e) {
            //只会输出最后定义异常的方法调用栈信息，即定位到《"1异常"》这个代码处
            e.printStackTrace();
        } finally {

        }
    }

}