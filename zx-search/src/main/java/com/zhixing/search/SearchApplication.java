package com.zhixing.search;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 搜索服务启动类
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.zhixing.api.client")
public class SearchApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(SearchApplication.class).run(args);
    }
}
