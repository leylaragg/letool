package com.github.leyland.letool.demo.spring.callback;

/**
 * @ClassName <h2>Caller</h2>
 * @Description TODO 调用回调方法的类
 * @Author Rungo
 * @Version 1.0
 **/
class Caller {
    void registerCallback(Callback callback) {
        // 模拟事件发生
        try {
            System.out.println("正在做了一些事情，sleep(2000)");
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 当事件发生时调用回调接口方法
        System.out.println("开始回调");
        callback.onCallback();
    }
}
