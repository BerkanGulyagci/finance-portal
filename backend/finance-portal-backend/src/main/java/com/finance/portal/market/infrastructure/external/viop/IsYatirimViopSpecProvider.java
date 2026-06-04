package com.finance.portal.market.infrastructure.external.viop;

import com.finance.portal.portfolio.application.viop.spec.ViopAssetClassGuesser;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec.AssetClass;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpec.SettlementType;
import com.finance.portal.portfolio.application.viop.spec.ViopContractSpecResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * YAML'da bulunamayan VİOP sözleşmeleri için İş Yatırım'dan canlı spec sağlar (büyüklük + teminat).
 *
 * <p>{@link ViopContractSpecRegistry}'nin çözüm zincirinde 2. adım. {@link IsYatirimViopSpecClient}'ı
 * çağırıp tüm sözleşme map'ini {@code @Cacheable} ile günde bir kez çeker (teminatlar gün-bazlı
 * güncellenir); tek tek sembol çözümü bu cache'lenmiş map üzerinden O(1) yapılır.
 *
 * <p>İş Yatırım'ın TL-tutar teminatını ({@code BASLANGIC_TEMINATI}) {@code marginAmount} olarak
 * taşır → teminat hesabı fiyattan bağımsız ve gerçek (Takasbank) değerle yapılır. Çekemezse
 * {@link Optional#empty()} döner; registry benzer-tür fallback'e düşer.
 */
@Component
public class IsYatirimViopSpecProvider {

    private static final Logger log = LoggerFactory.getLogger(IsYatirimViopSpecProvider.class);

    private final IsYatirimViopSpecClient client;

    public IsYatirimViopSpecProvider(IsYatirimViopSpecClient client) {
        this.client = client;
    }

    /**
     * Tüm İş Yatırım VİOP spec'lerini günde bir kez çeker (cache: {@code market.viop.specs}).
     * Boş map dönerse (kaynak çökmüş) çağıran taraf fallback'e gider.
     */
    @Cacheable(cacheNames = "market.viop.specs", unless = "#result == null || #result.isEmpty()")
    public Map<String, IsYatirimViopSpecClient.IsYatirimViopSpec> allSpecs() {
        return client.fetchSpecs();
    }

    /**
     * Tam kontrat sembolü ({@code F_AKBNK0626}) için scrape spec'i çözer.
     *
     * @return doldurulmuş {@link ViopContractSpec} (marginAmount içerir) ya da empty (bulunamadı/çökme)
     */
    public Optional<ViopContractSpec> resolve(String contractSymbol) {
        if (contractSymbol == null || contractSymbol.isBlank()) {
            return Optional.empty();
        }
        Map<String, IsYatirimViopSpecClient.IsYatirimViopSpec> all;
        try {
            all = allSpecs();
        } catch (Exception e) {
            log.debug("VIOP scrape map alınamadı symbol={}: {}", contractSymbol, e.getMessage());
            return Optional.empty();
        }
        if (all == null || all.isEmpty()) {
            return Optional.empty();
        }

        String key = contractSymbol.trim().toUpperCase();
        IsYatirimViopSpecClient.IsYatirimViopSpec raw = all.get(key);
        if (raw == null) {
            return Optional.empty();
        }

        String code = ViopContractSpecResolver.resolveUnderlier(contractSymbol);
        if (code == null) {
            code = key;
        }
        AssetClass assetClass = ViopAssetClassGuesser.guess(contractSymbol, code);
        if (assetClass == null) {
            assetClass = AssetClass.SINGLE_STOCK;
        }

        // marginRate'i de hesaplanabilir tut (geri uyum / log): teminat varsa oran bilinmez (fiyat gerek),
        // o yüzden tipik oranı koy; ASIL hesap marginAmount üzerinden gider (ViopValuationService).
        BigDecimal marginRate = ViopContractSpec.typicalMarginRate(assetClass);
        SettlementType settlement = raw.physical() ? SettlementType.PHYSICAL : SettlementType.CASH;
        String currency = raw.currency() != null ? raw.currency() : "TRY";

        return Optional.of(new ViopContractSpec(
                code,
                assetClass,
                raw.multiplier(),
                marginRate,
                currency,
                settlement,
                raw.marginAmount()   // ← gerçek teminat TL tutarı (asıl margin hesabı buradan)
        ));
    }
}
