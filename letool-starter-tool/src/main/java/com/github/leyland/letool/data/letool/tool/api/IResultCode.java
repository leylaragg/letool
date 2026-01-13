package com.github.leyland.letool.data.letool.tool.api;

import java.io.Serializable;

/**
 * @ClassName <h2>IResultCode</h2>
 * @Description TODO 业务代码接口
 * @Author Rungo
 * @Version 1.0
 **/
public interface IResultCode extends Serializable {
    /**
     * 获取消息
     *
     * @return message
     */
    String getMessage();

    /**
     * 获取状态码
     *
     * @return code
     */
    int getCode();

}
