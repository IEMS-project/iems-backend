package com.iems.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class UserServiceApplication {

    public static void main(String[] args) {
        System.out.println("Default JVM TimeZone: " + java.time.ZoneId.systemDefault());
        SpringApplication.run(UserServiceApplication.class, args);
    }

}
