package com.github.rungo;

import org.junit.jupiter.api.Test;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");

        //ex1();
        try {
            ex1();
            System.out.println("111111111");
        }catch (Exception e){
            e.printStackTrace();
            System.err.println(e.getMessage());
        }
    }

    public static void ex1() {
        throw new RuntimeException("ex1() 运行时异常");
    }
}