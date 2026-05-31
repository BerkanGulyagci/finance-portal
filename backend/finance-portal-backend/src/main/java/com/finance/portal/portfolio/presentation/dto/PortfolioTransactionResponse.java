package com.finance.portal.portfolio.presentation.dto;

import com.finance.portal.common.domain.AssetType;
import com.finance.portal.portfolio.domain.TransactionType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PortfolioTransactionResponse {

    private UUID id;
    private String symbol;
    private AssetType assetType;
    private TransactionType transactionType;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal commission;
    private LocalDateTime transactionDate;
    private LocalDateTime createdAt;

    /** VİOP pozisyon yönü: "LONG" veya "SHORT". Yalnızca FUTURE için doludur; diğerleri null. */
    private String direction;
    /** VİOP kontrat çarpanı (multiplier). Yalnızca FUTURE için doludur; diğerleri null. */
    private BigDecimal viopMultiplier;
    /** VİOP başlangıç teminat oranı (marginRate). Yalnızca FUTURE için doludur; diğerleri null. */
    private BigDecimal viopMarginRate;

    /**
     * BOND nominal ölçek katsayısı (par scale). Yalnızca BOND için doludur; diğerleri null.
     * <ul>
     *   <li>{@code 100} — Klasik DİBS / Eurobond / TÜFE-endeksli / kira sertifikası: TCMB
     *       konvansiyonu "100 TL nominal üzerinden temiz fiyat" → {@code Toplam = qty × price / 100}.</li>
     *   <li>{@code 1} — Altına dayalı senet ({@link com.finance.portal.market.application.bond.evds.model.BondCategory#GOLD_INDEXED_BOND}):
     *       birim 1 gram has altın, fiyat TL/gram → {@code Toplam = qty × price} (bölme yok).</li>
     * </ul>
     * Frontend BU alanı işlem-satırı "Toplam" hesabında bölücü olarak kullanır.
     */
    private BigDecimal bondParScale;
}
