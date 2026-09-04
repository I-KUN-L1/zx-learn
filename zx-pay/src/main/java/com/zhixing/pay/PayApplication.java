package com.zhixing.pay;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 支付服务启动类
 */
@SpringBootApplication
public class PayApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(PayApplication.class).run(args);
    }
}
