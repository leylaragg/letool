package com.github.leyland.letool.tool.exception;

import com.github.leyland.letool.tool.api.IResultCode;
import com.github.leyland.letool.tool.api.SystemResultCode;

/**
 * @ClassName <h2>BusinessException</h2>
 * @Description TODO 业务异常
 * @Author Rungo
 * @Version 1.0
 **/
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 2359767895161832954L;
    private final IResultCode resultCode;

    private boolean enableCustomStackTrace = true;

    public BusinessException(String message) {
        super(message);
        this.resultCode = SystemResultCode.FAILURE;
    }

    public BusinessException(IResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(IResultCode resultCode, Throwable cause) {
        super(cause);
        this.resultCode = resultCode;
    }

    public BusinessException(IResultCode resultCode, Object... args) {
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
