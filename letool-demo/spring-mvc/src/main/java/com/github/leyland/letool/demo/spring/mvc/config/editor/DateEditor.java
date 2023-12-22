package com.github.leyland.letool.demo.spring.mvc.config.editor;

import java.beans.PropertyEditorSupport;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @ClassName <h2>DateEditor</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class DateEditor extends PropertyEditorSupport {
    private String formatter = "yyyy-MM-dd";

    @Override
    public void setAsText(String text) throws IllegalArgumentException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat(formatter);
        try {
            Date date = simpleDateFormat.parse(text);
            System.out.println("-----DateEditor-----");
            //转换后的值设置给PropertyEditorSupport内部的value属性
            setValue(date);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public DateEditor() {
    }

    public DateEditor(String formatter) {
        this.formatter = formatter;
    }
}
