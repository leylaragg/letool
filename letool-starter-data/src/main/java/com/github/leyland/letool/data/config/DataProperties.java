package com.github.leyland.letool.data.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "letool.data")
public class DataProperties {

    private boolean enabled = true;
    private Pagination pagination = new Pagination();
    private Mapping mapping = new Mapping();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Pagination getPagination() { return pagination; }
    public void setPagination(Pagination pagination) { this.pagination = pagination; }
    public Mapping getMapping() { return mapping; }
    public void setMapping(Mapping mapping) { this.mapping = mapping; }

    public static class Pagination {
        private int maxPageSize = 1000;
        private int defaultPageSize = 20;

        public int getMaxPageSize() { return maxPageSize; }
        public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }
        public int getDefaultPageSize() { return defaultPageSize; }
        public void setDefaultPageSize(int defaultPageSize) { this.defaultPageSize = defaultPageSize; }
    }

    public static class Mapping {
        private boolean autoCamelCase = true;
        private boolean useGeneratedKeys = true;

        public boolean isAutoCamelCase() { return autoCamelCase; }
        public void setAutoCamelCase(boolean autoCamelCase) { this.autoCamelCase = autoCamelCase; }
        public boolean isUseGeneratedKeys() { return useGeneratedKeys; }
        public void setUseGeneratedKeys(boolean useGeneratedKeys) { this.useGeneratedKeys = useGeneratedKeys; }
    }
}
