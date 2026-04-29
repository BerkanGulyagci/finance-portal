package com.finance.portal.market.application.stock;

import com.finance.portal.market.infrastructure.external.midas.MidasStockClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BIST hisse sembol listesi.
 * Uygulama başlangıcında ve her gün gece yarısı Midas'tan dinamik olarak çekilir.
 * Midas erişilemezse önceki liste korunur.
 */
@Component
public class StockSymbolProvider {

    private static final Logger log = LoggerFactory.getLogger(StockSymbolProvider.class);

    private final MidasStockClient midasStockClient;

    // Thread-safe listeler
    private final AtomicReference<List<String>> allSymbols   = new AtomicReference<>(List.of());
    private final AtomicReference<List<String>> bist30       = new AtomicReference<>(List.of());
    private final AtomicReference<List<String>> bist50       = new AtomicReference<>(List.of());
    private final AtomicReference<List<String>> bist100      = new AtomicReference<>(List.of());

    public StockSymbolProvider(MidasStockClient midasStockClient) {
        this.midasStockClient = midasStockClient;
    }

    @PostConstruct
    public void init() {
        refreshSymbols();
    }

    /** Her gün 02:00'de güncelle (endeks bileşenleri 3 ayda bir değişir) */
    @Scheduled(cron = "0 0 2 * * *")
    public void refreshSymbols() {
        log.info("Refreshing BIST symbol lists from Midas...");
        try {
            List<String> all  = midasStockClient.fetchSymbols("");
            List<String> b30  = midasStockClient.fetchSymbols("xu030-bist-30-hisseleri");
            List<String> b50  = midasStockClient.fetchSymbols("xu050-bist-50-hisseleri");
            List<String> b100 = midasStockClient.fetchSymbols("xu100-bist-100-hisseleri");

            if (!all.isEmpty())  { allSymbols.set(all);   log.info("Loaded {} total BIST symbols", all.size()); }
            if (!b30.isEmpty())  { bist30.set(b30);        log.info("Loaded {} BIST30 symbols", b30.size()); }
            if (!b50.isEmpty())  { bist50.set(b50);        log.info("Loaded {} BIST50 symbols", b50.size()); }
            if (!b100.isEmpty()) { bist100.set(b100);      log.info("Loaded {} BIST100 symbols", b100.size()); }
        } catch (Exception e) {
            log.error("Failed to refresh symbol lists: {}", e.getMessage());
        }
    }

    public int getTotalElements() {
        return allSymbols.get().size();
    }

    public List<String> getAllSymbols() {
        return allSymbols.get();
    }

    public List<String> getBist30Symbols() {
        return bist30.get();
    }

    public List<String> getBist50Symbols() {
        return bist50.get();
    }

    public List<String> getBist100Symbols() {
        return bist100.get();
    }

    public List<String> getPagedSymbols(int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 250) throw new IllegalArgumentException("size must be between 1 and 250");
        List<String> all = allSymbols.get();
        int total = all.size();
        int start = page * size;
        if (start >= total) return Collections.emptyList();
        return all.subList(start, Math.min(start + size, total));
    }
}
