package com.github.rungo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rungo.rudrmboy.demo.spring.mvc.convert.MyModel;

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
}