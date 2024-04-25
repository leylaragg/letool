package com.github.leyland.letool.demo.basic.core.thread.demo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @ClassName <h2>CompletableFutureDemo</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class CompletableFutureDemo {

    public static final int SIZE = 50;

    public static void main(String[] args) throws Exception {
        MyNumber myNumber = new MyNumber();

        CompletableFuture<Void>[] futures = new CompletableFuture[SIZE];

        for (int i = 0; i < SIZE; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < 1000; j++) {
                    myNumber.addPlusPlus();
                }
            });
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures);
        allOf.get(); // Ensure all tasks are completed

        System.out.println(myNumber.getResult());
    }

    static class MyNumber {
        private int result = 0;
        private final Object lock = new Object();

        public void addPlusPlus() {
            synchronized (lock) {
                result++;
            }
        }

        public int getResult() {
            //synchronized (lock) {
                return result;
            //}
        }
    }
}
