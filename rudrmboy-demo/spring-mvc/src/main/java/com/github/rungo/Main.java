package com.github.rungo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rungo.rudrmboy.demo.spring.mvc.convert.MyModel;
import org.testng.annotations.Test;

import java.io.IOException;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
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
    }

    @Test
    public void test1(){

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

}