package com.finance.portal.common.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * "Son başarılı değer" (last-known-good, LKG) dayanıklılık deseni — <b>stale-if-error</b>.
 *
 * <p>Dış kaynak (scrape/API) çekimini sarmalar: çekim BAŞARILI + kullanılabilir sonuç verirse
 * değeri ayrı bir {@code lkg:} anahtarına (uzun TTL) yazar ve döner. Çekim HATA verir ya da BOŞ
 * dönerse, son başarılı LKG değerini döner → kaynak çökse bile kullanıcı boş/hata değil
 * "biraz eski" veri görür. LKG yoksa orijinal davranışa (hata fırlat / boş dön) düşülür.</p>
 *
 * <p>Cache katmanının ALTINDA (fetch sınırında) çalışır → hem {@code @Cacheable} hem manuel
 * cache'leri (portföy/banka) kapsar ve kritik cache altyapısına dokunmaz. Mevcut TTL/negative-cache
 * davranışı değişmez; LKG yalnız hata/boş anında devreye girer.</p>
 *
 * <p>NOT: Yalnız <b>idempotent okuma</b> çekimlerinde kullanılmalı (fiyat/liste/seri gibi). Yan
 * etkili işlemlerde değil.</p>
 */
@Component
public class LastKnownGoodCache {

    private static final Logger log = LoggerFactory.getLogger(LastKnownGoodCache.class);
    private static final String LKG_PREFIX = "lkg:";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public LastKnownGoodCache(StringRedisTemplate redis, ObjectMapper mapper) {
        this.redis = redis;
        this.mapper = mapper;
    }

    /**
     * Çekimi LKG fallback ile sarmalar.
     *
     * @param key     LKG anahtarı (ör. {@code "fx.tcmb.latest"}); {@code lkg:} öneki otomatik eklenir
     * @param ttl     LKG ömrü (kaynak çöktüğünde en fazla bu kadar eski veri gösterilir)
     * @param typeRef sonucun Jackson tip referansı (deserialize için)
     * @param fetch   asıl dış çekim
     * @return taze değer; ya da çekim hata/boş ise son başarılı LKG; ya da LKG yoksa orijinal sonuç/hata
     */
    public <T> T resilient(String key, Duration ttl, TypeReference<T> typeRef, Supplier<T> fetch) {
        try {
            T value = fetch.get();
            if (isUsable(value)) {
                store(key, value, ttl);
                return value;
            }
            // Boş/kullanılamaz sonuç → son başarılıyı dene; yoksa boş sonucu olduğu gibi dön.
            Optional<T> lkg = read(key, typeRef);
            if (lkg.isPresent()) {
                log.warn("[LKG] {} bos dondu → son basarili deger servis edildi", key);
                return lkg.get();
            }
            return value;
        } catch (RuntimeException e) {
            Optional<T> lkg = read(key, typeRef);
            if (lkg.isPresent()) {
                log.warn("[LKG] {} kaynak HATASI ({}) → son basarili deger servis edildi",
                        key, e.getMessage());
                return lkg.get();
            }
            throw e; // LKG yok → orijinal hatayı yükselt (çağıran kendi degrade'ini uygular)
        }
    }

    /** Sonuç "kullanılabilir" mi: null / boş koleksiyon / boş map / boş Optional → değil. */
    private boolean isUsable(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Optional<?> o) {
            return o.isPresent() && isUsable(o.get());
        }
        if (v instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (v instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        return true;
    }

    private void store(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(LKG_PREFIX + key, mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            // LKG yazımı best-effort: başarısız olsa bile asıl akış bozulmaz.
            log.debug("[LKG] {} yazilamadi: {}", key, e.getMessage());
        }
    }

    private <T> Optional<T> read(String key, TypeReference<T> typeRef) {
        try {
            String raw = redis.opsForValue().get(LKG_PREFIX + key);
            if (raw == null || raw.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(mapper.readValue(raw, typeRef));
        } catch (Exception e) {
            log.debug("[LKG] {} okunamadi: {}", key, e.getMessage());
            return Optional.empty();
        }
    }
}
