package com.github.leyland.letool.demo.spring.mvc.config.fromatter;

import com.github.leyland.letool.demo.spring.mvc.pojo.Person;
import com.github.leyland.letool.demo.spring.mvc.config.fromatter.annotation.PersonFormat;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * @ClassName <h2>PersonAnnotationFormatterFactory</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Component
public class PersonAnnotationFormatterFactory implements AnnotationFormatterFactory<PersonFormat> {

    Set<Class<?>> classSet = Collections.singleton(Person.class);

    @Override
    public Set<Class<?>> getFieldTypes() {
        return classSet;
    }

    @Override
    public Parser<?> getParser(PersonFormat annotation, Class<?> fieldType) {
        return configureFormatterFrom(annotation);
    }

    @Override
    public Printer<?> getPrinter(PersonFormat annotation, Class<?> fieldType) {
        return configureFormatterFrom(annotation);
    }

    private Formatter<Person> configureFormatterFrom(PersonFormat annotation) {
        return new PersonFormatter();
    }
}
