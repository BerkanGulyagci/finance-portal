/**
 * Yeniden kullanılabilir skeleton (iskelet) loading bileşenleri.
 * Veri yüklenirken spinner/metin yerine içeriğin gri placeholder'ını gösterir
 * (animate-pulse) — algılanan hızı artırır, layout shift'i azaltır.
 *
 * Tailwind `bg-gray-200` + dark override (index.css'teki .dark .bg-gray-200) ile
 * koyu modda da doğru görünür.
 */

/** Tek gri çubuk (satır/etiket placeholder'ı). */
export function SkeletonBar({ className = '' }) {
  return <div className={`bg-gray-200 rounded animate-pulse ${className}`} />;
}

/**
 * Tablo iskeleti — liste sayfaları (hisse/fon/tahvil) için.
 * @param {number} rows  satır sayısı (varsayılan 8)
 * @param {number} cols  sütun sayısı (varsayılan 6)
 */
export function SkeletonTable({ rows = 8, cols = 6 }) {
  return (
    <div className="overflow-hidden">
      {/* Başlık satırı */}
      <div className="flex items-center gap-4 px-4 py-3 border-b border-gray-100 bg-gray-50">
        {Array.from({ length: cols }).map((_, i) => (
          <SkeletonBar key={`h-${i}`} className={`h-3 ${i === 1 ? 'flex-[2]' : 'flex-1'}`} />
        ))}
      </div>
      {/* Veri satırları */}
      {Array.from({ length: rows }).map((_, r) => (
        <div key={`r-${r}`} className="flex items-center gap-4 px-4 py-3.5 border-b border-gray-50">
          {Array.from({ length: cols }).map((_, c) => (
            <div key={`c-${c}`} className={c === 0 ? 'flex items-center gap-2.5 flex-1' : (c === 1 ? 'flex-[2]' : 'flex-1')}>
              {c === 0 && <div className="w-6 h-6 rounded-full bg-gray-200 animate-pulse shrink-0" />}
              <SkeletonBar className="h-3.5 w-full" />
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

/**
 * Kart ızgarası iskeleti — kart-tabanlı listeler (kripto, emtia) için.
 * @param {number} count kart sayısı (varsayılan 8)
 */
export function SkeletonCardGrid({ count = 8 }) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="rounded-2xl border border-gray-100 bg-white p-4 flex items-center gap-3">
          <div className="w-10 h-10 rounded-full bg-gray-200 animate-pulse shrink-0" />
          <div className="flex-1 space-y-2">
            <SkeletonBar className="h-3.5 w-2/3" />
            <SkeletonBar className="h-3 w-1/2" />
          </div>
          <SkeletonBar className="h-5 w-16" />
        </div>
      ))}
    </div>
  );
}

/**
 * Portföy kartları grid iskeleti — PortfolioPage'in gerçek kart düzeninin silüeti
 * (başlık + tip/para-birimi rozetleri + 3 satır değer: maliyet/piyasa değeri/kâr-zarar).
 * Generic SkeletonCardGrid (tek satır avatar) yerine kullanılır → yükleme hâli, dolu hâle birebir benzer.
 */
export function SkeletonPortfolioGrid({ count = 6 }) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-6">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="bg-white rounded-lg border border-gray-200 shadow-sm p-4 sm:p-6">
          {/* Başlık + chevron */}
          <div className="flex items-start justify-between mb-3">
            <div className="flex-1 min-w-0 space-y-1.5">
              <SkeletonBar className="h-4 w-1/2" />
              <SkeletonBar className="h-3 w-2/3" />
            </div>
            <SkeletonBar className="h-4 w-4 ml-2 shrink-0" />
          </div>
          {/* Tip + para birimi rozetleri */}
          <div className="flex items-center gap-2 mb-4">
            <SkeletonBar className="h-5 w-20" />
            <SkeletonBar className="h-5 w-12" />
          </div>
          {/* 3 satır değer (sonuncusu üst-çizgili) */}
          <div className="space-y-2.5">
            <div className="flex justify-between"><SkeletonBar className="h-3.5 w-24" /><SkeletonBar className="h-3.5 w-20" /></div>
            <div className="flex justify-between"><SkeletonBar className="h-3.5 w-20" /><SkeletonBar className="h-3.5 w-24" /></div>
            <div className="flex justify-between pt-2 border-t border-gray-100"><SkeletonBar className="h-3.5 w-16" /><SkeletonBar className="h-5 w-24" /></div>
          </div>
        </div>
      ))}
    </div>
  );
}

