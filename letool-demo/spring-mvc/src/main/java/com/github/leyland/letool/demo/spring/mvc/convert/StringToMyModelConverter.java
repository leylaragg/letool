package com.github.leyland.letool.demo.spring.mvc.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 把字符串转换MyModel
 */
@Component
public class StringToMyModelConverter implements Converter<String, MyModel> {

    /**
     * String source    传入进来字符串
     *
     * @param source 传入的要被转换的字符串
     * @return 转换后的格式类型
     */
    @Override
    public MyModel convert(String source) {
        //jackson jar 类
        ObjectMapper mapper = new ObjectMapper();
        MyModel myModel = null;
        try {
            myModel = mapper.readValue(source, MyModel.class);
            //myModel = JSON.parseObject(source, MyModel.class);
            System.out.println("-----------------");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("转换异常！");
        }
        return myModel;
    }
}
