package com.finance.portal.market.application.bond.evds.model;

/**
 * DİBS / Kira Sertifikası finansal türü.
 *
 * <p>TCMB'nin {@code dibs1.txt} yayınındaki 7 borçlanma + 4 kira sertifikası alt-bölümünü
 * ortogonal kategorilere indirger. Sınıflandırma kaynakları:
 * <ul>
 *   <li><b>CBRT (TCMB) kodu</b> — EVDS SERIE_NAME'nin son parantezinde (örn. {@code 121T2K19240227}).</li>
 *   <li><b>ISIN suffix harfi</b> — T (tam bond) / A (ana para stripi) / K (kupon stripi) / F (yabancı para).</li>
 * </ul>
 *
 * <p>Her kategori {@link BondCurrency} ile birlikte hesap motorunda kullanılır.
 *
 * <p>Tier 1 (TL): {@link #ZERO_COUPON_BILL}, {@link #ZERO_COUPON_BOND}, {@link #FIXED_COUPON_BOND},
 * {@link #PRINCIPAL_STRIP}, {@link #COUPON_STRIP}, {@link #TLREF_INDEXED_BOND}.
 * <br>Tier 2 (TÜFE): {@link #INFLATION_INDEXED_BOND}, {@link #INFLATION_PRINCIPAL_STRIP},
 * {@link #INFLATION_COUPON_STRIP}.
 * <br>Tier 3 (özel): {@link #GOLD_INDEXED_BOND}, {@link #FX_DENOMINATED_BOND},
 * {@link #LEASE_CERTIFICATE} ve TÜFE/altın/USD kira sertifikası varyantları.
 */
public enum BondCategory {

    // ── Tier 1 (TL, /100 scaling, basit kupon mantığı) ──────────────────────

    /** Kuponsuz Hazine Bonosu — ISIN TRB...T, CBRT 10B / 11B. */
    ZERO_COUPON_BILL,

    /** Kuponsuz Devlet Tahvili — ISIN TRT...T (Section 1), CBRT 12T. */
    ZERO_COUPON_BOND,

    /** Sabit kuponlu tam Devlet Tahvili — ISIN TRT...T (Section 2), CBRT 121T2 (suffix yok). */
    FIXED_COUPON_BOND,

    /** Sabit kuponlu DT'nin ana para stripi — ISIN TRT...A, CBRT 121T2A.... */
    PRINCIPAL_STRIP,

    /** Sabit kuponlu DT'nin kupon stripi (tek ödeme) — ISIN TRT...K, CBRT 121T2K.... */
    COUPON_STRIP,

    /** TLREF-endeksli DT (değişken kupon) — Section 7. */
    TLREF_INDEXED_BOND,

    // ── Tier 2 (TÜFE-endeksli) ──────────────────────────────────────────────

    /** TÜFE-endeksli tam Devlet Tahvili — Section 3-T, CBRT 61T.... */
    INFLATION_INDEXED_BOND,

    /** TÜFE-endeksli ana para stripi — Section 3-A. */
    INFLATION_PRINCIPAL_STRIP,

    /** TÜFE-endeksli kupon stripi — Section 3-K. */
    INFLATION_COUPON_STRIP,

    // ── Tier 3 (özel para birimleri / sukuk) ─────────────────────────────────

    /** Altına dayalı senet — Section 4 (birim "adet/gram"). */
    GOLD_INDEXED_BOND,

    /** Yabancı para cinsli DT (EUR/USD) — Section 5-6. */
    FX_DENOMINATED_BOND,

    /**
     * Standart Kira Sertifikası (Sukuk) — Section B-1.
     *
     * <p>Not (gelecek iş): Kira sertifikaları EVDS'in ayrı data group'unda yer alır:
     * {@code bie_pyks} ("Kira Sertifikaları Gösterge Değerleri"). Şu anki
     * {@link com.finance.portal.market.application.bond.evds.EvdsBondService} yalnız
     * {@code bie_pydibs} (DİBS) çekiyor — kira sertifikalarını sisteme dahil etmek için
     * benzer bir {@code EvdsLeaseCertificateService} ile {@code bie_pyks} verisini çekip
     * mevcut classifier/enricher altyapısı reuse edilmeli. Hesap mantığı (qty × price / 100)
     * standart kuponlu DT ile aynı; sadece terminoloji "kira getirisi" / "Sukuk" olur.
     */
    LEASE_CERTIFICATE,

