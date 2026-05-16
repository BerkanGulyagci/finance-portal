package com.finance.portal.market.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EVDS tahvil listesi toplu çekiminde paralel HTTP (sınırlı eşzamanlılık).
 */
@Configuration
public class EvdsBondFetchConfig {

    @Bean(name = "evdsBondFetchExecutor", destroyMethod = "shutdown")
    public ExecutorService evdsBondFetchExecutor(
            @Value("${evds.bond-fetch-parallelism:8}") int parallelism) {
        int threads = Math.max(2, Math.min(parallelism, 32));
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "evds-bond-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(threads, tf);
    }
}
