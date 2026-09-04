package com.zhixing.media;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 媒资服务启动类
 */
@SpringBootApplication
public class MediaApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(MediaApplication.class).run(args);
    }
}
