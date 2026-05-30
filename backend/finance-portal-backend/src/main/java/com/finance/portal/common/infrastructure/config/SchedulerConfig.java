package com.finance.portal.common.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @Scheduled görevleri için thread havuzu yapılandırması.
 *
 * <p><b>Neden:</b> Spring'in varsayılan scheduler'ı TEK thread'lidir; tüm @Scheduled job'lar
 * ({@code alarm kontrolü}, {@code haber cache}, {@code stok/tahvil/fon warm-up}, {@code newsletter})
 * aynı thread'de sıralanır. Uzun süren bir job (ör. tahvil listesi warm-up'ı ~45 sn) diğer
 * zaman-kritik job'ları (alarm 60 sn) o süre boyunca BLOKLAR.
 *
 * <p><b>Çözüm:</b> Birden çok thread'li bir {@link ThreadPoolTaskScheduler} tanımlanır; bağımsız
 * job'lar paralel çalışır, head-of-line blocking ortadan kalkar. (Aynı job'un kendisiyle
 * çakışması @Scheduled + ShedLock tarafından zaten engellenir.)
 */
@Configuration
@EnableScheduling
public class SchedulerConfig implements SchedulingConfigurer {

    @Value("${scheduling.pool-size:5}")
    private int poolSize;

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setTaskScheduler(taskScheduler());
    }
}
