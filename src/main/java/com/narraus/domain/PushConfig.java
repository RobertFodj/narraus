package com.narraus.domain;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PushConfig {

    /** Oletuksena jokainen lähetys omassa virtuaalisäikeessään. */
    @Bean(destroyMethod = "close")
    ExecutorService pushExecutorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    PushDispatcher pushDispatcher(ExecutorService pushExecutorService) {
        return pushExecutorService::execute;
    }
}