/**
 * Profil sayfası iskeleti — ProfilePage'in gerçek düzeni: üst hero kart (avatar + isim +
 * rozetler) + altta 2 sütunlu bilgi kartları. "Yükleniyor..." metninin yerine.
 */
export function SkeletonProfile() {
  return (
    <div className="space-y-3 sm:space-y-5">
      {/* Üst hero kart */}
      <div className="m3-card p-3 sm:p-6">
        <div className="flex flex-col gap-5 lg:flex-row lg:items-center">
          <div className="flex items-center gap-4 min-w-0 flex-1">
            <div className="w-16 h-16 rounded-full bg-gray-200 animate-pulse shrink-0" />
            <div className="flex-1 space-y-2 min-w-0">
              <SkeletonBar className="h-5 w-40" />
              <SkeletonBar className="h-3.5 w-56" />
              <div className="flex gap-1.5 mt-1">
                <SkeletonBar className="h-5 w-24" />
                <SkeletonBar className="h-5 w-20" />
              </div>
            </div>
          </div>
          <div className="flex gap-2">
            <SkeletonBar className="h-9 w-28 rounded-lg" />
            <SkeletonBar className="h-9 w-28 rounded-lg" />
          </div>
        </div>
      </div>
      {/* 2 sütunlu bilgi kartları */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 sm:gap-5 items-start">
        {Array.from({ length: 2 }).map((_, i) => (
          <div key={i} className="m3-card p-3 sm:p-6 space-y-4">
            <div className="flex items-center gap-3">
              <SkeletonBar className="h-9 w-9 rounded-lg" />
              <div className="flex-1 space-y-1.5">
                <SkeletonBar className="h-4 w-1/3" />
                <SkeletonBar className="h-3 w-1/2" />
              </div>
            </div>
            {Array.from({ length: 3 }).map((_, j) => (
              <div key={j} className="flex justify-between">
                <SkeletonBar className="h-3.5 w-24" />
                <SkeletonBar className="h-3.5 w-32" />
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Hisse/endeks detay iskeleti — StockDetailPage'in GERÇEK düzeni: tam genişlik kart içinde
 * 2 fiyat kutusu (Güncel Fiyat | Günlük Hacim) yan yana + grafik kartı (mod/range tab + grafik) +
 * alt bilgi kartları. Sol-340px SkeletonDetail'den (kripto/fon) farklı; hisseye birebir uyar.
 */
export function SkeletonStockDetail() {
  return (
    <div className="space-y-4 sm:space-y-6">
      {/* Fiyat + hacim + grafik kartı */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
        <div className="grid grid-cols-1 sm:grid-cols-2 divide-y sm:divide-y-0 sm:divide-x divide-gray-100">
          <div className="p-4 sm:p-6 space-y-2">
            <SkeletonBar className="h-9 w-32" />
            <SkeletonBar className="h-3.5 w-24" />
          </div>
          <div className="p-4 sm:p-6 space-y-2">
            <SkeletonBar className="h-7 w-28" />
            <SkeletonBar className="h-3.5 w-28" />
          </div>
        </div>
        <div className="px-4 pt-2 pb-4">
          {/* Mod tab + range butonları */}
          <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
            <SkeletonBar className="h-8 w-40 rounded-lg" />
            <SkeletonBar className="h-8 w-28 rounded-lg" />
          </div>
          <div className="flex gap-1.5 mb-3">
            {Array.from({ length: 8 }).map((_, i) => <SkeletonBar key={i} className="h-7 w-10 rounded-md" />)}
          </div>
          {/* Grafik alanı */}
          <SkeletonBar className="h-[340px] w-full rounded-xl" />
        </div>
      </div>
      {/* Alt bilgi kartları (Finansal Bilgiler / Şirket Hakkında / İlişkili VİOP) */}
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4 sm:p-6">
          <SkeletonBar className="h-5 w-48" />
        </div>
      ))}
    </div>
  );
}

/**
 * Ekonomi sayfası iskeleti — sol dikey bölüm-navigasyonu + sağ içerik (başlık + paragraflar +
 * grafik kutusu) silueti. EconomyPage'in gerçek sol-nav + içerik düzenine uyar
 * (generic SkeletonCardGrid yerine).
 */
export function SkeletonEconomy({ sections = 3 }) {
  return (
    <div className="lg:flex lg:gap-8 lg:items-start">
      {/* Sol dikey nav */}
      <nav className="hidden lg:block lg:w-52 shrink-0">
        <SkeletonBar className="h-3 w-20 mb-3 ml-4" />
        <div className="border-l border-gray-200 space-y-2 pl-4 py-1">
          {Array.from({ length: 6 }).map((_, i) => (
            <SkeletonBar key={i} className="h-3.5 w-3/4" />
          ))}
        </div>
      </nav>
      {/* Sağ içerik: her bölüm başlık + paragraf çizgileri + grafik kutusu */}
      <div className="flex-1 min-w-0 space-y-12">
        {Array.from({ length: sections }).map((_, i) => (
          <div key={i}>
            <SkeletonBar className="h-6 w-1/3 mb-3" />
            <div className="space-y-2 mb-5">
              <SkeletonBar className="h-3.5 w-full" />
              <SkeletonBar className="h-3.5 w-5/6" />
            </div>
            <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4 sm:p-6">
              <SkeletonBar className="h-4 w-40 mx-auto mb-4" />
              <SkeletonBar className="h-[260px] w-full rounded-xl" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Haber ana sayfası "hero" iskeleti — büyük banner + sağ "öne çıkan" listesi düzeni.
 * NewsPage HeroSection'ın yükleme hâli için.
 */
export function SkeletonHero() {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-6">
      {/* Sol: büyük banner */}
      <div className="lg:col-span-2">
        <div className="h-[300px] sm:h-[420px] rounded-md bg-gray-200 animate-pulse relative overflow-hidden">
          {/* Alt köşede başlık çubukları (gerçek banner'daki gibi) */}
          <div className="absolute bottom-0 left-0 right-0 p-6 space-y-3">
            <SkeletonBar className="h-5 w-24 rounded-md bg-gray-300" />
            <SkeletonBar className="h-7 w-2/3 bg-gray-300" />
            <SkeletonBar className="h-4 w-1/3 bg-gray-300" />
          </div>
        </div>
      </div>
      {/* Sağ: öne çıkan haberler listesi */}
      <div className="bg-white rounded-md border border-gray-200 p-5 space-y-4">
        <SkeletonBar className="h-5 w-40" />
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className={`flex gap-3 ${i > 0 ? 'border-t border-gray-100 pt-3' : ''}`}>
            <SkeletonBar className="w-16 h-12 rounded shrink-0" />
            <div className="flex-1 space-y-2">
              <SkeletonBar className="h-3.5 w-full" />
              <SkeletonBar className="h-3 w-1/2" />
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

/**
 * Portföy/liste detay iskeleti — başlık + özet kutuları + holdings tablosu düzeni.
 * Portföy detay sayfasının yükleme hâli için (üst toolbar + 4 stat + tablo).
 */
export function SkeletonPortfolio() {
  return (
    <div className="space-y-5">
      {/* Başlık satırı */}
      <div className="flex items-center justify-between gap-4">
        <SkeletonBar className="h-7 w-48" />
        <div className="flex gap-2">
          <SkeletonBar className="h-9 w-28 rounded-full" />
          <SkeletonBar className="h-9 w-9 rounded-full" />
        </div>
      </div>
      {/* Özet stat kutuları */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="rounded-2xl border border-gray-100 bg-white p-4 space-y-2">
            <SkeletonBar className="h-3 w-1/2" />
            <SkeletonBar className="h-6 w-3/4" />
          </div>
        ))}
      </div>
      {/* Holdings tablosu */}
      <div className="rounded-2xl border border-gray-100 bg-white overflow-hidden">
        <SkeletonTable rows={6} cols={7} />
      </div>
    </div>
  );
}

/**
 * Detay sayfası iskeleti — sol özet kartı + ana içerik (grafik) düzeni.
 * Kripto/fon/hisse detay sayfalarının yükleme hâli için.
 */
export function SkeletonDetail() {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-[340px_1fr] gap-4 sm:gap-6">
      {/* Sol özet kartı */}
      <div className="space-y-4">
        <div className="rounded-2xl border border-gray-100 bg-white p-5 space-y-4">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 rounded-full bg-gray-200 animate-pulse" />
            <div className="flex-1 space-y-2">
              <SkeletonBar className="h-4 w-2/3" />
              <SkeletonBar className="h-3 w-1/3" />
            </div>
          </div>
          <SkeletonBar className="h-8 w-1/2" />
          <SkeletonBar className="h-4 w-1/3" />
          <div className="flex gap-2">
            <SkeletonBar className="h-9 flex-1 rounded-lg" />
            <SkeletonBar className="h-9 flex-1 rounded-lg" />
          </div>
        </div>
        <div className="rounded-2xl border border-gray-100 bg-white p-5 space-y-3">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="flex justify-between">
              <SkeletonBar className="h-3 w-1/3" />
              <SkeletonBar className="h-3 w-1/4" />
            </div>
          ))}
        </div>
      </div>
      {/* Ana içerik (grafik alanı) */}
      <div className="rounded-2xl border border-gray-100 bg-white p-5">
        <div className="flex gap-2 mb-4">
          <SkeletonBar className="h-8 w-20 rounded-lg" />
          <SkeletonBar className="h-8 w-20 rounded-lg" />
          <div className="flex-1" />
          <SkeletonBar className="h-8 w-32 rounded-lg" />
        </div>
        <SkeletonBar className="h-[360px] w-full rounded-xl" />
      </div>
    </div>
  );
}

/**
 * Değerli metal detay iskeleti — GoldPage/SilverPage/PreciousMetalPage'in YENİ düzeni:
 * üst sekmeler + fiyat başlığı, SOL grafik (2/3) + SAĞ fiyat-bilgileri kartı (1/3),
 * altta çevirici + teorik fiyatlar. Eski bounce-spinner (GoldLoadingState) yerine.
 * @param tabs üst sekme sayısı (altın 8, gümüş 3, platin/paladyum yok → 0)
 */
export function SkeletonMetalDetail({ tabs = 0 }) {
  return (
    <div className="space-y-5">
      {tabs > 0 && (
        <div className="flex gap-2 border-b border-gray-100 pb-1 overflow-hidden">
          {Array.from({ length: tabs }).map((_, i) => (
            <SkeletonBar key={i} className="h-9 w-24 rounded-lg shrink-0" />
          ))}
        </div>
      )}
      <div className="space-y-2">
        <SkeletonBar className="h-5 w-40" />
        <SkeletonBar className="h-9 w-56" />
        <SkeletonBar className="h-4 w-32" />
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-3 sm:gap-5 items-start">
        <div className="lg:col-span-2 min-w-0 space-y-3">
          <div className="flex gap-2">
            <SkeletonBar className="h-8 w-24 rounded-lg" />
            <div className="flex-1" />
            <SkeletonBar className="h-8 w-48 rounded-lg" />
          </div>
          <SkeletonBar className="h-[300px] w-full rounded-xl" />
        </div>
        <div className="min-w-0 space-y-3">
          <SkeletonBar className="h-8 w-full rounded-lg" />
          <div className="rounded-2xl border border-gray-200 bg-white p-4 sm:p-5">
            <SkeletonBar className="h-4 w-1/3 mb-3" />
            <div className="grid grid-cols-2 gap-2.5">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="rounded-xl bg-gray-50 p-3 space-y-1.5">
                  <SkeletonBar className="h-2.5 w-2/3 mx-auto" />
                  <SkeletonBar className="h-4 w-3/4 mx-auto" />
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 sm:gap-5 items-start">
        <div className="rounded-2xl border border-gray-200 bg-white p-5 h-40" />
        <div className="rounded-2xl border border-gray-200 bg-white p-5 h-40" />
      </div>
    </div>
  );
}

/**
 * Tahvil/Bono detay iskeleti — bond detay sayfalarının GERÇEK düzenine uyar
 * (generic SkeletonDetail'in sol-340px + grafik şablonu bu sayfalara uymuyordu).
 *
 * variant='dibs' (BondDetailPage / TCMB EVDS DİBS-Bono):
 *   header kartı → işlem-buton satırı → 4'lü metrik bar → 2 sütun (göstergeler | bilgi tablosu)
 *   → mini-analiz kartı → tarihsel grafik kartı → uyarı şeridi.
 * variant='eurobond' (EurobondDetailPage / Business Insider):
 *   geri-link → isim+fiyat üst kartı → fiyat grafiği kartı → tahvil bilgileri tablosu.
 */
export function SkeletonBondDetail({ variant = 'dibs' }) {
  if (variant === 'eurobond') {
    return (
      <div className="space-y-5">
        {/* Üst kart: isim + canlı fiyat */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5">
          <div className="flex items-start justify-between gap-4 flex-wrap">
            <div className="min-w-0 space-y-2 flex-1">
              <SkeletonBar className="h-6 w-2/3" />
              <SkeletonBar className="h-3.5 w-1/2" />
              <SkeletonBar className="h-3 w-1/3" />
            </div>
            <div className="flex items-center gap-2">
              <SkeletonBar className="h-8 w-24 rounded-lg" />
              <SkeletonBar className="h-8 w-24 rounded-lg" />
            </div>
          </div>
          <div className="mt-4 pt-4 border-t border-gray-100 flex items-baseline gap-3">
            <SkeletonBar className="h-9 w-40" />
            <SkeletonBar className="h-5 w-16" />
          </div>
        </div>
        {/* Fiyat grafiği kartı */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5">
          <SkeletonBar className="h-5 w-32 mb-3" />
          <SkeletonBar className="h-[300px] w-full rounded-xl" />
        </div>
        {/* Tahvil bilgileri tablosu */}
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-4 py-3 border-b border-gray-100">
            <SkeletonBar className="h-5 w-40" />
          </div>
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className="flex items-center justify-between gap-4 px-4 py-3 border-b border-gray-100 last:border-0">
              <SkeletonBar className="h-3.5 w-1/3" />
              <SkeletonBar className="h-3.5 w-1/4" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  // variant === 'dibs'
  return (
    <div className="space-y-3 sm:space-y-5">
      {/* 1. Header kartı — kod + tür rozeti + tarih badge'leri + kaynak */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm px-5 py-4">
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <div className="flex items-center gap-3">
            <SkeletonBar className="h-5 w-5 rounded shrink-0" />
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <SkeletonBar className="h-7 w-40" />
                <SkeletonBar className="h-5 w-14 rounded-full" />
              </div>
              <div className="flex gap-2">
                <SkeletonBar className="h-5 w-24 rounded-full" />
                <SkeletonBar className="h-5 w-24 rounded-full" />
                <SkeletonBar className="h-5 w-20 rounded-full" />
              </div>
            </div>
          </div>
          <div className="space-y-1.5 text-right">
            <SkeletonBar className="h-3 w-28 ml-auto" />
            <SkeletonBar className="h-3 w-32 ml-auto" />
          </div>
        </div>
      </div>

      {/* 2. İşlem butonları satırı (sağa yaslı) */}
      <div className="flex justify-end gap-2">
        <SkeletonBar className="h-9 w-28 rounded-lg" />
        <SkeletonBar className="h-9 w-24 rounded-lg" />
      </div>

      {/* 3. 4'lü metrik bar */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="bg-white rounded-2xl border border-gray-200 shadow-sm p-3 sm:p-5 space-y-2">
            <SkeletonBar className="h-3 w-2/3" />
            <SkeletonBar className="h-7 w-1/2" />
          </div>
        ))}
      </div>

      {/* 4. Göstergeler (sol) + Kıymet Bilgileri (sağ) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 sm:gap-5 items-start">
        {Array.from({ length: 2 }).map((_, col) => (
          <div key={col} className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
            <SkeletonBar className="h-4 w-32 mb-4" />
            <div className="space-y-3">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="flex items-center justify-between py-1 border-b border-gray-50 last:border-0">
                  <SkeletonBar className="h-3 w-1/3" />
                  <SkeletonBar className="h-3 w-1/4" />
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* 5. Mini analiz kartı (tam genişlik) */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5 space-y-3">
        <SkeletonBar className="h-4 w-40" />
        <SkeletonBar className="h-3.5 w-full" />
        <SkeletonBar className="h-3.5 w-5/6" />
      </div>

      {/* 6. Tarihsel grafik kartı */}
      <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
        <div className="flex items-center justify-between gap-2 mb-3 flex-wrap">
          <SkeletonBar className="h-5 w-40" />
          <SkeletonBar className="h-8 w-28 rounded-lg" />
        </div>
        <SkeletonBar className="h-[300px] w-full rounded-xl" />
      </div>

      {/* 7. Uyarı şeridi */}
      <div className="rounded-2xl border border-amber-200 bg-amber-50 p-4 space-y-2">
        <SkeletonBar className="h-3 w-full" />
        <SkeletonBar className="h-3 w-2/3" />
      </div>
    </div>
  );
}

/**
 * Genel liste sayfası iskeleti — başlık + opsiyonel sekmeler + tablo silüeti.
 * Emtia/endeks/bildirim/ekonomik-takvim gibi liste sayfaları için.
 */
export function SkeletonList({ tabs = 0, rows = 10, cols = 6 }) {
  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <SkeletonBar className="h-6 w-48" />
        <SkeletonBar className="h-3.5 w-72" />
      </div>
      {tabs > 0 && (
        <div className="flex gap-2">
          {Array.from({ length: tabs }).map((_, i) => (
            <SkeletonBar key={i} className="h-9 w-28 rounded-xl" />
          ))}
        </div>
      )}
      <SkeletonTable rows={rows} cols={cols} />
    </div>
  );
}
