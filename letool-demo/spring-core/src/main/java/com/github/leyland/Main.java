package com.github.leyland;

import java.sql.Timestamp;
import java.util.Date;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");

        // 创建一个java.util.Date对象
        Date utilDate = new Date();

        // 将java.util.Date对象转换为java.sql.Timestamp对象
        Timestamp sqlTimestamp = new Timestamp(utilDate.getTime());

        // 现在你可以使用sqlTimestamp对象
        System.out.println("SQL Timestamp: " + sqlTimestamp);


        try {
            throw new RuntimeException("一场");
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }
}