package com.finance.portal.market.application.viop;

import com.finance.portal.market.infrastructure.external.viop.AkbankViopClient;
import com.finance.portal.market.presentation.dto.ViopContractDetailDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ViopService {

    private static final Logger log = LoggerFactory.getLogger(ViopService.class);

    // TradingView sembol mapping (sadece bilinen olanlar)
    private static final Map<String, String> TRADINGVIEW_SYMBOL_MAP = new HashMap<>();

    static {
        // Endeks vadeli işlemleri
        TRADINGVIEW_SYMBOL_MAP.put("XU030", "BIST:XU030");
        TRADINGVIEW_SYMBOL_MAP.put("XU100", "BIST:XU100");
        TRADINGVIEW_SYMBOL_MAP.put("XUSIN", "BIST:XUSIN");
        TRADINGVIEW_SYMBOL_MAP.put("XBANK", "BIST:XBANK");
        
        // Döviz vadeli işlemleri
        TRADINGVIEW_SYMBOL_MAP.put("USDTRY", "FX:USDTRY");
        TRADINGVIEW_SYMBOL_MAP.put("EURTRY", "FX:EURTRY");
        
        // Not: Hisse senedi vadeli işlemleri için TradingView'da genelde sembol yok
        // Sadece bilinen ve doğrulanmış sembolleri ekleyin
    }

    private final AkbankViopClient akbankViopClient;

    public ViopService(AkbankViopClient akbankViopClient) {
        this.akbankViopClient = akbankViopClient;
    }

    @Cacheable(cacheNames = "market.viop.contracts", key = "'all'")
    public List<ViopContract> getAllContracts() {
        return akbankViopClient.fetchContracts();
    }

    /**
     * Belirli bir hisse senedine ait VİOP kontratlarını döndürür
     * Örnek: "ALARK" için "ALARK (..." ile başlayan tüm kontratlar
     * Kontrat formatı: "SASA (25 May 26) Vadeli FIZ."
     */
    public List<ViopContract> getContractsByUnderlyingAsset(String underlyingSymbol) {
        List<ViopContract> allContracts = getAllContracts();
        
        log.info("Searching VIOP contracts for underlying asset: {}", underlyingSymbol);
        log.info("Total contracts available: {}", allContracts.size());
        
        // İlk 5 kontratın ismini logla
        allContracts.stream()
                .limit(5)
                .forEach(c -> log.info("Sample contract name: {}", c.getName()));
        
        // Kontrat formatı: "SYMBOL (tarih) Vadeli..." şeklinde
        // Örnek: "ALARK (25 May 26) Vadeli FIZ."
        String searchPattern = underlyingSymbol.toUpperCase() + " (";
        
        List<ViopContract> filtered = allContracts.stream()
                .filter(contract -> contract.getName() != null && 
                        contract.getName().toUpperCase().startsWith(searchPattern))
                .toList();
        
        log.info("Found {} contracts for {} (searching for pattern: '{}')", 
                filtered.size(), underlyingSymbol, searchPattern);
        
        return filtered;
    }

    /**
     * Belirli bir kontratın detayını döndürür.
     * Akbank'tan ana veriyi alır (güvenilir kaynak)
     */
    @Cacheable(cacheNames = "market.viop.detail", key = "#symbol")
    public ViopContractDetailDto getContractDetail(String symbol) {
        List<ViopContract> contracts = getAllContracts();
        
        if (contracts.isEmpty()) {
            log.error("No VIOP contracts available from Akbank");
            throw new IllegalArgumentException("VIOP kontratları yüklenemedi. Lütfen daha sonra tekrar deneyin.");
        }
        
        log.info("Searching for VIOP contract: '{}'", symbol);
        log.info("Total contracts available: {}", contracts.size());
        
        // Kontrat adıyla tam eşleşme ara (case-insensitive)
        ViopContract contract = contracts.stream()
                .filter(c -> c.getName() != null && c.getName().equalsIgnoreCase(symbol))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("VIOP contract not found: '{}'", symbol);
                    log.warn("Sample available contracts: {}", 
                            contracts.stream().map(ViopContract::getName).limit(5).toList());
                    return new IllegalArgumentException("VIOP kontratı bulunamadı: " + symbol);
                });

        log.info("Contract found: '{}'", contract.getName());
        
        // Symbol olarak kontrat adından kısa kod çıkar
        String shortSymbol = extractSymbol(contract.getName());
        return mapToDetailDto(contract, shortSymbol);
    }

    private ViopContractDetailDto mapToDetailDto(ViopContract contract, String symbol) {
        ViopContractDetailDto dto = new ViopContractDetailDto();
        dto.setName(contract.getName());
        dto.setSymbol(symbol);
        dto.setLastPrice(parseBigDecimal(contract.getLastPrice()));
        dto.setChangePercent(parsePercent(contract.getChangePercent()));
        dto.setHigh(parseBigDecimal(contract.getHigh()));
        dto.setLow(parseBigDecimal(contract.getLow()));
        dto.setOpenPositionCount(parseLong(contract.getOpenPositionCount()));
        dto.setOpenPositionChange(parseLong(contract.getOpenPositionChange()));
        dto.setSettlementPrice(parseBigDecimal(contract.getSettlementPrice()));
        dto.setPrevSettlementPrice(parseBigDecimal(contract.getPrevSettlementPrice()));
        dto.setTime(contract.getTime());
        
        // TradingView sembolü varsa ekle (yoksa null kalır)
        String tvSymbol = TRADINGVIEW_SYMBOL_MAP.get(symbol.toUpperCase());
        dto.setTradingViewSymbol(tvSymbol);
        
        return dto;
    }

    /**
     * Kontrat adından sembol çıkarır.
     * Örnek: "AEFES Vadeli 25 May 26" → "AEFES"
     */
    private String extractSymbol(String name) {
        if (name == null) return "";
        // "Vadeli" kelimesinden önceki kısmı al
        int idx = name.indexOf("Vadeli");
        if (idx > 0) {
            return name.substring(0, idx).trim();
        }
        // Boşluktan önceki ilk kelimeyi al
        idx = name.indexOf(" ");
        if (idx > 0) {
            return name.substring(0, idx).trim();
        }
        return name.trim();
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            // Türkçe format: 1.234,56 → 1234.56
            String normalized = value.replace(".", "").replace(",", ".");
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse BigDecimal: {}", value);
            return null;
        }
    }

    private BigDecimal parsePercent(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            // "%+1,23" → 1.23
            String normalized = value.replace("%", "").replace("+", "")
                    .replace(".", "").replace(",", ".");
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse percent: {}", value);
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            // "1.234" → 1234
            String normalized = value.replace(".", "").replace(",", "");
            return Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse Long: {}", value);
            return null;
        }
    }
}
