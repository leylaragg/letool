package io.github.leylaragg.letool.verification;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.leylaragg.letool.print.context.PrintContext;
import io.github.leylaragg.letool.print.service.PrintDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 模拟外部项目接入动态打印 Starter 的最小 Spring Boot 应用。
 *
 * @author leyland
 */
@SpringBootApplication
public class PrintConsumerApplication {

    /**
     * 启动独立消费者，便于发布后按普通应用方式排查接入问题。
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(PrintConsumerApplication.class, args);
    }

    /**
     * 消费者只声明调用编码和数据适配，模板解析与 PDF 渲染仍由 Starter 完成。
     *
     * @return 通用文档打印定义
     */
    @Bean
    PrintDefinition<Long> consumerPrintDefinition() {
        return PrintDefinition.of("consumer", "consumer-template", Long.class,
                documentId -> PrintContext.of(1, JsonNodeFactory.instance.objectNode()
                        .put("name", "document-" + documentId)
                        .put("approved", true)));
    }
}
