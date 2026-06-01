package com.finance.portal.market.application.currency;

import com.finance.portal.market.application.currency.model.HesapkurduFxItem;
import com.finance.portal.market.application.currency.port.BankCurrencyPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankCurrencyServiceTest {

    private static final String CACHE_KEY = "market:currency:banks:all";

    @Mock
    private BankCurrencyPort bankCurrencyPort;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private BankCurrencyService service;

    // ── cache hit ────────────────────────────────────────────────────────────

    @Test
    void getAllBankRates_servesFromRedisCacheWithoutHittingPort() {
        String json = "[{\"bankName\":\"Garanti\",\"currencyCode\":\"USD\",\"buyRate\":40.1,"
                + "\"sellRate\":40.5,\"source\":\"HESAPKURDU\"}]";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(json);

        List<BankCurrencyRateDto> result = service.getAllBankRates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBankName()).isEqualTo("Garanti");
        assertThat(result.get(0).getCurrencyCode()).isEqualTo("USD");
        assertThat(result.get(0).getBuyRate()).isEqualTo(40.1);
        verify(bankCurrencyPort, never()).fetchBankRates();
    }

    // ── cache miss → fetch + map + cache write ───────────────────────────────

    @Test
    void getAllBankRates_cacheMissFetchesMapsAndCaches() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(bankCurrencyPort.fetchBankRates()).thenReturn(List.of(fxItem()));

        List<BankCurrencyRateDto> result = service.getAllBankRates();

        assertThat(result).hasSize(1);
        BankCurrencyRateDto dto = result.get(0);
        assertThat(dto.getBankName()).isEqualTo("Garanti BBVA");
        assertThat(dto.getBankId()).isEqualTo(7);
        assertThat(dto.getCurrencyCode()).isEqualTo("USD");
        assertThat(dto.getCurrencyName()).isEqualTo("ABD Doları");
        assertThat(dto.getBuyRate()).isEqualTo(40.10);
        assertThat(dto.getSellRate()).isEqualTo(40.50);
        assertThat(dto.getLastRate()).isEqualTo(40.30);
        // spread = ask - bid = 40.50 - 40.10
        assertThat(dto.getSpread()).isEqualTo(new BigDecimal("40.50")
                .subtract(new BigDecimal("40.10")).doubleValue());
        assertThat(dto.getDailyChange()).isEqualTo(0.25);
        assertThat(dto.getDailyChangePercent()).isEqualTo(0.62);
        assertThat(dto.getSource()).isEqualTo("HESAPKURDU");
        assertThat(dto.getSourceUpdatedAt()).isNotNull(); // epoch -> ISO instant

        // value written back to Redis cache with a TTL
        verify(valueOps).set(eq(CACHE_KEY), anyString(), any(Duration.class));
    }

    // ── empty upstream handling ──────────────────────────────────────────────

    @Test
    void getAllBankRates_returnsEmptyWhenPortReturnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(bankCurrencyPort.fetchBankRates()).thenReturn(Collections.emptyList());

        List<BankCurrencyRateDto> result = service.getAllBankRates();

        assertThat(result).isEmpty();
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ── exception → graceful fallback ────────────────────────────────────────

    @Test
    void getAllBankRates_returnsEmptyWhenPortThrows() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(bankCurrencyPort.fetchBankRates()).thenThrow(new RuntimeException("hesapkurdu down"));

        assertThat(service.getAllBankRates()).isEmpty();
    }

    @Test
    void getAllBankRates_fallsBackToFetchWhenRedisReadFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenThrow(new RuntimeException("redis read boom"));
        when(bankCurrencyPort.fetchBankRates()).thenReturn(List.of(fxItem()));

        List<BankCurrencyRateDto> result = service.getAllBankRates();

        assertThat(result).hasSize(1);
        verify(bankCurrencyPort).fetchBankRates();
    }

    @Test
    void getAllBankRates_stillReturnsDataWhenRedisWriteFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(bankCurrencyPort.fetchBankRates()).thenReturn(List.of(fxItem()));
        // Redis write blows up — data must still be returned.
        org.mockito.Mockito.doThrow(new RuntimeException("redis write boom"))
                .when(valueOps).set(anyString(), anyString(), any(Duration.class));

        List<BankCurrencyRateDto> result = service.getAllBankRates();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCurrencyCode()).isEqualTo("USD");
    }

    // ── filtering: by currency ───────────────────────────────────────────────

    @Test
    void getBankRatesByCurrency_filtersCaseInsensitively() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(bankCurrencyPort.fetchBankRates())
                .thenReturn(List.of(fxItem(), eurItem()));

        List<BankCurrencyRateDto> usd = service.getBankRatesByCurrency("usd");

        assertThat(usd).hasSize(1);
        assertThat(usd.get(0).getCurrencyCode()).isEqualTo("USD");
    }

    @Test
    void getBankRatesByCurrency_returnsEmptyWhenNoMatch() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(bankCurrencyPort.fetchBankRates()).thenReturn(List.of(fxItem()));

        assertThat(service.getBankRatesByCurrency("GBP")).isEmpty();
    }

    // ── filtering: by bank name (partial, case-insensitive) ──────────────────

    @Test
    void getBankRatesByBankName_filtersByPartialCaseInsensitiveMatch() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(bankCurrencyPort.fetchBankRates())
                .thenReturn(List.of(fxItem(), eurItem()));

        List<BankCurrencyRateDto> result = service.getBankRatesByBankName("garanti");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBankName()).isEqualTo("Garanti BBVA");
    }

    // ── mapping edge: null BigDecimals → null Doubles, spread null ───────────

    @Test
    void mapping_handlesNullNumericFieldsGracefully() {
        HesapkurduFxItem sparse = new HesapkurduFxItem();
        sparse.setExchangeDisplayName("Akbank");
        sparse.setBankId(1);
        sparse.setShortCode("USD");
        sparse.setDisplayName("ABD Doları");
        // all numeric fields left null; dateTime null
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(CACHE_KEY)).thenReturn(null);
        when(bankCurrencyPort.fetchBankRates()).thenReturn(List.of(sparse));

        List<BankCurrencyRateDto> result = service.getAllBankRates();

        assertThat(result).hasSize(1);
        BankCurrencyRateDto dto = result.get(0);
        assertThat(dto.getBuyRate()).isNull();
        assertThat(dto.getSellRate()).isNull();
        assertThat(dto.getLastRate()).isNull();
        assertThat(dto.getSpread()).isNull();
        assertThat(dto.getSourceUpdatedAt()).isNull();
        assertThat(dto.getSource()).isEqualTo("HESAPKURDU");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static HesapkurduFxItem fxItem() {
        HesapkurduFxItem item = new HesapkurduFxItem();
        item.setExchangeDisplayName("Garanti BBVA");
        item.setBankId(7);
        item.setShortCode("USD");
        item.setDisplayName("ABD Doları");
        item.setBid(new BigDecimal("40.10"));
        item.setAsk(new BigDecimal("40.50"));
        item.setLast(new BigDecimal("40.30"));
        item.setDailyChange(new BigDecimal("0.25"));
        item.setDailyChangePercent(new BigDecimal("0.62"));
        item.setDateTime(1_715_000_000_000L);
        return item;
    }

    private static HesapkurduFxItem eurItem() {
        HesapkurduFxItem item = new HesapkurduFxItem();
        item.setExchangeDisplayName("İş Bankası");
        item.setBankId(8);
        item.setShortCode("EUR");
        item.setDisplayName("Euro");
        item.setBid(new BigDecimal("43.10"));
        item.setAsk(new BigDecimal("43.60"));
        item.setLast(new BigDecimal("43.40"));
        return item;
    }
}
