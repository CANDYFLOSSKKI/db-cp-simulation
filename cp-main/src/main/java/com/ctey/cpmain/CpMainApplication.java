package com.ctey.cpmain;

import com.feiniaojin.gracefulresponse.EnableGracefulResponse;
import org.dromara.dynamictp.core.spring.EnableDynamicTp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableDynamicTp
@EnableGracefulResponse
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
@ComponentScan(basePackages = {
        "com.ctey.cpmodule",
        "com.ctey.cpstatic",
        "com.ctey.cpweb"
})
public class CpMainApplication {
    public static void main(String[] args) {
        SpringApplication.run(CpMainApplication.class, args);
    }
}
