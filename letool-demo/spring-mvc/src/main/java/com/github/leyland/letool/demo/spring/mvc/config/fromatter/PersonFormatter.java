package com.github.leyland.letool.demo.spring.mvc.config.fromatter;

import com.github.leyland.letool.demo.spring.mvc.pojo.Person;
import org.springframework.format.Formatter;

import java.text.ParseException;
import java.util.Locale;

/**
 * @ClassName <h2>PersonFormatter</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class PersonFormatter implements Formatter<Person> {

    @Override
    public Person parse(String text, Locale locale) throws ParseException {
        String[] split = text.split("\\|");
        if (split.length == 4) {
            return new Person(Long.valueOf(split[0]), split[1], Integer.valueOf(split[2]), split[3]);
        }
        throw new ParseException("参数格式不正确：" + text, 0);
    }

    @Override
    public String print(Person object, Locale locale) {
        return object.toString();
    }
}
