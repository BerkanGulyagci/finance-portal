/**
 * Akbank VIOP sözleşme adlarındaki bozuk Türkçe ay kısaltmalarını düzeltir.
 * Backend TurkishCharFixer ile aynı varyantlar (URL eşleştirmesi için).
 */
export function fixViopContractName(name) {
  if (!name) return name;
  return String(name)
    .replace(/Âub/g, 'Şub')
    .replace(/Âu/g, 'Şu')
    .replace(/Åub/g, 'Şub')
    .replace(/Åu/g, 'Şu')
    .replace(/Aşub/g, 'Şub')
    .replace(/Aşu/g, 'Şu')
    .replace(/AĞYu/g, 'Ağu')
    .replace(/AĞY/g, 'Ağ')
    .replace(/Äžu/g, 'Ağu')
    .replace(/Äž/g, 'ğ');
}

/**
 * VİOP sözleşme adını eşleştirme için normalize eder: Türkçe ay düzeltmesi + küçük harf +
 * fazla boşlukları tek boşluğa indir + sondaki nokta/boşlukları at.
 * Akbank canlı listesi "HEKTS (30 Haz 26) Vadeli FIZ." (sonda nokta) gönderir; portföyde
 * tutulan sembol "HEKTS (30 HAZ 26) VADELI FIZ" (noktasız, büyük harf) → birebir eşleşmez.
 */
function normalizeViopName(name) {
  const fixed = fixViopContractName(name);
  if (!fixed) return null;
  return String(fixed)
    .toLowerCase()
    .replace(/\s+/g, ' ')   // çoklu boşluk → tek boşluk
    .replace(/[.\s]+$/, '') // sondaki nokta/boşlukları at
    .trim();
}

export function viopContractNamesMatch(a, b) {
  const na = normalizeViopName(a);
  const nb = normalizeViopName(b);
  return !!na && !!nb && na === nb;
}
