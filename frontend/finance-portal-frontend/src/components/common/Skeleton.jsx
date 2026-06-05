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
