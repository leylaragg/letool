package com.github.leyland.letool.letool.tool.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @ClassName <h2>SpringProperties</h2>
 * @Description TODO 未完善
 * @Author Rungo
 * @Version 1.0
 **/
@ConfigurationProperties("letool.tool.spring")
public class SpringProperties {

    /**
     * 是否给 SpringUtil.applicationContext 注入。
     */
    private Boolean enabled = true;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

}
