package com.degel.app;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * degel-app C端 BFF 微服务启动类
 * 端口：9205 | 数据库：degel_app
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.degel.app.mapper")
public class DegelAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(DegelAppApplication.class, args);
    }
}
