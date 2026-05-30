package com.finance.portal.market.application.funds.service;

import com.finance.portal.market.application.funds.model.RasyonetFundDetailDto;
import com.finance.portal.market.application.funds.model.RasyonetFundDto;
import com.finance.portal.market.application.funds.model.RasyonetOsmanliFundBulletinDto;
import com.finance.portal.market.application.funds.port.RasyonetFundPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rasyonet/YatırımDirekt fon servisi.
 *
 * Desteklenen kategoriler:
 *   TMF → Normal yatırım fonları (TEFAS)
 *   TPF → BES (Bireysel Emeklilik) fonları
 *   TAF → OKS (Otomatik Katılım) fonları
 *   Osmanlı → Osmanlı Portföy fon bülteni
 */
@Service
public class RasyonetFundService {

    private static final Logger log = LoggerFactory.getLogger(RasyonetFundService.class);

    private final RasyonetFundPort rasyonetFundPort;

    public RasyonetFundService(RasyonetFundPort rasyonetFundPort) {
        this.rasyonetFundPort = rasyonetFundPort;
    }

    // ── TMF — Normal yatırım fonları (TEFAS) ─────────────────────────────────

    @Cacheable(cacheNames = "market.tefas.funds", key = "'rasyonet:funds:TMF'")
    public List<RasyonetFundDto> getAllFunds() {
        log.info("Fetching TMF funds from Rasyonet (cache miss)");
        List<RasyonetFundDto> funds = rasyonetFundPort.fetchFundsBySourceCode("TMF");
        log.info("TMF funds: {}", funds.size());
        return funds;
    }

    // ── TPF — BES fonları ─────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.tefas.funds", key = "'rasyonet:funds:TPF'")
    public List<RasyonetFundDto> getAllBesFunds() {
        log.info("Fetching TPF (BES) funds from Rasyonet (cache miss)");
        List<RasyonetFundDto> funds = rasyonetFundPort.fetchFundsBySourceCode("TPF");
        log.info("TPF (BES) funds: {}", funds.size());
        return funds;
    }

    // ── TAF — OKS fonları ─────────────────────────────────────────────────────

    @Cacheable(cacheNames = "market.tefas.funds", key = "'rasyonet:funds:TAF'")
    public List<RasyonetFundDto> getAllOksFunds() {
        log.info("Fetching TAF (OKS) funds from Rasyonet (cache miss)");
        List<RasyonetFundDto> funds = rasyonetFundPort.fetchFundsBySourceCode("TAF");
        log.info("TAF (OKS) funds: {}", funds.size());
        return funds;
    }

    // ── Osmanlı Portföy fon bülteni ───────────────────────────────────────────

    @Cacheable(cacheNames = "market.tefas.funds", key = "'rasyonet:osmanli:bulletin'")
    public List<RasyonetOsmanliFundBulletinDto> getOsmanliFundBulletin() {
        log.info("Fetching Osmanlı fund bulletin from Rasyonet (cache miss)");
        List<RasyonetOsmanliFundBulletinDto> funds = rasyonetFundPort.fetchOsmanliFundBulletin();
        log.info("Osmanlı bulletin: {} funds", funds.size());
        return funds;
    }

    // ── Warm-up refresh'leri (cache'i proaktif sıcak tutar) ────────────────────
    // @CachePut: her çağrıda Rasyonet'ten taze çekip ilgili liste key'ini atomik günceller
    // (evict→reload boşluğu yok). Scheduler + açılışta çağrılır; kullanıcı soğuk yola düşmez.

    @CachePut(cacheNames = "market.tefas.funds", key = "'rasyonet:funds:TMF'")
    public List<RasyonetFundDto> refreshAllFunds() {
        log.info("Refreshing TMF funds (warm-up)");
        return rasyonetFundPort.fetchFundsBySourceCode("TMF");
    }

    @CachePut(cacheNames = "market.tefas.funds", key = "'rasyonet:funds:TPF'")
    public List<RasyonetFundDto> refreshBesFunds() {
        log.info("Refreshing TPF (BES) funds (warm-up)");
        return rasyonetFundPort.fetchFundsBySourceCode("TPF");
    }

    @CachePut(cacheNames = "market.tefas.funds", key = "'rasyonet:funds:TAF'")
    public List<RasyonetFundDto> refreshOksFunds() {
        log.info("Refreshing TAF (OKS) funds (warm-up)");
        return rasyonetFundPort.fetchFundsBySourceCode("TAF");
    }

    // ── Fon detayı ────────────────────────────────────────────────────────────

    /**
     * Fon detayını Rasyonet card endpoint'inden çeker.
     * sourceCode: TMF (TEFAS/default) | TPF (BES) | TAF (OKS)
     * Cache key sourceCode içerir — farklı kategoriler çakışmaz.
     */
    @Cacheable(cacheNames = "market.tefas.funds",
               key = "'rasyonet:detail:' + #code.toUpperCase() + ':' + (#sourceCode != null && !#sourceCode.isBlank() ? #sourceCode.toUpperCase() : 'TMF')",
               unless = "#result == null")
    public RasyonetFundDetailDto getFundDetailRich(String code, String sourceCode) {
        log.info("Fetching Rasyonet fund detail: code={}, sourceCode={}", code, sourceCode);
        RasyonetFundDetailDto detail = rasyonetFundPort.fetchFundDetailRich(code, sourceCode);
        if (detail == null) log.warn("Rasyonet card returned null for code={}, sourceCode={}", code, sourceCode);
        return detail;
    }

    /** Geriye dönük uyumluluk — TMF varsayılan */
    public RasyonetFundDetailDto getFundDetailRich(String code) {
        return getFundDetailRich(code, "TMF");
    }
}
