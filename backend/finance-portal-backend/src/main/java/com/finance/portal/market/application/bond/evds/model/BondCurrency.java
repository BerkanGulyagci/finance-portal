package com.finance.portal.market.application.bond.evds.model;

/**
 * DİBS / Kira Sertifikasının nominal cinsi.
 * TCMB bie_pydibs verisinde her enstrüman bu kovalardan birine düşer.
 *
 * <ul>
 *   <li>{@link #TRY} — yurt içi DİBS çoğunluğu (Hazine bonosu, kuponlu DT, TÜFE-endeksli, TLREF, kira sertifikası)</li>
 *   <li>{@link #USD} — yurt içi USD cinsli DT/kira sertifikası</li>
 *   <li>{@link #EUR} — yurt içi EUR cinsli DT</li>
 *   <li>{@link #GOLD} — altına dayalı senetler (birim "gram altın")</li>
 * </ul>
 */
public enum BondCurrency {
    TRY,
    USD,
    EUR,
    GOLD
}
