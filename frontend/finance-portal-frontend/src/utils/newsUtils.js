// Haber kategori rozet renkleri (Material 3 tonal — açık zemin + koyu metin) ve tarih biçimi.

export const CATEGORY_BADGE = {
  ECONOMY: 'bg-slate-100 text-slate-700',
  STOCKS: 'bg-emerald-100 text-emerald-700',
  FX: 'bg-sky-100 text-sky-700',
  BONDS: 'bg-amber-100 text-amber-800',
  CRYPTO: 'bg-violet-100 text-violet-700',
  COMMODITIES: 'bg-yellow-100 text-yellow-800',
  WORLD: 'bg-rose-100 text-rose-700',
};

export function categoryBadgeClass(key) {
  return CATEGORY_BADGE[key] ?? 'bg-gray-100 text-gray-600';
}

/** Göreli zaman ("5 dk önce" / "3 saat önce") veya tarih. */
export function formatNewsTime(raw, t = (x) => x) {
  if (!raw) return '';
  try {
    const diff = Math.floor((Date.now() - new Date(raw).getTime()) / 60000);
    if (diff < 1) return t('az önce');
    if (diff < 60) return `${diff} ${t('dk önce')}`;
    if (diff < 1440) return `${Math.floor(diff / 60)} ${t('saat önce')}`;
    return new Date(raw).toLocaleDateString('tr-TR', { day: 'numeric', month: 'long', year: 'numeric' });
  } catch {
    return raw;
  }
}

/**
 * Kaynak facet'lerini markaya göre gruplar: "Kriptofoni", "Kriptofoni Altcoin", ... → tek "Kriptofoni".
 * Aynı ilk kelimeyi paylaşan ≥2 kaynak, ortak kelime önekinde toplanır (backend tam-veya-önek eşleştirir).
 */
export function groupSources(sources) {
  const byFirst = {};
  (sources || []).forEach(s => {
    const fw = s.split(' ')[0];
    (byFirst[fw] = byFirst[fw] || []).push(s);
  });
  const groups = Object.values(byFirst).map(list =>
    list.length === 1 ? list[0] : longestCommonWordPrefix(list)
  );
  return groups.sort((a, b) => a.localeCompare(b, 'tr'));
}

function longestCommonWordPrefix(list) {
  const split = list.map(s => s.split(' '));
  const words = [];
  for (let i = 0; i < split[0].length; i++) {
    const w = split[0][i];
    if (split.every(arr => arr[i] === w)) words.push(w);
    else break;
  }
  return words.join(' ') || list[0];
}

/** Detay sayfası için uzun tarih biçimi. */
export function formatNewsDate(raw) {
  if (!raw) return '';
  try {
    return new Date(raw).toLocaleString('tr-TR', {
      day: 'numeric', month: 'long', year: 'numeric', hour: '2-digit', minute: '2-digit',
    });
  } catch {
    return raw;
  }
}
