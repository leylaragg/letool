package com.github.leyland.letool.data.letool.thread.reference;

/**
 * @ClassName <h2>UnsafeThisEscape</h2>
 * @Description 引用逃逸
 * @Author rungo
 * @Date 4/10/2025
 * @Version 1.0
 **/
public class UnsafeThisEscape {
    private String id;

    public UnsafeThisEscape(String id) {
        new Thread(new EscapeRunnable()).start();
        // 模拟耗时操作，增加子线程先执行的概率
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.id = id;
    }

    private class EscapeRunnable implements Runnable {
        @Override
        public void run() {
            System.out.println("id: "+UnsafeThisEscape.this.id);
            // 在这里通过UnsafeThisEscape.this就可以引用UnsafeThisEscape对象, 但是此时UnsafeThisEscape对象可能还没有构造完成, 即发生了this引用的逃逸.
        }
    }
}
