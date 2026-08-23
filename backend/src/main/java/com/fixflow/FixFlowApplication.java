package com.fixflow;

import com.fixflow.config.FixFlowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableConfigurationProperties(FixFlowProperties.class)
@EnableTransactionManagement
public class FixFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(FixFlowApplication.class, args);
    }
}
