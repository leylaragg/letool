package com.github.leyland.letool.data.letool.tool.exception;

import com.github.leyland.letool.data.letool.tool.api.IResultCode;
import com.github.leyland.letool.data.letool.tool.api.SystemCode;

/**
 * @ClassName <h2>SystemException</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class SystemException extends RuntimeException {

    private final IResultCode resultCode;

    private boolean enableCustomStackTrace = false;

    public SystemException(String message) {
        super(message);
        this.resultCode = SystemCode.SYSTEM_ERROR;
    }

    public SystemException(IResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public SystemException(IResultCode resultCode, Throwable cause) {
        super(cause);
        this.resultCode = resultCode;
    }

    public SystemException(IResultCode resultCode, Object... args) {
        super(String.format(resultCode.getMessage(), args));
        this.resultCode = resultCode;
    }

    public IResultCode getResultCode() {
        return resultCode;
    }

    public boolean isEnableCustomStackTrace() {
        return enableCustomStackTrace;
    }

    public void setEnableCustomStackTrace(boolean enableCustomStackTrace) {
        this.enableCustomStackTrace = enableCustomStackTrace;
    }

    /**
     * 提高性能
     *
     * @return Throwable
     */
    @Override
    public Throwable fillInStackTrace() {
        if (enableCustomStackTrace) {
            // 执行自定义的堆栈跟踪逻辑
            // 可以在这里添加特定的逻辑来构造自定义的堆栈跟踪信息  -- 先临时这么写吧。
            System.out.println("Custom fillInStackTrace logic executed");
            return this;
        } else {
            // 调用默认的 fillInStackTrace() 方法
            return super.fillInStackTrace();
        }
    }
}
