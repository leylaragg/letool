package com.github.leyland.letool.demo.spring.mvc.convert;

public class MyModel {
    String modelId;

    String modelType;

    String modelName;

    public String getModelId() {
        return modelId;
    }

    public void setModelId(String modelId) {
        this.modelId = modelId;
    }

    public String getModelType() {
        return modelType;
    }

    public void setModelType(String modelType) {
        this.modelType = modelType;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    @Override
    public String toString() {
        return "MyModel{" +
                "modelId='" + modelId + '\'' +
                ", modelType='" + modelType + '\'' +
                ", modelName='" + modelName + '\'' +
                '}';
    }
}
