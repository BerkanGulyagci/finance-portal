package com.finance.portal.market.application.viop;

import com.finance.portal.market.application.viop.model.ViopContractDetail;
import com.finance.portal.market.application.viop.port.ViopContractListPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Ek {@link ViopService} testleri — {@link ViopServiceTest} tarafından KAPSANMAYAN
 * kırmızı/sarı dalları (JaCoCo nc/pc) hedefler. Tekrar yok: yalnızca açık kalan
 * koşullar (4 haneli yıl, kısa ay token'ı, ay switch'inin tüm case'leri, geçersiz gün,
 * F_ ters-eşleme null/empty kolları, normalize sonrası boş/kısa needle, whitespace
 * sayı alanları, parsePercent NumberFormatException kolu).
 *
 * Strictness LENIENT — bazı testlerde mapper stub'ları kısa-devre nedeniyle hiç
 * çağrılmayabilir; UnnecessaryStubbingException'dan kaçınılır.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ViopServiceMoreTest {

    @Mock
    private ViopContractListPort contractListPort;

    @Mock
    private ViopIndexCodeMapper indexCodeMapper;

    private ViopService service;

    private ViopService newService() {
        return new ViopService(contractListPort, null, indexCodeMapper);
    }

    private static ViopContract contract(String name) {
        ViopContract c = new ViopContract();
        c.setName(name);
        return c;
    }

    // ─────────────────────────────────────── parseContractMaturity (static) açık dallar

    @Test
    @DisplayName("parseContractMaturity: 4 haneli yıl (>=100) +2000 EKLENMEZ (L133 false dalı)")
    void maturity_fourDigitYearNotOffset() {
        // year=2026 (>=100) → 'year < 100' FALSE → 2000 eklenmez, tarih 2026 kalır
        assertThat(ViopService.parseContractMaturity("XU030 (15 HAZ 2026) Vadeli"))
                .contains(java.time.LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("parseContractMaturity: 2 haneli ay token'ı (<3 char) → bilinmeyen → empty (L136 else dalı)")
    void maturity_shortMonthTokenEmpty() {
        // m.group(3)="HA" uzunluğu 2 (<3) → ternary'nin else kolu (monStr olduğu gibi);
        // TURKISH_MONTH_ABBREV'de "HA" yok → 0 → empty
        assertThat(ViopService.parseContractMaturity("X (15 HA 26) Vadeli")).isEmpty();
    }

    // ─────────────────────────────────── findMatchingContract: needle normalize kolları

    @Test
    @DisplayName("findMatchingContract: blank olmayan ama normalize sonrası BOŞ needle (\".\") → empty (L185/L186)")
    void findMatching_needleBecomesEmptyAfterNormalize() {
        // "." isBlank() DEĞİL → ilk guard'ı geçer; normalize sonrası "" → needle.isEmpty()==true
        when(contractListPort.fetchContracts())
                .thenReturn(List.of(contract("AKBNK (30 Haz 26) Vadeli FIZ.")));
        service = newService();
        assertThat(service.findMatchingContract(".")).isEmpty();
    }

    @Test
    @DisplayName("findMatchingContract: 3 karakterden kısa needle → findByIsYatirimCode erken döner (L207 length<3)")
    void findMatching_shortNeedleBelowThree() {
        // "ab" → isBlank false, normalize "ab" non-empty → findByIsYatirimCode("ab") → length<3 → empty;
        // sonra portföy yolu da regex'e uymaz → empty
        when(contractListPort.fetchContracts())
                .thenReturn(List.of(contract("AKBNK (30 Haz 26) Vadeli FIZ.")));
        service = newService();
        assertThat(service.findMatchingContract("ab")).isEmpty();
    }

    @Test
    @DisplayName("findMatchingContract: F_ ters-eşleme — null isim atlanır, mapper-empty atlanır, eşleşen bulunur (L215/L217)")
    void findMatching_isYatirimSkipsNullAndEmpty() {
        ViopContract nullNamed = contract(null);                     // L215 'getName()==null' → continue
        ViopContract noCode = contract("FOO (30 Haz 26) Vadeli");    // L217 code.isEmpty() → atla
        ViopContract target = contract("AKBNK (30 Haz 26) Vadeli FIZ.");
        when(contractListPort.fetchContracts())
                .thenReturn(List.of(nullNamed, noCode, target));
        when(indexCodeMapper.toIsYatirimEndeksCode("FOO (30 Haz 26) Vadeli"))
                .thenReturn(Optional.empty());
        when(indexCodeMapper.toIsYatirimEndeksCode("AKBNK (30 Haz 26) Vadeli FIZ."))
                .thenReturn(Optional.of("F_AKBNK0626"));
        service = newService();
        assertThat(service.findMatchingContract("F_AKBNK0626")).contains(target);
    }

    // ───────────────────────── findContractForPortfolioSymbol: parse-zoo (ay switch + dallar)

    @Test
    @DisplayName("findContractForPortfolioSymbol: liste her ay token'ını + geçersiz gün/yıl + null/parse-edilemez kontratları parse eder")
    void fuzzyPath_parsesEveryMonthAndEdgeContract() {
        // needle parse OLUR (AKBNK/15/Haz/2026) ve hiçbir kontratla EXACT key eşleşmez →
        // fuzzy yola düşer ve listedeki HER kontrat parseViopNameParts'tan geçer.
        List<ViopContract> list = new ArrayList<>();

        // L367 alt-dalları: underlying eşit ama gün farklı / gün eşit ama ay farklı
        list.add(contract("AKBNK (20 Haz 26) Vadeli FIZ."));   // underlying-eq, day 20!=15
        list.add(contract("AKBNK (15 May 26) Vadeli FIZ."));   // day-eq 15, month May(5)!=Haz(6); "may"->5

        // Türkçe ay kısaltmaları (monthFromToken switch case'leri)
        list.add(contract("MOCA (15 Oca 26) Vadeli"));  // oca->1
        list.add(contract("MSUB (15 Sub 26) Vadeli"));  // sub->2
        list.add(contract("MMAR (15 Mar 26) Vadeli"));  // mar->3
        list.add(contract("MNIS (15 Nis 26) Vadeli"));  // nis->4
        list.add(contract("MTEM (15 Tem 26) Vadeli"));  // tem->7
        list.add(contract("MAGU (15 Agu 26) Vadeli"));  // agu->8
        list.add(contract("MEYL (15 Eyl 26) Vadeli"));  // eyl->9
        list.add(contract("MEKI (15 Eki 26) Vadeli"));  // eki->10
        list.add(contract("MKAS (15 Kas 26) Vadeli"));  // kas->11
        list.add(contract("MARA (15 Ara 26) Vadeli"));  // ara->12

        // İngilizce ay kısaltmaları (switch case'leri)
        list.add(contract("EJAN (15 Jan 26) Vadeli"));  // jan->1
        list.add(contract("EFEB (15 Feb 26) Vadeli"));  // feb->2
        list.add(contract("EAPR (15 Apr 26) Vadeli"));  // apr->4
        list.add(contract("EJUN (15 Jun 26) Vadeli"));  // jun->6
        list.add(contract("EJUL (15 Jul 26) Vadeli"));  // jul->7
        list.add(contract("EAUG (15 Aug 26) Vadeli"));  // aug->8
        list.add(contract("ESEP (15 Sep 26) Vadeli"));  // sep->9
        list.add(contract("EOCT (15 Oct 26) Vadeli"));  // oct->10
        list.add(contract("ENOV (15 Nov 26) Vadeli"));  // nov->11
        list.add(contract("EDEC (15 Dec 26) Vadeli"));  // dec->12

        // default switch (bilinmeyen ay) + kısa ay token'ı (L440 length<3)
        list.add(contract("MDEF (15 Zzz 26) Vadeli"));  // default -> null -> parts null
        list.add(contract("MSHR (15 Ha 26) Vadeli"));   // "ha" len2 (<3) -> default -> null

        // L420 gün doğrulama dalları
        list.add(contract("DZERO (00 Oca 26) Vadeli")); // day=0 -> day<1
        list.add(contract("DBIG (99 Kas 26) Vadeli"));  // day=99 -> day>31

        // expandYear dalları
        list.add(contract("FOURD (15 Mar 2027) Vadeli")); // yy=2027 (>=100) -> L428 true
        list.add(contract("OLDY (15 Nis 85) Vadeli"));    // yy=85 (>=70) -> L431 true

        // L364 null-isim filtresi + L366 parse-edilemez (regex eşleşmez)
        list.add(contract(null));
        list.add(contract("BADPARSE no parens at all"));

        when(contractListPort.fetchContracts()).thenReturn(list);
        service = newService();

        // AKBNK+15+Haz slotunda hiçbir kontrat yok → sameSlot boş → empty
        Optional<ViopContract> r = service.findMatchingContract("AKBNK (15 Haz 26) Vadeli FIZ");
        assertThat(r).isEmpty();
    }

    // ───────────────────────────── mapToDetailDto: whitespace sayı alanları + percent hata kolu

    @Test
    @DisplayName("buildDetailDto: whitespace (boşluk) sayı alanları → null (parseBigDecimal/parseLong trim().isEmpty dalı)")
    void buildDetail_whitespaceNumericFieldsNull() {
        ViopContract c = contract("XU030 Vadeli");
        c.setLastPrice("   ");          // L511: value!=null ama trim().isEmpty() → null
        c.setLow("\t");                 // aynı dal, BigDecimal
        c.setOpenPositionChange("  ");  // L536: parseLong trim().isEmpty() → null
        service = newService();
        ViopContractDetail dto = service.buildDetailDto(c);
        assertThat(dto.getLastPrice()).isNull();
        assertThat(dto.getLow()).isNull();
        assertThat(dto.getOpenPositionChange()).isNull();
    }

    @Test
    @DisplayName("buildDetailDto: parse edilemeyen yüzde değeri → NumberFormatException yutulur, null (parsePercent catch)")
    void buildDetail_unparseablePercentNull() {
        ViopContract c = contract("XU030 Vadeli");
        // "%abc" → '%','+','.',',' temizlenir → "abc" → new BigDecimal("abc") fırlatır → catch → null
        c.setChangePercent("%abc");
        service = newService();
        ViopContractDetail dto = service.buildDetailDto(c);
        assertThat(dto.getChangePercent()).isNull();
    }
}
