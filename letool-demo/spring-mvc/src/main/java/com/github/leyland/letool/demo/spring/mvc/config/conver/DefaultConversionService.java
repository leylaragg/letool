package com.github.leyland.letool.demo.spring.mvc.config.conver;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.lang.Nullable;

/**
 * @ClassName <h2>DefaultConversionService</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class DefaultConversionService extends GenericConversionService {

    @Nullable
    private static volatile DefaultConversionService sharedInstance;

    public static ConversionService getSharedInstance() {
        DefaultConversionService cs = sharedInstance;
        if (cs == null) {
            synchronized (DefaultConversionService.class) {
                cs = sharedInstance;
                if (cs == null) {
                    cs = new DefaultConversionService();
                    sharedInstance = cs;
                }
            }
        }
        return cs;
    }

    //…………
}
