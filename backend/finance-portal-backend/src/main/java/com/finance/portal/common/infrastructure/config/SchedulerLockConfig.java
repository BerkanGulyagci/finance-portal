package com.finance.portal.common.infrastructure.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ShedLock — @Scheduled görevleri için dağıtık kilit.
 *
 * <p>Kubernetes'te birden çok pod replica'sı çalışırken her pod'daki
 * Spring scheduler, aynı @Scheduled metodu aynı anda tetiklemeye çalışır.
 * Alarm değerlendirme, newsletter mail gönderimi, cache ısıtma gibi işler
 * buna izin verirse: <b>aynı alarm 3 kez tetiklenir, aynı mail 3 kez gider,
 * aynı API 3 kat boşa çağrılır.</b>
 *
 * <p>ShedLock, Redis üzerinde kilit alır: bir pod metodu kapana çekince
 * diğerleri o tetikleme penceresini atlar. Tek pod (compose) çalıştığında
 * da hatasız çalışır — kilit alınır, görev koşar, bırakılır.
 *
 * <p>Anahtar isim alanı: {@code finance-portal:lock:<metod-adi>}.
 * {@code defaultLockAtMostFor} = pod ölürse kilit en geç bu kadar süre
 * sonra başkalarına geçer (güvenlik supabı).
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory redisConnectionFactory) {
        return new RedisLockProvider(redisConnectionFactory, "finance-portal");
    }
}
