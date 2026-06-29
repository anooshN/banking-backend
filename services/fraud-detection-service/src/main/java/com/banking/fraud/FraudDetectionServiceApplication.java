package com.banking.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@ComponentScan(basePackages = {
    "com.banking.fraud",
    "com.banking.security",
    "com.banking.common",
    "com.banking.kafka",
    "com.banking.audit",
    "com.banking.exception"
})
public class FraudDetectionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FraudDetectionServiceApplication.class, args);
    }
}
