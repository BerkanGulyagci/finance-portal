package com.finance.portal.market.application.cache;

import com.finance.portal.common.application.logging.CentralIntegrationLogService;
import com.finance.portal.common.application.logging.IntegrationLogSupport;
import com.finance.portal.market.application.bond.evds.EvdsBondService;
import com.finance.portal.market.application.economy.InflationDeflatorService;
import com.finance.portal.market.application.funds.service.RasyonetFundService;
import com.finance.portal.market.application.service.MarketFxService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Tahvil + fon LİSTE cache'lerini arka planda sıcak tutar.
 *
 * <p><b>Sorun:</b> Tahvil listesi (DİBS + kira sertifikaları, 537+ enstrüman) EVDS'ten her enstrüman
 * için ayrı çağrıyla yüklenir; soğuk cache'te bu çok ağırdır ve kullanıcı uzun süre bekler.
 *
 * <p><b>Çözüm:</b> Liste {@code @CachePut} ile açılışta ve periyodik olarak arka planda yeniden
 * yüklenir (atomik — evict→reload boşluğu yok). TTL warm-up aralığından uzundur, böylece kullanıcı
 * isteği soğuk yola HİÇ düşmez; her zaman sıcak cache'e isabet eder.
 *
 * <p>Fon listeleri (TMF/TPF/TAF) benzer şekilde sık (8 dk) tazelenir.
 *
 * <p>ShedLock ile çok-instance ortamında aynı anda yalnızca bir node ısıtır.
 */
@Service
public class MarketListCacheWarmupService {

    private static final Logger log = LoggerFactory.getLogger(MarketListCacheWarmupService.class);

    private final EvdsBondService evdsBondService;
    private final RasyonetFundService rasyonetFundService;
    private final MarketFxService marketFxService;
    private final InflationDeflatorService inflationDeflatorService;
    private final CentralIntegrationLogService integrationLogService;
    private final com.finance.portal.market.application.movers.MarketMoversService marketMoversService;

    public MarketListCacheWarmupService(EvdsBondService evdsBondService,
                                        RasyonetFundService rasyonetFundService,
                                        MarketFxService marketFxService,
                                        InflationDeflatorService inflationDeflatorService,
                                        CentralIntegrationLogService integrationLogService,
                                        com.finance.portal.market.application.movers.MarketMoversService marketMoversService) {
        this.evdsBondService = evdsBondService;
        this.rasyonetFundService = rasyonetFundService;
        this.marketFxService = marketFxService;
        this.inflationDeflatorService = inflationDeflatorService;
        this.integrationLogService = integrationLogService;
        this.marketMoversService = marketMoversService;
    }

    // ── Açılışta bir kez (asenkron — uygulama başlatmayı bloklamaz) ──────────────
    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void warmupOnStartup() {
        log.info("Market list cache warmup (startup) starting...");
        // Hızlı + kritik backbone'lar ÖNCE (TCMB FX her TL-çevriminin, TÜFE reel-getiri/what-if'in
        // temeli) → yavaş bonds (~45s) arkasında beklemesinler; ilk kullanıcı soğuk-fetch ödemesin.
        warmFxTcmb();
        warmEconomy();
        warmMovers();
        warmBonds();
        warmFunds();
    }

    // ── Dashboard movers + hacim liderleri: 90 sn'de bir (cache TTL 120 sn) ──────
    // Cold-start ~1.4 sn'yi kullanıcıdan gizler; dashboard kartları hazır gelir.
    @Scheduled(fixedDelayString = "${market.warmup.movers.fixed-delay-ms:90000}",
               initialDelayString = "${market.warmup.movers.initial-delay-ms:90000}")
    @SchedulerLock(name = "market-movers-warmup", lockAtMostFor = "PT2M", lockAtLeastFor = "PT10S")
    public void scheduledMoversWarmup() {
        warmMovers();
    }

