package com.finance.portal.market.application.bond.evds.model;

/**
 * TCMB {@code dibs1.txt} yayınındaki <b>bölüm (section) ailesi</b>.
 *
 * <p>Bu, kıymetin hangi finansal aileye ait olduğunun <b>otoriter</b> sinyalidir: kıymet TCMB
 * dosyasında hangi numaralı bölümün altında listeleniyorsa ailesi odur. CBRT kod-deseni tahmini
 * güvenilmezdir — örneğin {@code 49TDOZ} (Kuponlu), {@code 121TDOZ} (TÜFE) ve {@code 61T2DOZ}
 * (TLREF) neredeyse aynı görünür ama farklı bölümlerdedir; sadece bölüm üyeliği kesin ayırır.
 *
 * <p>Aile → {@link BondCategory} eşlemesi (strip alt-türü ISIN son harfinden) için
 * {@link BondClassifier#classifyFromFamily(DibsSectionFamily, String)}.
 */
public enum DibsSectionFamily {
    /** Bölüm 1 — İskontolu (kuponsuz) DT/Hazine Bonosu. ISIN TRB→bono, TRT→tahvil. */
    ZERO_COUPON,
    /** Bölüm 2 — Sabit kuponlu DT (+ ana para / kupon stripleri). */
    FIXED_COUPON,
    /** Bölüm 3 — TÜFE-endeksli DT (+ stripleri). */
    INFLATION,
    /** Bölüm 4(b) — Altına dayalı DT (birim = 1 gr has altın). */
    GOLD,
    /** Bölüm 5-6 — Avro / ABD Doları cinsi DT. */
    FX,
    /** Bölüm 7 — TL gecelik referans (TLREF) endeksli DT. */
    TLREF,
    /** Bölüm 8 — TCMB likidite senetleri (kuponsuz benzeri). */
    LIQUIDITY,
    /** Part B Bölüm 1 — Kira sertifikası (sukuk). */
    LEASE,
    /** Part B Bölüm 2 — TÜFE-endeksli kira sertifikası. */
    LEASE_INFLATION,
    /** Part B Bölüm 3(b) — Altına dayalı kira sertifikası. */
    LEASE_GOLD,
    /** Part B Bölüm 4 — Döviz cinsi kira sertifikası. */
    LEASE_FX
}
