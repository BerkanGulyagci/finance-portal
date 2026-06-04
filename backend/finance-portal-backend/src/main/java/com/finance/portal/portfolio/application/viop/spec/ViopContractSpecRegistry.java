package com.finance.portal.portfolio.application.viop.spec;

import com.finance.portal.market.application.viop.ViopIndexCodeMapper;
import com.finance.portal.market.infrastructure.external.viop.IsYatirimViopSpecProvider;
import com.finance.portal.portfolio.infrastructure.viop.config.ViopContractSpecProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * YAML'dan yüklenen {@link ViopContractSpec}'leri {@code code → spec} olarak indeksler ve
 * kontrat sembolleriyle ({@code F_AKBNK0626} gibi) hızlı O(1) erişim sağlar.
 *
 * <p>Sembol parse mantığı {@link ViopContractSpecResolver}'da. Çözüm zinciri (öncelik sırası):
 * <ol>
 *   <li><b>YAML</b> — mevcut ~50 dayanak (AKBNK, USDTRY…). Statik, hızlı, kanıtlanmış.
 *       <b>Bu hit hiç değişmez</b> — eski sözleşmeler her zaman YAML kullanır.</li>
 *   <li><b>İş Yatırım scrape</b> — YAML'da OLMAYAN yeni sözleşme için canlı büyüklük + teminat
 *       ({@link IsYatirimViopSpecProvider}, günlük cache).</li>
 *   <li><b>Benzer-tür</b> — scrape de yoksa, sembolden tahmin edilen varlık sınıfının tipik oranı
 *       ({@link ViopContractSpec#similarTypeFallback}).</li>
 *   <li><b>Düz fallback</b> — hiçbiri olmazsa mult=1, %15 ({@link ViopContractSpec#fallback}).</li>
 * </ol>
 * Her durumda WARN loglanır ve uygulama ASLA çökmez.
 */
@Component
public class ViopContractSpecRegistry {

    private static final Logger log = LoggerFactory.getLogger(ViopContractSpecRegistry.class);

    private final ViopContractSpecProperties properties;
    private final ViopIndexCodeMapper indexCodeMapper;
    private final IsYatirimViopSpecProvider scrapeProvider;
    private Map<String, ViopContractSpec> byCode = Collections.emptyMap();

    public ViopContractSpecRegistry(ViopContractSpecProperties properties,
                                    ViopIndexCodeMapper indexCodeMapper,
                                    IsYatirimViopSpecProvider scrapeProvider) {
        this.properties = properties;
        this.indexCodeMapper = indexCodeMapper;
        this.scrapeProvider = scrapeProvider;
    }

    @PostConstruct
    void load() {
        Map<String, ViopContractSpec> map = new HashMap<>();
        for (ViopContractSpecProperties.Entry entry : properties.getContractSpecs()) {
            if (entry.getCode() == null) continue;
            try {
                map.put(entry.getCode().trim().toUpperCase(), entry.toSpec());
            } catch (Exception e) {
                log.warn("VIOP spec parse hatası code={}: {}", entry.getCode(), e.getMessage());
            }
        }
        this.byCode = Map.copyOf(map);
        log.info("VIOP contract specs yüklendi: {} dayanak varlık", byCode.size());
    }

    /**
     * Tam kontrat sembolünden (ör. {@code F_AKBNK0626}) ya da Akbank kanonik adından
     * (ör. {@code "USDTRY (30 HAZ 26) VADELI"}) spec çözer. İkinci formatı
     * {@link ViopIndexCodeMapper#toIsYatirimEndeksCode} kullanarak F_ formatına çevirir.
     *
     * @return Optional.empty() = sembol parse edilemedi VE map'te yok
     */
    public Optional<ViopContractSpec> resolveBySymbol(String contractSymbol) {
        if (contractSymbol == null || contractSymbol.isBlank()) {
            return Optional.empty();
        }
        // 1) Direkt F_ prefix ile parse dene
        String normalized = contractSymbol.trim();
        String code = ViopContractSpecResolver.resolveUnderlier(normalized);
        // 2) Olmadıysa Akbank kanonik adı olabilir — F_ formatına çevir
        if (code == null) {
            try {
                Optional<String> fForm = indexCodeMapper.toIsYatirimEndeksCode(normalized);
                if (fForm.isPresent()) {
                    code = ViopContractSpecResolver.resolveUnderlier(fForm.get());
                }
            } catch (Exception e) {
                log.debug("VIOP sembol çevirisi başarısız: {} — {}", normalized, e.getMessage());
            }
        }
        if (code == null) {
            log.debug("VIOP sembol parse edilemedi: {}", contractSymbol);
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(code));
    }

    /**
     * Sembol için spec döner — çözüm zinciri (bkz. sınıf javadoc): YAML → İş Yatırım scrape →
     * benzer-tür → düz fallback. Her durumda bir spec döner (asla null/exception).
     */
    public ViopContractSpec resolveOrFallback(String contractSymbol) {
        // 1) YAML (statik, mevcut davranış — hiç değişmez)
        Optional<ViopContractSpec> fromYaml = resolveBySymbol(contractSymbol);
        if (fromYaml.isPresent()) {
            return fromYaml.get();
        }

        // 2) İş Yatırım scrape (YAML'da olmayan yeni sözleşme için canlı büyüklük + teminat)
        Optional<ViopContractSpec> fromScrape = scrapeProvider.resolve(contractSymbol);
        if (fromScrape.isPresent()) {
            ViopContractSpec spec = fromScrape.get();
            log.info("VIOP spec scrape'ten alındı symbol={} code={} multiplier={} marginAmount={}",
                    contractSymbol, spec.code(), spec.multiplier(), spec.marginAmount());
            return spec;
        }

        // 3) Benzer-tür (sembolden tahmin edilen varlık sınıfının tipik oranı)
        String code = ViopContractSpecResolver.resolveUnderlier(contractSymbol);
        ViopContractSpec.AssetClass guessed = ViopAssetClassGuesser.guess(contractSymbol, code);
        if (guessed != null) {
            log.warn("VIOP spec bulunamadı symbol={} code={} — benzer-tür fallback ({}, oran=%{}) kullanılıyor",
                    contractSymbol, code, guessed, ViopContractSpec.typicalMarginRate(guessed));
            return ViopContractSpec.similarTypeFallback(code != null ? code : contractSymbol, guessed);
        }

        // 4) Düz fallback (multiplier=1, margin=%15)
        log.warn("VIOP spec bulunamadı symbol={} code={} — düz fallback (multiplier=1, margin=15%) kullanılıyor",
                contractSymbol, code);
        return ViopContractSpec.fallback(code != null ? code : contractSymbol);
    }

    /** Doğrudan YAML key ile lookup (test/admin için). */
    public Optional<ViopContractSpec> resolveByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(byCode.get(code.trim().toUpperCase()));
    }

    public int size() {
        return byCode.size();
    }
}