    // ── Tahvil listesi: 2 saatte bir (cache TTL 6 saat — warm-up'lar arası expire olmaz) ──
    @Scheduled(fixedDelayString = "${market.warmup.bonds.fixed-delay-ms:7200000}",
               initialDelayString = "${market.warmup.bonds.initial-delay-ms:7200000}")
    @SchedulerLock(name = "market-bonds-list-warmup", lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void scheduledBondsWarmup() {
        log.info("Market bonds list cache scheduled refresh starting...");
        warmBonds();
    }

    // ── Fon listeleri: 8 dakikada bir (cache TTL 10 dakika) ──────────────────────
    @Scheduled(fixedDelayString = "${market.warmup.funds.fixed-delay-ms:480000}",
               initialDelayString = "${market.warmup.funds.initial-delay-ms:600000}")
    @SchedulerLock(name = "market-funds-list-warmup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void scheduledFundsWarmup() {
        log.info("Market funds list cache scheduled refresh starting...");
        warmFunds();
    }

    // ── TCMB FX latest: 2 saatte bir (cache TTL 6 saat) — her TL-çevriminin temeli ──
    @Scheduled(fixedDelayString = "${market.warmup.fx.fixed-delay-ms:7200000}",
               initialDelayString = "${market.warmup.fx.initial-delay-ms:7200000}")
    @SchedulerLock(name = "market-fx-tcmb-warmup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void scheduledFxWarmup() {
        log.info("Market FX (TCMB) cache scheduled refresh starting...");
        warmFxTcmb();
    }

    // ── TÜFE / ABD CPI serisi: 4 saatte bir (cache TTL 6 saat) — reel-getiri + what-if temeli ──
    @Scheduled(fixedDelayString = "${market.warmup.economy.fixed-delay-ms:14400000}",
               initialDelayString = "${market.warmup.economy.initial-delay-ms:14400000}")
    @SchedulerLock(name = "market-economy-warmup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1M")
    public void scheduledEconomyWarmup() {
        log.info("Market economy (TÜFE/CPI) series cache scheduled refresh starting...");
        warmEconomy();
    }

    // ── Internal ─────────────────────────────────────────────────────────────────

    private void warmBonds() {
        try {
            int count = evdsBondService.refreshEvdsBondsAll().size();
            log.info("Market bonds list cache warmed: {} instruments.", count);
        } catch (Exception e) {
            log.warn("Market bonds list cache warmup failed: {}", e.getMessage());
            publishFailure("market_bonds_list_warmup", IntegrationLogSupport.PROVIDER_TCMB, e.getMessage());
        }
    }

    private void warmFunds() {
        int ok = 0;
        int fail = 0;
        // TMF / TPF / TAF — bağımsız: biri patlarsa diğerleri yine ısınsın
        try { rasyonetFundService.refreshAllFunds(); ok++; }
        catch (Exception e) { fail++; log.warn("TMF funds warmup failed: {}", e.getMessage()); }
        try { rasyonetFundService.refreshBesFunds(); ok++; }
        catch (Exception e) { fail++; log.warn("TPF (BES) funds warmup failed: {}", e.getMessage()); }
        try { rasyonetFundService.refreshOksFunds(); ok++; }
        catch (Exception e) { fail++; log.warn("TAF (OKS) funds warmup failed: {}", e.getMessage()); }

        log.info("Market funds list cache warmed: {} ok, {} failed.", ok, fail);
        if (fail > 0) {
            publishFailure("market_funds_list_warmup", IntegrationLogSupport.PROVIDER_RASYONET,
                    fail + " fund category warmup(s) failed");
        }
    }

    private void warmMovers() {
        // Frontend dashboard kartları limit=5 kullanıyor → o key'i ısıt.
        try {
            marketMoversService.refreshMovers(5);
            marketMoversService.refreshVolumeLeaders(5);
            log.info("Market movers + volume-leaders cache warmed (limit=5).");
        } catch (Exception e) {
            log.warn("Market movers/volume-leaders cache warmup failed: {}", e.getMessage());
            publishFailure("market_movers_warmup", IntegrationLogSupport.PROVIDER_YAHOO, e.getMessage());
        }
    }

    private void warmFxTcmb() {
        try {
            int n = marketFxService.refreshTcmbLatest().getRates().size();
            log.info("Market FX (TCMB) cache warmed: {} rates.", n);
        } catch (Exception e) {
            log.warn("Market FX (TCMB) cache warmup failed: {}", e.getMessage());
            publishFailure("market_fx_tcmb_warmup", IntegrationLogSupport.PROVIDER_TCMB, e.getMessage());
        }
    }

    private void warmEconomy() {
        try {
            int tufe = inflationDeflatorService.refreshTufeSeries().size();
            int cpi = inflationDeflatorService.refreshUsCpiSeries().size();
            log.info("Market economy series cache warmed: TÜFE {} pts, US-CPI {} pts.", tufe, cpi);
        } catch (Exception e) {
            log.warn("Market economy series cache warmup failed: {}", e.getMessage());
            publishFailure("market_economy_warmup", IntegrationLogSupport.PROVIDER_EVDS, e.getMessage());
        }
    }

    private void publishFailure(String jobName, String provider, String message) {
        try {
            integrationLogService.publish(
                    IntegrationLogSupport.EVENT_SCHEDULER_JOB_FAILED,
                    "WARN",
                    jobName + " failed: " + message,
                    provider,
                    jobName,
                    null,           // httpStatus
                    null,           // durationMs
                    null,           // fallbackUsed
                    Boolean.TRUE,   // degraded
                    Map.of("jobName", jobName, "trigger", IntegrationLogSupport.TRIGGER_SCHEDULER),
                    MarketListCacheWarmupService.class.getName()
            );
        } catch (Exception ignore) {
            // gözlemlenebilirlik kaydı best-effort — ana işi etkilemesin
        }
    }
}
