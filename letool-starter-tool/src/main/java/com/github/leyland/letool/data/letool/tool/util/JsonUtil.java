package com.github.leyland.letool.data.letool.tool.util;

import java.util.concurrent.*;

/**
 * 对 FastJson 进行二次封装的工具类
 *
 * <h2>JsonUtil</h2>
 *
 * @author: leyland
 * @date: 2023.12
 * @version: 1.0
 **/
public class JsonUtil {

    public static void main(String[] args) throws ExecutionException, InterruptedException {

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println(Thread.currentThread().getName() + "-------------------run------------------------");
            }
        };

        Thread thread = new Thread(runnable, "t1");
        thread.start();

        Callable callable = new Callable() {
            @Override
            public Object call() throws Exception {
                System.out.println(Thread.currentThread().getName() + "-------------------run------------------------");
                return "call callable";
            }
        };

        FutureTask<String> futureTask = new FutureTask(callable);

        Thread thread2 = new Thread(futureTask, "t2");
        thread2.start();
        System.out.println(futureTask.get());   //futureTask.get() 会阻塞后续程序


//        CompletableFuture

    }

}
