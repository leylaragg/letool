package com.github.leyland.letool.data.mapper;

public class DefaultRowMapper<T> extends BeanPropertyRowMapper<T> {

    public DefaultRowMapper(Class<T> mappedClass) {
        super(mappedClass, true);
    }
}
