package com.github.leyland.letool.demo.spring.mvc.config.editor.registrar;

import com.github.leyland.letool.demo.spring.mvc.config.editor.DateEditor;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;

import java.util.Date;

/**
 * @ClassName <h2>CustomPropertyEditorRegistrar</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public final class CustomPropertyEditorRegistrar implements PropertyEditorRegistrar {

    private String formatter;


    /**
     * 传递一个PropertyEditorRegistry的实现，使用给定的PropertyEditorRegistry注册自定义PropertyEditor
     * BeanWrapperImpl和DataBinder都实现了PropertyEditorRegistry接口，传递的通常是 BeanWrapper 或 DataBinder。
     * <p>
     * 该方法仅仅是定义了注册的流程，只有当某个BeanWrapper 或 DataBinder实际调用时才会真正的注册
     *
     * @param registry 将要注册自定义PropertyEditor的PropertyEditorRegistry
     */
    //@Override
    public void registerCustomEditors(PropertyEditorRegistry registry) {

        // 预期将创建新的属性编辑器实例，可以自己控制创建流程
        registry.registerCustomEditor(Date.class, new DateEditor(formatter));

        // 可以在此处注册尽可能多的自定义属性编辑器...
    }


    public String getFormatter() {
        return formatter;
    }

    public void setFormatter(String formatter) {
        this.formatter = formatter;
    }
}
