package com.github.leyland.letool.web.wrapper;

import com.github.leyland.letool.web.xss.XssCleaner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * XSS 请求包装器 —— 对所有请求参数进行 HTML 转义.
 */
public class XssRequestWrapper extends HttpServletRequestWrapper {

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        return XssCleaner.clean(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) return null;
        String[] cleaned = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            cleaned[i] = XssCleaner.clean(values[i]);
        }
        return cleaned;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> map = super.getParameterMap();
        return map.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            String[] vals = e.getValue();
                            String[] cleaned = new String[vals.length];
                            for (int i = 0; i < vals.length; i++) {
                                cleaned[i] = XssCleaner.clean(vals[i]);
                            }
                            return cleaned;
                        }
                ));
    }

    @Override
    public String getHeader(String name) {
        return XssCleaner.clean(super.getHeader(name));
    }
}
