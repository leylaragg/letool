package com.github.leyland.letool.demo.spring.mvc.config.fromatter;

import org.springframework.format.Formatter;

import java.util.Locale;


/**
 * @ClassName <h2>SexFormatter</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class SexFormatter implements Formatter<String> {
    private static final String MAN = "男";
    private static final String WOMAN = "女";
    private static final String OTHER = "未知";


    @Override
    public String parse(String text, Locale locale) {
        if ("0".equals(text)) {
            return MAN;
        }
        if ("1".equals(text)) {
            return WOMAN;
        }
        return OTHER;
    }

    @Override
    public String print(String object, Locale locale) {
        return object;
    }


    public static class WomanFormatter extends SexFormatter {

        @Override
        public String parse(String text, Locale locale) {
            return WOMAN;
        }
    }

    public static class ManFormatter extends SexFormatter {

        @Override
        public String parse(String text, Locale locale) {
            return MAN;
        }
    }
}
