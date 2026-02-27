package com.degel.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.degel.admin.mapper")
public class DegelAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(DegelAdminApplication.class, args);
    }
}
