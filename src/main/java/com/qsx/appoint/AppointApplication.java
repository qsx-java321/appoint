package com.qsx.appoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 医院预约项目的启动入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AppointApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointApplication.class, args);
    }
}
