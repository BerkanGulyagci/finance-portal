package com.finance.portal.portfolio.domain;

import com.finance.portal.common.domain.AssetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * İzleme listesi kalemi.
 * <p>
 * Fiyat bilgisi (son fiyat, açılış, yüksek, düşük, fark, hacim vb.) bu tabloda tutulmaz.
 * Fiyat verileri backend response hazırlanırken mevcut market servislerinden anlık olarak çekilir.
 * Veri yoksa ilgili alanlar null / "-" olarak döner.
 */
@Entity
@Table(name = "watchlist_item")
@Getter
@Setter
@NoArgsConstructor
public class WatchlistItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 20)
    private AssetType assetType;

    @Column(name = "notes")
    private String notes;

    @Column(name = "start_price", precision = 19, scale = 6)
    private BigDecimal startPrice;

    @Column(name = "start_currency", length = 10)
    private String startCurrency;

    @Column(name = "added_at", nullable = false, updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (addedAt == null) {
            addedAt = LocalDateTime.now();
        }
    }
}
