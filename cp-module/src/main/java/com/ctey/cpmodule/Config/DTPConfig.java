package com.ctey.cpmodule.Config;

import org.dromara.dynamictp.core.executor.DtpExecutor;
import org.dromara.dynamictp.core.executor.OrderedDtpExecutor;
import org.dromara.dynamictp.core.executor.ScheduledDtpExecutor;
import org.dromara.dynamictp.core.support.DynamicTp;
import org.dromara.dynamictp.core.support.ThreadPoolBuilder;
import org.dromara.dynamictp.core.support.ThreadPoolCreator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledExecutorService;

// 客户端线程模拟/连接池守护线程的线程池
@Configuration
public class DTPConfig {
    @Bean
    public ScheduledExecutorService cpExecutorRequestTask() {
        return ThreadPoolBuilder.newBuilder()
                .threadPoolName("cpExecutorRequestTask")
                .threadFactory("cp-request-task")
                .corePoolSize(10)
                .maximumPoolSize(20)
                .queueCapacity(1024)
                .keepAliveTime(60)
                .buildScheduled();
    }

    @Bean
    public ScheduledExecutorService cpExecutorExamineTask() {
        return ThreadPoolBuilder.newBuilder()
                .threadPoolName("cpExecutorExamineTask")
                .threadFactory("cp-examine-task")
                .corePoolSize(10)
                .maximumPoolSize(20)
                .queueCapacity(1024)
                .keepAliveTime(60)
                .buildScheduled();
    }


}
