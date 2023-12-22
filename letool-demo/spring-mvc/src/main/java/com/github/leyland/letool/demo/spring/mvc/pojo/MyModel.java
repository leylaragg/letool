package com.github.leyland.letool.demo.spring.mvc.pojo;

/**
 * @ClassName <h2>MyModel</h2>
 * @Description TODO
 * @Author Rungo
 * @Version 1.0
 **/
public class MyModel {
    private Long modelId;
    private Integer modelType;
    private String modelName;

    public void setModelId(Long modelId) {
        this.modelId = modelId;
    }

    public void setModelType(Integer modelType) {
        this.modelType = modelType;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }


    @Override
    public String toString() {
        return "MyModel{" +
                "modelId=" + modelId +
                ", modelType=" + modelType +
                ", modelName='" + modelName + '\'' +
                '}';
    }
}
