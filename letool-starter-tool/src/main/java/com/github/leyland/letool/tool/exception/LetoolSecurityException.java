package com.github.leyland.letool.tool.exception;

import com.github.leyland.letool.tool.api.IResultCode;
import com.github.leyland.letool.tool.api.SystemResultCode;

/**
 * @ClassName <h2>LetoolSecurityException</h2>
 * @Description TODO 权限异常类
 * @Author Rungo
 * @Version 1.0
 **/
public class LetoolSecurityException extends RuntimeException {

    private static final long serialVersionUID = 2359767895161832954L;

    private final IResultCode resultCode;

    private boolean enableCustomStackTrace = true;

    public LetoolSecurityException(String message) {
        super(message);
        this.resultCode = SystemResultCode.UN_AUTHORIZED;
    }

    public LetoolSecurityException(IResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public LetoolSecurityException(IResultCode resultCode, Throwable cause) {
        super(cause);
        this.resultCode = resultCode;
    }

    public LetoolSecurityException(IResultCode resultCode, Object... args) {
        super(String.format(resultCode.getMessage(), args));
        this.resultCode = resultCode;
    }

    public boolean isEnableCustomStackTrace() {
        return enableCustomStackTrace;
    }

    public void setEnableCustomStackTrace(boolean enableCustomStackTrace) {
        this.enableCustomStackTrace = enableCustomStackTrace;
    }

    public IResultCode getResultCode() {
        return resultCode;
    }

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
