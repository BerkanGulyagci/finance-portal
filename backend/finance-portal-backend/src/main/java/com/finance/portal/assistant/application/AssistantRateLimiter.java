package com.finance.portal.assistant.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Warren AI kullanım limiti (Redis sayaçları):
 *  - Giriş yapmayan (IP başına): toplam {@code anonMessageLimit} mesaj (24 saatlik pencere) → sonra giriş gerekir.
 *  - Giriş yapan (kullanıcı başına): günlük {@code userDailyLimit} mesaj → ücretsiz kota kötüye kullanımını sınırlar.
 * Redis erişilemezse limit uygulanmaz (asistan çalışmaya devam eder; "fail-open").
 */
@Component
public class AssistantRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(AssistantRateLimiter.class);
    private static final Duration ANON_WINDOW = Duration.ofHours(24);
    private static final Duration USER_WINDOW = Duration.ofHours(26); // gün sonuna kadar yeterli pay

    private final StringRedisTemplate redis;
    private final AssistantProperties props;

    public AssistantRateLimiter(StringRedisTemplate redis, AssistantProperties props) {
        this.redis = redis;
        this.props = props;
    }

    public enum Decision { ALLOWED, LOGIN_REQUIRED, RATE_LIMITED }

    /**
     * Bir mesaj denemesini kaydeder ve karara döner. Sayaç çağrı anında artar (atomik INCR);
     * limit aşılırsa Groq çağrılmaz.
     *
     * @param userId giriş yapan kullanıcı subject'i (null ise anonim)
     * @param clientIp anonim kullanıcı için IP
     */
    public Decision register(String userId, String clientIp) {
        try {
            if (userId != null && !userId.isBlank()) {
                String key = "assistant:user:" + userId + ":" + LocalDate.now();
                long count = incr(key, USER_WINDOW);
                return count > props.getUserDailyLimit() ? Decision.RATE_LIMITED : Decision.ALLOWED;
            }
            String ip = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp;
            String key = "assistant:anon:" + ip;
            long count = incr(key, ANON_WINDOW);
            return count > props.getAnonMessageLimit() ? Decision.LOGIN_REQUIRED : Decision.ALLOWED;
        } catch (Exception e) {
            // Redis yoksa/hatalıysa engelleme (fail-open) — asistan kullanılabilir kalsın.
            log.debug("Assistant rate limit check skipped: {}", e.getMessage());
            return Decision.ALLOWED;
        }
    }

    private long incr(String key, Duration ttl) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, ttl);
        }
        return count == null ? 1L : count;
    }
}
