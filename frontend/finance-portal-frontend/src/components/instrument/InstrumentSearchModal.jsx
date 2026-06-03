import { createPortal } from 'react-dom';
import { Search, X, Plus } from 'lucide-react';
import { useTranslation } from '../../context/LanguageContext';
import { ASSET_TYPES, BOND_CATEGORY_LABELS } from './instrumentSearchUtils';
import { useInstrumentSearch } from './useInstrumentSearch';

/**
 * Yeniden kullanılabilir enstrüman arama modal'ı.
 * - Tip seçilince hemen varsayılan liste gösterilir
 * - Arama case-insensitive, hem sembol hem isim üzerinden
 * - Sonsuz kaydırma: aşağı kaydırdıkça daha fazla sonuç yüklenir
 * - FON → Rasyonet TEFAS fonları
 * - VADELİ → VİOP sözleşmeleri
 * - EMTİA → BİST kıymetli maden (yalnızca ₺) + Yahoo emtialar (TCMB kuru ile ₺)
 */


// ── Component ─────────────────────────────────────────────────────────────────

export default function InstrumentSearchModal({ portfolioName, onSelect, onClose, initialType = 'STOCK', allowIndices = false }) {
  const { t } = useTranslation();
  const {
    activeType, query, setQuery, allItems, displayed, hasMore, loading, priceLoading,
    commodityExpanded, setCommodityExpanded, listRef, searchRef,
    handleTypeChange, handleScroll, handleSelect, loadMore,
    seeAll, currentType, filtered, commodityView,
  } = useInstrumentSearch({ initialType, onSelect });

  function renderInstrumentRow(item, i, keyPrefix = '') {
    const isStockOrFund = activeType === 'STOCK' || activeType === 'FUND';
    const primary = isStockOrFund ? item.symbol : (item.name ?? item.symbol);
    const secondary = isStockOrFund
      ? (item.name && item.name !== item.symbol ? item.name : null)
      : (!String(item.symbol ?? '').includes(':') && item.symbol !== item.name ? item.symbol : null);
    const typeLabel = activeType === 'BOND' ? t('dibs') : (currentType?.label ? t(currentType.label).toLowerCase() : '');
    return (
      <button
        key={`${keyPrefix}-${item.symbol}-${i}`}
        onClick={() => handleSelect(item)}
        disabled={priceLoading}
        className="group w-full flex items-center justify-between p-4 text-left border-b border-[#e2e1eb] last:border-b-0 hover:bg-[#f3f3fc] cursor-pointer transition-colors disabled:opacity-50"
      >
        <div className="flex flex-col min-w-0 flex-1">
          <span className="font-bold text-[#1a1b22] truncate">{primary}</span>
          {secondary && <span className="text-[#434653] text-sm mt-0.5 truncate">{secondary}</span>}
          {(item.category || item.metal) && (
            <span className="flex items-center gap-1.5 mt-1">
              {item.category && (
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                  item.category === 'BES'     ? 'bg-emerald-50 text-emerald-700' :
                  item.category === 'OKS'     ? 'bg-purple-50 text-purple-700' :
                  item.category === 'Osmanlı' ? 'bg-orange-50 text-orange-700' :
                  activeType === 'BOND'       ? 'bg-indigo-50 text-indigo-700' :
                                                'bg-blue-50 text-blue-700'
                }`}>
                  {activeType === 'BOND'
                    ? (BOND_CATEGORY_LABELS[item.category] ? t(BOND_CATEGORY_LABELS[item.category]) : item.category)
                    : item.category}
                </span>
              )}
              {item.metal && (
                <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-amber-50 text-amber-700">
                  {t('BİST')}
                </span>
              )}
            </span>
          )}
        </div>
        <div className="flex items-center gap-3 ml-3 shrink-0">
          <span className="text-[10px] font-bold tracking-wider uppercase bg-[#e8e7f1] text-[#434653] px-2 py-1 rounded">
            {typeLabel}
          </span>
          <span className="text-[#c4c5d5] group-hover:text-[#093eaa] transition-colors p-1 rounded-full group-hover:bg-[#d0e1fb]">
            <Plus className="w-4 h-4" />
          </span>
        </div>
      </button>
    );
  }

  const commodityEmptyQuery =
    activeType === 'COMMODITY' &&
    commodityView &&
    query.trim() &&
    commodityView.sections.every(s => !s.items.length);

  return createPortal((
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1a1b22]/30">
      <div
        className="bg-white rounded-2xl shadow-2xl border border-[#e2e1eb] w-full max-w-2xl overflow-hidden flex flex-col"
        style={{ maxHeight: '85vh' }}
        role="dialog"
        aria-modal="true"
      >

        {/* Header */}
        <div className="px-4 pt-4 pb-3 sm:px-6 sm:pt-6 sm:pb-4 border-b border-[#e2e1eb] flex justify-between items-start shrink-0">
          <div>
            <h2 className="text-xl sm:text-2xl font-bold text-[#1a1b22] mb-1">{t('Enstrüman Seç')}</h2>
            <p className="text-sm text-[#434653]">
              {portfolioName
                ? t('{name} portföyüne ekle', { name: portfolioName })
                : t('Portföyünüze yeni bir varlık ekleyin')}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label={t('Kapat')}
            className="text-[#434653] hover:text-[#1a1b22] transition-colors rounded-full p-2 sm:p-1 hover:bg-[#f3f3fc]"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Tip sekmeleri — alt çizgili (M3). Taşmasın diye alt satıra sarar (İngilizce'de "Indices" kesilmesin). */}
        <div className="px-3 pt-2 sm:px-6 bg-[#f3f3fc]/60 border-b border-[#e2e1eb] flex flex-wrap gap-x-1 gap-y-0.5 shrink-0">
          {ASSET_TYPES.filter(at => at.value !== 'INDICATOR' || allowIndices).map(at => (
            <button
              key={at.value}
              type="button"
              onClick={() => handleTypeChange(at.value)}
              className={`px-4 py-2 border-b-2 text-sm whitespace-nowrap transition-colors ${
                activeType === at.value
                  ? 'border-[#093eaa] text-[#093eaa] font-bold'
                  : 'border-transparent text-[#434653] hover:text-[#1a1b22]'
              }`}
            >
              {t(at.label)}
            </button>
          ))}
        </div>

        {/* Arama */}
        <div className="p-4 pb-3 sm:p-6 sm:pb-4 border-b border-[#e2e1eb] shrink-0">
          <div className="relative flex items-center w-full h-12 rounded-lg bg-[#f3f3fc] border border-[#c4c5d5] overflow-hidden focus-within:border-[#093eaa] focus-within:ring-1 focus-within:ring-[#093eaa] transition-all">
            <Search className="w-5 h-5 text-[#747684] ml-4 mr-2 shrink-0" />
            <input
              ref={searchRef}
              type="text"
              value={query}
              onChange={e => setQuery(e.target.value)}
              placeholder={currentType?.placeholder ? t(currentType.placeholder) : t('Sembol veya enstrüman ara...')}
              autoFocus
              className="w-full h-full bg-transparent border-none focus:ring-0 text-[#1a1b22] text-sm placeholder-[#747684] outline-none"
            />
          </div>
          {/* Sonuç sayısı */}
          {!loading && allItems.length > 0 && (
            <span className="block mt-2 text-xs text-[#747684]">
              {activeType === 'COMMODITY' && commodityView ? (
                query.trim()
                  ? t('{count} sonuç · "{query}" için', { count: commodityView.totalCount, query })
                  : commodityExpanded
                    ? t('{count} enstrüman', { count: commodityView.totalCount })
                    : t('{visible} öne çıkan · {total} toplam', { visible: commodityView.visibleCount, total: commodityView.totalCount })
              ) : query.trim()
                ? t('{count} sonuç · "{query}" için', { count: filtered.length, query })
                : t('{count} enstrüman', { count: allItems.length })}
            </span>
          )}
        </div>

        {/* Sonuçlar — kaydırılabilir */}
        <div
          ref={listRef}
          onScroll={handleScroll}
          className="overflow-y-auto flex-1 bg-white"
          style={{ minHeight: 120 }}
        >
          {loading && (
            <div className="flex items-center justify-center py-10 gap-1.5">
              <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
              <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
              <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
            </div>
          )}

          {!loading && ((activeType === 'COMMODITY' && commodityView && commodityEmptyQuery)
            || (activeType !== 'COMMODITY' && displayed.length === 0 && allItems.length > 0)) && (
            <div className="px-4 py-10 text-center">
              <p className="text-[#434653] text-sm">
                <span className="font-semibold">&quot;{query}&quot;</span> {t('için sonuç bulunamadı.')}
              </p>
              <p className="text-xs text-[#747684] mt-1">
                {t('Farklı bir sembol veya tür deneyin.')}
              </p>
            </div>
          )}

          {!loading && allItems.length === 0 && (
            <div className="px-4 py-10 text-center text-[#747684] text-sm">
              {t('Veri yüklenemedi. Lütfen tekrar deneyin.')}
            </div>
          )}

          {!loading && activeType === 'COMMODITY' && commodityView && !commodityEmptyQuery && (
            <>
              {commodityView.sections.map(section => (
                <div key={section.key}>
                  {section.title && (
                    <div className="px-4 py-2 text-[11px] font-bold text-[#434653] uppercase tracking-wide bg-[#f3f3fc] border-b border-[#e2e1eb] sticky top-0 z-[1]">
                      {t(section.title)}
                    </div>
                  )}
                  {section.items.map((item, i) => renderInstrumentRow(item, i, section.key))}
                </div>
              ))}
              {commodityView.showExpandButton && (
                <button
                  type="button"
                  onClick={() => setCommodityExpanded(true)}
                  className="w-full py-3 text-xs font-medium text-[#093eaa] hover:underline transition-colors text-center border-t border-[#e2e1eb]"
                >
                  {t('Diğer emtiaları göster ({count} gizli)', { count: commodityView.totalCount - commodityView.visibleCount })}
                </button>
              )}
              {!commodityView.showExpandButton && commodityExpanded && !query.trim() && (
                <button
                  type="button"
                  onClick={() => setCommodityExpanded(false)}
                  className="w-full py-2.5 text-xs text-[#747684] hover:text-[#434653] transition-colors text-center border-t border-[#e2e1eb]"
                >
                  {t('Daha az göster')}
                </button>
              )}
            </>
          )}

          {!loading && activeType !== 'COMMODITY' && displayed.map((item, i) => renderInstrumentRow(item, i, 'd'))}

          {/* Daha fazla yükle göstergesi */}
          {hasMore && !loading && activeType !== 'COMMODITY' && (
            <button
              type="button"
              onClick={loadMore}
              className="w-full py-3 text-xs font-medium text-[#093eaa] hover:underline transition-colors text-center border-t border-[#e2e1eb]"
            >
              {t('Daha fazla göster ({count} kaldı)', { count: filtered.length - displayed.length })}
            </button>
          )}
        </div>

        {/* Alt link */}
        {seeAll && (
          <div className="p-3 sm:p-4 bg-white border-t border-[#e2e1eb] flex justify-center shrink-0">
            <a
              href={seeAll.path}
              target="_blank"
              rel="noreferrer"
              className="text-sm font-medium text-[#093eaa] hover:underline flex items-center gap-1"
            >
              {t(seeAll.label)} →
            </a>
          </div>
        )}
      </div>
    </div>
  ), document.body);
}
