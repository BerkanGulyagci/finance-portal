package com.finance.portal.market.application.bond.evds.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TcmbDibsParser} + {@link BondClassifier#classifyFromFamily} birim testi. Gerçek
 * {@code dibs1.txt} bölüm yapısını minik bir örnekle taklit eder: her aile + strip (A/K) +
 * Part A/Part B ayrımı. Odaklar:
 * <ul>
 *   <li><b>TÜFE-endeksli TRT080530T19</b> (CBRT {@code 121TDOZ}) — heuristik onu Kuponlu sanıyordu;
 *       otoriter bölüm üyeliği TÜFE'ye oturtmalı.</li>
 *   <li><b>Strip türü CBRT kodundan</b>: {@code TRT160926KA0} ISIN'i 'A' ile bitiyor (basit kural
 *       Ana Para sanır) ama CBRT {@code 24T2K...} → Kupon stripi. CBRT otoriter, doğru olmalı.</li>
 * </ul>
 */
class TcmbDibsParserTest {

    // Gerçek dosyanın bölüm başlıklarını + sabit-genişlik veri satırı formatını taklit eder.
    private static final String SAMPLE = String.join("\n",
            "TÜRKİYE CUMHURİYET MERKEZ BANKASINCA BELİRLENEN DEVLET İÇ BORÇLANMA SENETLERİ",
            "A)BORÇLANMA SENETLERİ",
            "",
            "1- T.C. HAZİNE VE MALİYE BAKANLIĞINCA İHRAÇ EDİLEN İSKONTOLU DEVLET",
            "İÇ BORÇLANMA SENETLERİNİN DEĞERLERİ AŞAĞIDADIR.",
            "17.06.2026 10B             TRB170626T13           14         98.594",
            "06.01.2027 12T             TRT060127T10          217         81.039",
            "",
            "2- T.C. HAZİNE VE MALİYE BAKANLIĞINCA İHRAÇ EDİLEN KUPONLU DEVLET",
            "24.02.2027 121T2           TRT240227T17         5.50         86.211",
            "11.08.2027 121T2A          TRT110827A17         0.00         70.000",
            "03.06.2026 121T2K          TRT030626K26         0.00         99.000",
            "16.09.2026 24T2K1150328    TRT160926KA0         0.00         99.000",
            "",
            "3- T.C. HAZİNE VE MALİYE BAKANLIĞINCA İHALE YOLU İLE İHRAÇ EDİLEN TÜKETİCİ",
            "FİYAT ENDEKSLİ DEVLET İÇ BORÇLANMA SENETLERİ",
            "08.05.2030 121TDOZ         TRT080530T19         0.00        797.382",
            "07.07.2027 121T2D          TRT070727T13         2.00        150.000",
            "11.08.2027 36T2DA          TRT110827A25         0.00        120.000",
            "03.06.2027 49T2DK          TRT030627K11         0.00        130.000",
            "",
            "4- (b) T.C. HAZİNE VE MALİYE BAKANLIĞINCA İHRAÇ EDİLEN ALTINA DAYALI DEVLET",
            "27.01.2027 12TA2           TRT270127T15         0.00       4200.000",
            "",
            "5- (a) T.C. HAZİNE VE MALİYE BAKANLIĞINCA İHRAÇ EDİLEN AVRO CİNSİ DEVLET İÇ",
            "03.03.2027 12T232          TRT030327F17         0.00        110.000",
            "",
            "7- T.C. HAZİNE VE MALİYE BAKANLIĞINCA İHRAÇ EDİLEN TÜRK LİRASI GECELİK",
            "21.12.2029 61T2DOZ         TRT211229T16         0.00        250.000",
            "",
            "B)KİRA SERTİFİKALARI",
            "",
            "1- HAZİNE MÜSTEŞARLIĞI VARLIK KİRALAMA ŞİRKETİNCE İHRAÇ EDİLEN KİRA",
            "23.06.2032 121D2           TRD230632T15         0.00        140.000",
            "",
            "2- HAZİNE MÜSTEŞARLIĞI VARLIK KİRALAMA ŞİRKETİNCE İHRAÇ EDİLEN TÜKETİCİ KİRA",
            "28.05.2031 121D2D          TRD280531T12         0.00        160.000",
            "",
            "3- (b) HAZİNE MÜSTEŞARLIĞI VARLIK KİRALAMA ŞİRKETİNCE İHRAÇ EDİLEN ALTINA KİRA",
            "27.01.2027 12DA2           TRD270127T13         0.00       4100.000",
            "",
            "4- HAZİNE MÜSTEŞARLIĞI VARLIK KİRALAMA ŞİRKETİNCE İHRAÇ EDİLEN AMERİKAN DOLARI KİRA",
            "22.07.2026 12D201          TRD220726F19         0.00        108.000"
    );

