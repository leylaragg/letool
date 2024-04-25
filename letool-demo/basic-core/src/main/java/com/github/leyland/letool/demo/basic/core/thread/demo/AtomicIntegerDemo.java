package com.github.leyland.letool.demo.basic.core.thread.demo;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @ClassName <h2>AtomicIntegerDemo</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class AtomicIntegerDemo {

    public static final int SIZE = 50;

    public static void main(String[] args) throws InterruptedException {

        MyNumber myNumber = new MyNumber();
        CountDownLatch count = new CountDownLatch(SIZE);

        for (int i = 0; i < SIZE; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < 1000; j++) {
                        myNumber.addPlusPlus();
                    }
                } finally {
                    count.countDown();
                }
            }, String.valueOf(i)).start();
        }

        count.await();

        System.out.println(myNumber.atomicInteger.get());
    }

    static class MyNumber {
        AtomicInteger atomicInteger = new AtomicInteger();

        public void addPlusPlus() {
            atomicInteger.getAndIncrement();
        }
    }
}



