package com.github.leyland;

import java.util.Arrays;

public class Main {
    public static void main(String[] args) {
        System.out.println("Hello world!");

        // 定义长度为 5 的数组
        int scores[] = new int[]{57, 81, 68, 75, 91};
        // 输出原数组
        System.out.println("原数组内容如下：");
        // 循环遍历原数组
        for(int i=0; i < scores.length; i++) {
            // 将数组元素输出
            System.out.print(scores[i] + "\t");
        }
        // 定义一个新的数组，将 scores 数组中的 5 个元素复制过来
        // 同时留 3 个内存空间供以后开发使用
        int[] newScores = (int[]) Arrays.copyOf(scores, 8);
        newScores[0] = 60;
        System.out.println("\n复制的新数组内容如下：");
        // 循环遍历复制后的新数组
        for(int j = 0; j < newScores.length; j++) {
            // 将新数组的元素输出
            System.out.print(newScores[j] + "\t");
        }
    }
}