    private BondCategory cat(String isin) {
        return TcmbDibsParser.parse(SAMPLE).get(isin);
    }

    @Test
    @DisplayName("TRT080530T19 (121TDOZ) → TÜFE-endeksli (heuristik bunu Kuponlu sanıyordu)")
    void inflationBond_notMisclassifiedAsFixed() {
        assertThat(cat("TRT080530T19")).isEqualTo(BondCategory.INFLATION_INDEXED_BOND);
    }

    @Test
    @DisplayName("Strip türü CBRT kodundan: TRT160926KA0 (ISIN 'A' ama CBRT 24T2K...) → Kupon stripi")
    void stripTypeFromCbrtCode_overridesAmbiguousIsin() {
        // Basit ISIN-suffix kuralı bunu Ana Para sanırdı; CBRT 'K' otoriter → Kupon stripi.
        assertThat(cat("TRT160926KA0")).isEqualTo(BondCategory.COUPON_STRIP);
    }

    @Test
    @DisplayName("Kuponsuz (Bölüm 1): TRB→bono, TRT→tahvil")
    void zeroCoupon() {
        assertThat(cat("TRB170626T13")).isEqualTo(BondCategory.ZERO_COUPON_BILL);
        assertThat(cat("TRT060127T10")).isEqualTo(BondCategory.ZERO_COUPON_BOND);
    }

    @Test
    @DisplayName("Kuponlu aile (Bölüm 2): strip ayrımı korunur (tam/anapara/kupon)")
    void fixedCouponFamily_stripsPreserved() {
        assertThat(cat("TRT240227T17")).isEqualTo(BondCategory.FIXED_COUPON_BOND);
        assertThat(cat("TRT110827A17")).isEqualTo(BondCategory.PRINCIPAL_STRIP);
        assertThat(cat("TRT030626K26")).isEqualTo(BondCategory.COUPON_STRIP);
    }

    @Test
    @DisplayName("TÜFE aile (Bölüm 3): strip ayrımı korunur (tam/anapara/kupon)")
    void inflationFamily_stripsPreserved() {
        assertThat(cat("TRT070727T13")).isEqualTo(BondCategory.INFLATION_INDEXED_BOND);
        assertThat(cat("TRT110827A25")).isEqualTo(BondCategory.INFLATION_PRINCIPAL_STRIP);
        assertThat(cat("TRT030627K11")).isEqualTo(BondCategory.INFLATION_COUPON_STRIP);
    }

    @Test
    @DisplayName("Altın / Döviz / TLREF aileleri doğru")
    void otherFamilies() {
        assertThat(cat("TRT270127T15")).isEqualTo(BondCategory.GOLD_INDEXED_BOND);
        assertThat(cat("TRT030327F17")).isEqualTo(BondCategory.FX_DENOMINATED_BOND);
        assertThat(cat("TRT211229T16")).isEqualTo(BondCategory.TLREF_INDEXED_BOND);
    }

    @Test
    @DisplayName("Part B kira sertifikaları: düz / TÜFE / altın / döviz")
    void leaseCertificates() {
        assertThat(cat("TRD230632T15")).isEqualTo(BondCategory.LEASE_CERTIFICATE);
        assertThat(cat("TRD280531T12")).isEqualTo(BondCategory.INFLATION_INDEXED_LEASE_CERTIFICATE);
        assertThat(cat("TRD270127T13")).isEqualTo(BondCategory.GOLD_INDEXED_LEASE_CERTIFICATE);
        assertThat(cat("TRD220726F19")).isEqualTo(BondCategory.FX_LEASE_CERTIFICATE);
    }

    @Test
    @DisplayName("CBRT kodu yoksa ISIN suffix'ine düşülür (2-arg fallback)")
    void isinSuffixFallback_whenNoCbrt() {
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.FIXED_COUPON, "TRT110827A17"))
                .isEqualTo(BondCategory.PRINCIPAL_STRIP);
        assertThat(BondClassifier.classifyFromFamily(DibsSectionFamily.INFLATION, "TRT030627K11"))
                .isEqualTo(BondCategory.INFLATION_COUPON_STRIP);
    }

    @Test
    @DisplayName("Boş/null içerik güvenli (boş harita)")
    void emptyContent() {
        assertThat(TcmbDibsParser.parse(null)).isEmpty();
        assertThat(TcmbDibsParser.parse("")).isEmpty();
    }
}