    /** TÜFE-endeksli Kira Sertifikası — Section B-2. */
    INFLATION_INDEXED_LEASE_CERTIFICATE,

    /** Altına dayalı Kira Sertifikası — Section B-3. */
    GOLD_INDEXED_LEASE_CERTIFICATE,

    /** Yabancı para cinsli Kira Sertifikası — Section B-4. */
    FX_LEASE_CERTIFICATE,

    // ── Sentinel ─────────────────────────────────────────────────────────────

    /** Sınıflandırılamadı — bilinmeyen kalıp; fallback olarak fiyat takibi yapılır. */
    UNKNOWN;

    // ── Helper ──────────────────────────────────────────────────────────────

    /** Tier 1 (TL, en basit hesap mantığı) kapsamında mı? */
    public boolean isTier1() {
        return this == ZERO_COUPON_BILL || this == ZERO_COUPON_BOND
                || this == FIXED_COUPON_BOND || this == PRINCIPAL_STRIP
                || this == COUPON_STRIP || this == TLREF_INDEXED_BOND;
    }

    /** TÜFE-endeksli (Tier 2) mi? Nominal değer TÜFE çarpanı ile büyür. */
    public boolean isInflationIndexed() {
        return this == INFLATION_INDEXED_BOND
                || this == INFLATION_PRINCIPAL_STRIP
                || this == INFLATION_COUPON_STRIP
                || this == INFLATION_INDEXED_LEASE_CERTIFICATE;
    }

    /** Strip (ana para veya kupon) mı? Kupon ödemesi yok, sadece vade ödemesi. */
    public boolean isStrip() {
        return this == PRINCIPAL_STRIP || this == COUPON_STRIP
                || this == INFLATION_PRINCIPAL_STRIP || this == INFLATION_COUPON_STRIP;
    }

    /** Periyodik kupon ödemesi yapan mı? */
    public boolean paysCoupon() {
        return this == FIXED_COUPON_BOND || this == TLREF_INDEXED_BOND
                || this == INFLATION_INDEXED_BOND
                || this == LEASE_CERTIFICATE || this == INFLATION_INDEXED_LEASE_CERTIFICATE
                || this == GOLD_INDEXED_LEASE_CERTIFICATE || this == FX_LEASE_CERTIFICATE
                || this == GOLD_INDEXED_BOND || this == FX_DENOMINATED_BOND;
    }

    /** Kira Sertifikası (Sukuk) mı? */
    public boolean isLeaseCertificate() {
        return this == LEASE_CERTIFICATE
                || this == INFLATION_INDEXED_LEASE_CERTIFICATE
                || this == GOLD_INDEXED_LEASE_CERTIFICATE
                || this == FX_LEASE_CERTIFICATE;
    }

    /**
     * EVDS "Gösterge Değeri" doğrudan bir birim (adet / gram) cinsinden kote ediliyor mu?
     * Bu kategorilerde TCMB'nin "100 TL nominal üzerinden" yüzde-quote konvansiyonu
     * UYGULANMAZ — yani holdings hem cost hem mv hesabında {@code /100} ölçeği atlamalıdır:
     * <ul>
     *   <li>{@link #GOLD_INDEXED_BOND}: birim 1 gram has altın, fiyat TL/gram (binlerce TL).</li>
     * </ul>
     *
     * <p>TÜFE-endeksli aile (INFLATION_INDEXED_BOND / STRIP / LEASE) BU LİSTEYE DAHİL DEĞİL.
     * Önceki varsayım "indicator değeri ZATEN nominal-endeksli TL" yanlıştı — TRT150927T11
     * gibi TÜFE-endeksli DT'lerde EVDS gösterge değeri standart "100 TL nominal üzerinden
     * temiz fiyat" (ör. 73.11) olarak gelir; enflasyon endekslemesi yalnız kupon ve vade
     * ödemesine yansır ({@link com.finance.portal.portfolio.service.BondMaturityScheduler}
     * notuna bakınız). Bu yüzden TÜFE-endeksli bondlar da Tier-1 gibi {@code /100}
     * konvansiyonuna girer.
     *
     * <p>Aksi takdirde (Tier 1 TL, Eurobond, FX_DENOMINATED, TÜFE-endeksli aile,
     * kira sertifikaları): klasik % nominal quote → {@code /100} uygulanır.
     */
    public boolean usesPerUnitNominalQuote() {
        return this == GOLD_INDEXED_BOND;
    }
}
