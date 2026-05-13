package com.finsight;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FinSightApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinSightApplication.class, args);
    }
}
