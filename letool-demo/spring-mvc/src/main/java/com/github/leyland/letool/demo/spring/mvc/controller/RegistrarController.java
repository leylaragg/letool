package com.github.leyland.letool.demo.spring.mvc.controller;

import com.github.leyland.letool.demo.spring.mvc.config.conver.DefaultConversionService;
import com.github.leyland.letool.demo.spring.mvc.config.editor.registrar.CustomPropertyEditorRegistrar;
import com.github.leyland.letool.demo.spring.mvc.pojo.MyDate;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.text.DateFormat;

/**
 * @ClassName <h2>RegistrarController</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
@Controller
public class RegistrarController {
    @Resource
    private CustomPropertyEditorRegistrar customPropertyEditorRegistrar;

    /**
     * 这个参数是用来进行参数绑定的，其中通过调用customPropertyEditorRegistrar的registerCustomEditors方法，
     * 向binder中注册了CustomType的属性编辑器CustomTypeEditor。
     * @param binder
     */
    @InitBinder
    public void init(WebDataBinder binder) {
        //调用registerCustomEditors方法向当前DateBinder注册PropertyEditor
        customPropertyEditorRegistrar.registerCustomEditors(binder);
    }

    //其他控制器方法
    @RequestMapping("/dateTimeFormat/{date}")
    @ResponseBody
    public MyDate annotationFormatterFactory(MyDate date) {
        System.out.println(DateFormat.getDateTimeInstance().format(date.getDate()));
        //测试懒加载的转换器
        ConversionService sharedInstance = DefaultConversionService.getSharedInstance();
        return date;
    }
}
