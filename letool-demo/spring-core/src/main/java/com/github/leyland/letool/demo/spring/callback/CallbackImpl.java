package com.github.leyland.letool.demo.spring.callback;

/**
 * @ClassName <h2>CallbackImpl</h2>
 * @Description TODO 实现回调方法的类
 * @Author Rungo
 * @Version 1.0
 **/
class CallbackImpl implements Callback {
    @Override
    public void onCallback() {
        // 处理回调方法
        System.out.println("Callback method is called.");
    }
}
