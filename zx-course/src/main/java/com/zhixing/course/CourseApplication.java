package com.zhixing.course;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * 课程服务启动类
 */
@SpringBootApplication
@MapperScan("com.zhixing.course.mapper")
public class CourseApplication {

    public static void main(String[] args) {
        new SpringApplicationBuilder(CourseApplication.class).run(args);
    }
}
