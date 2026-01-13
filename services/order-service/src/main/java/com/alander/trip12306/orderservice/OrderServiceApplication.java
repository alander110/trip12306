package com.alander.trip12306.orderservice;

import cn.crane4j.spring.boot.annotation.EnableCrane4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.alander.trip12306.orderservice.dao.mapper")
@EnableFeignClients("com.alander.trip12306.orderservice.remote")
@EnableCrane4j(enumPackages = "com.alander.trip12306.orderservice.common.enums")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
