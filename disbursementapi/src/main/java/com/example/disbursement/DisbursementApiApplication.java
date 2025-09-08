package com.example.disbursement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.example.disbursement", "com.example.common"})
public class DisbursementApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(DisbursementApiApplication.class, args);
    }
}
