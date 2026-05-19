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

export function viopContractNamesMatch(a, b) {
  const na = fixViopContractName(a)?.trim().toLowerCase();
  const nb = fixViopContractName(b)?.trim().toLowerCase();
  return na && nb && na === nb;
}
