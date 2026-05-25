import { useTranslation } from '../../i18n/LanguageContext';

/**
 * Liste sayfaları için ortak sayfalama bileşeni — tasarım hisse listesi sayfasına dayanır
 * (« ‹ 1 2 3 4 5 6 7 … son › »). `page` 0-tabanlıdır.
 *
 * İsteğe bağlı:
 *  - totalElements + unitLabel → solda "Toplam X birim · Sayfa N/M" özeti
 *  - pageSize + pageSizeOptions + onPageSizeChange → solda "Sayfa Boyutu" seçici
 *
 * Her sayfanın kendi sayfalama mantığı (sunucu/istemci) korunur; bu bileşen yalnız görünüm + olaylar.
 */
export default function Pagination({
  page,
  totalPages = 0,
  onChange,
  totalElements = null,
  unitLabel = '',
  pageSize = null,
  pageSizeOptions = null,
  onPageSizeChange = null,
  className = '',
}) {
  const { t } = useTranslation();

  const hasPager = totalPages > 1;
  const hasSizer = Array.isArray(pageSizeOptions) && pageSizeOptions.length > 0 && onPageSizeChange;
  if (!hasPager && !hasSizer) return null;

  const WINDOW = 7;
  const nums = [];
  if (hasPager) {
    let start;
    if (totalPages <= WINDOW) start = 0;
    else if (page < 4) start = 0;
    else if (page > totalPages - 4) start = totalPages - WINDOW;
    else start = page - 3;
    for (let i = 0; i < Math.min(WINDOW, totalPages); i++) nums.push(start + i);
  }
  const showTail = hasPager && nums.length > 0 && nums[nums.length - 1] < totalPages - 1;

  const navBtn =
    'px-2 py-1.5 rounded-lg border border-gray-200 text-xs text-gray-600 hover:bg-gray-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors';
  const pageBtn = (active) =>
    `min-w-[2.1rem] px-3 py-1.5 rounded-lg border text-xs font-semibold transition-colors ${
      active ? 'bg-[#093eaa] text-white border-[#093eaa]' : 'border-gray-200 text-gray-700 hover:bg-gray-50'
    }`;

  return (
    <div className={`p-4 flex items-center justify-between border-t border-gray-100 flex-wrap gap-3 ${className}`}>
      <div className="flex items-center gap-4 flex-wrap">
        {totalElements != null && (
          <span className="text-xs text-gray-500">
            {t('Toplam')} {totalElements}{unitLabel ? ` ${t(unitLabel)}` : ''}
            {hasPager && <> · {t('Sayfa')} {page + 1} / {totalPages}</>}
          </span>
        )}
        {hasSizer && (
          <label className="flex items-center gap-2 text-xs text-gray-500">
            {t('Sayfa Boyutu')}
            <select
              value={pageSize ?? ''}
              onChange={(e) => onPageSizeChange(Number(e.target.value))}
              className="px-2.5 py-1.5 rounded-lg border border-gray-200 bg-white text-xs font-semibold text-gray-700 focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
            >
              {pageSizeOptions.map((o) => (
                <option key={o} value={o}>{o}</option>
              ))}
            </select>
          </label>
        )}
      </div>

      {hasPager && (
        <div className="flex items-center gap-1">
          <button type="button" className={navBtn} disabled={page === 0} onClick={() => onChange(0)} aria-label={t('İlk sayfa')}>«</button>
          <button type="button" className={navBtn} disabled={page === 0} onClick={() => onChange(page - 1)} aria-label={t('Önceki sayfa')}>‹</button>
          {nums.map((p) => (
            <button key={p} type="button" onClick={() => onChange(p)} className={pageBtn(p === page)}>
              {p + 1}
            </button>
          ))}
          {showTail && (
            <>
              <span className="px-1 text-xs text-gray-400">…</span>
              <button type="button" onClick={() => onChange(totalPages - 1)} className={pageBtn(page === totalPages - 1)}>
                {totalPages}
              </button>
            </>
          )}
          <button type="button" className={navBtn} disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)} aria-label={t('Sonraki sayfa')}>›</button>
          <button type="button" className={navBtn} disabled={page >= totalPages - 1} onClick={() => onChange(totalPages - 1)} aria-label={t('Son sayfa')}>»</button>
        </div>
      )}
    </div>
  );
}
