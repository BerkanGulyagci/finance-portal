package com.finance.portal.market.application.currency;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finance.portal.market.application.currency.model.HesapkurduFxItem;
import com.finance.portal.market.application.currency.port.BankCurrencyPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Banka döviz kurları servis katmanı.
 *
 * Cache stratejisi: RedisTemplate<String, String> ile manuel JSON cache.
 * - @Cacheable / GenericJackson2JsonRedisSerializer kullanılmaz.
 * - Redis'e saf JSON String yazılır, ObjectMapper ile deserialize edilir.
 * - TTL: 2 dakika (market:currency:banks:all key'i)
 * - Hata durumunda boş liste döner (stale cache yoksa).
 */
@Service
public class BankCurrencyService {

    private static final Logger log = LoggerFactory.getLogger(BankCurrencyService.class);

    private static final String CACHE_KEY_ALL = "market:currency:banks:all";
    private static final Duration CACHE_TTL   = Duration.ofMinutes(2);
    private static final String SOURCE        = "HESAPKURDU";

    private static final TypeReference<List<BankCurrencyRateDto>> LIST_TYPE =
            new TypeReference<>() {};

    private final BankCurrencyPort bankCurrencyPort;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public BankCurrencyService(BankCurrencyPort bankCurrencyPort,
                               RedisTemplate<String, String> redisTemplate) {
        this.bankCurrencyPort = bankCurrencyPort;
        this.redisTemplate = redisTemplate;
        // Kendi ObjectMapper — JavaTimeModule ekli, type info yok
        this.objectMapper  = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Tüm banka döviz kurlarını döndürür.
     * Önce Redis'ten okur; cache miss ise Hesapkurdu'dan çekip yazar.
     */
    public List<BankCurrencyRateDto> getAllBankRates() {
        // 1. Redis'ten oku
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY_ALL);
            if (cached != null && !cached.isBlank()) {
                List<BankCurrencyRateDto> result = objectMapper.readValue(cached, LIST_TYPE);
                log.debug("Bank rates served from Redis cache ({} records)", result.size());
                return result;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for key {}: {} — fetching fresh", CACHE_KEY_ALL, e.getMessage());
        }

        // 2. Cache miss — Hesapkurdu'dan çek
        return fetchAndCache();
    }

    /**
     * Belirli bir döviz koduna göre filtreler (örn. "USD", "EUR").
     */
    public List<BankCurrencyRateDto> getBankRatesByCurrency(String currencyCode) {
        String upper = currencyCode.toUpperCase(Locale.ROOT);
        return getAllBankRates().stream()
                .filter(dto -> upper.equals(dto.getCurrencyCode()))
                .toList();
    }

    /**
     * Belirli bir banka adına göre filtreler (case-insensitive, kısmi eşleşme).
     */
    public List<BankCurrencyRateDto> getBankRatesByBankName(String bankName) {
        String lower = bankName.toLowerCase(Locale.ROOT);
        return getAllBankRates().stream()
                .filter(dto -> dto.getBankName() != null
                        && dto.getBankName().toLowerCase(Locale.ROOT).contains(lower))
                .toList();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private List<BankCurrencyRateDto> fetchAndCache() {
        log.info("Fetching bank currency rates from Hesapkurdu");
        try {
            List<HesapkurduFxItem> items = bankCurrencyPort.fetchBankRates();
            if (items.isEmpty()) {
                log.warn("No bank rates returned from Hesapkurdu");
                return Collections.emptyList();
            }

            List<BankCurrencyRateDto> rates = items.stream().map(this::toDto).toList();

            // Redis'e JSON String olarak yaz
            try {
                String json = objectMapper.writeValueAsString(rates);
                redisTemplate.opsForValue().set(CACHE_KEY_ALL, json, CACHE_TTL);
                log.info("Bank rates cached to Redis: {} records, TTL={}m", rates.size(), CACHE_TTL.toMinutes());
            } catch (Exception e) {
                log.warn("Redis write failed for key {}: {}", CACHE_KEY_ALL, e.getMessage());
                // Cache yazma hatası kritik değil — veriyi yine de döndür
            }

            return rates;

        } catch (Exception e) {
            log.error("Failed to fetch bank rates from Hesapkurdu: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    private BankCurrencyRateDto toDto(HesapkurduFxItem item) {
        BankCurrencyRateDto dto = new BankCurrencyRateDto();

        dto.setBankName(item.getExchangeDisplayName());
        dto.setBankId(item.getBankId());
        dto.setCurrencyCode(item.getShortCode());
        dto.setCurrencyName(item.getDisplayName());
        dto.setBuyRate(toDouble(item.getBid()));
        dto.setSellRate(toDouble(item.getAsk()));
        dto.setLastRate(toDouble(item.getLast()));
        dto.setSpread(calculateSpread(item.getAsk(), item.getBid()));
        dto.setDailyChange(toDouble(item.getDailyChange()));
        dto.setDailyChangePercent(toDouble(item.getDailyChangePercent()));
        dto.setSourceUpdatedAt(parseDateTime(item.getDateTime()));
        dto.setSource(SOURCE);

        return dto;
    }

    private Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private Double calculateSpread(BigDecimal ask, BigDecimal bid) {
        if (ask == null || bid == null) return null;
        return ask.subtract(bid).doubleValue();
    }

    private String parseDateTime(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) return null;
        try {
            return Instant.ofEpochMilli(epochMillis).toString();
        } catch (Exception e) {
            log.debug("Failed to parse dateTime: {}", epochMillis);
            return null;
        }
    }
}
