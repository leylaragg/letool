package com.github.leyland.letool.data.exception;

import com.github.leyland.letool.tool.exception.LetoolException;

public class DataException extends LetoolException {

    public DataException(String errorCode, String message) {
        super(errorCode, message);
    }

    public DataException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
