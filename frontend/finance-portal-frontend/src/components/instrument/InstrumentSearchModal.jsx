import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { createPortal } from 'react-dom';
import { Search, X, Plus } from 'lucide-react';
import client from '../../lib/http';
import { parseTrNumber } from '../../utils/numberFormat';
import {
  pickCommoditySpotPriceTry,
  pickCommoditySpotPriceUsd,
  isYahooCommoditySymbol,
} from '../../utils/commodityPriceUtils';
import { pickSilverGramCloseTry } from '../../utils/silverPriceUtils';
import { useTranslation } from '../../context/LanguageContext';
import {
  ASSET_TYPES, SEE_ALL_LINKS, BOND_CATEGORY_LABELS, PAGE_SIZE, TYPE_CACHE,
  pickGoldSpotPrice, fetchAll, filterItems, buildCommoditySections,
} from './instrumentSearchUtils';

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
  const [activeType, setActiveType] = useState(initialType);
  const [query, setQuery] = useState('');
  const [allItems, setAllItems] = useState([]);   // tüm veri
  const [displayed, setDisplayed] = useState([]); // gösterilen (sayfalı)
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [priceLoading, setPriceLoading] = useState(false);
  const [commodityExpanded, setCommodityExpanded] = useState(false);
  const listRef = useRef(null);
  const searchRef = useRef(null);
  const searchTimer = useRef(null);
  const selectSeqRef = useRef(0);

  // Tüm veriyi yükle
  const loadAll = useCallback(async (type) => {
    setLoading(true);
    setDisplayed([]);
    setPage(1);

    // Bir öğe kümesini state'e yansıt (COMMODITY hariç ilk sayfayı göster).
    const applyItems = (items) => {
      setAllItems(items);
      if (type === 'COMMODITY') {
        setDisplayed([]);
        setHasMore(false);
        setCommodityExpanded(false);
      } else {
        const filtered = filterItems(items, '');
        setDisplayed(filtered.slice(0, PAGE_SIZE));
        setHasMore(filtered.length > PAGE_SIZE);
      }
    };

    try {
      const cached = TYPE_CACHE.get(type);
      if (cached) {
        applyItems(cached);
        setLoading(false);
        return;
      }
      // Progressive: ilk sayfa gelince hemen göster (loading kapanır), kalanı arka planda yüklenir.
      let shownPartial = false;
      const onPartial = (partial) => {
        if (!partial || partial.length === 0) return;
        applyItems(partial);
        setLoading(false);
        shownPartial = true;
      };
      const items = await fetchAll(type, onPartial);
      TYPE_CACHE.set(type, items);
      applyItems(items); // tam liste — arama tüm enstrümanlarda çalışsın
      if (!shownPartial) setLoading(false);
    } catch {
      setAllItems([]);
      setDisplayed([]);
      setHasMore(false);
      setLoading(false);
    }
  }, []);

  // İlk yükleme — modal hangi tür için açıldıysa o türle başla
  useEffect(() => { loadAll(initialType); }, [loadAll, initialType]);

  // Tip değişince
  function handleTypeChange(type) {
    setActiveType(type);
    setQuery('');
    setCommodityExpanded(false);
    loadAll(type);
    setTimeout(() => searchRef.current?.focus(), 50);
  }

  // Query değişince filtrele (emtia hariç — orada buildCommoditySections)
  useEffect(() => {
    clearTimeout(searchTimer.current);
    searchTimer.current = setTimeout(() => {
      if (activeType === 'COMMODITY') return;
      const filtered = filterItems(allItems, query);
      setDisplayed(filtered.slice(0, PAGE_SIZE));
      setPage(1);
      setHasMore(filtered.length > PAGE_SIZE);
    }, 150);
    return () => clearTimeout(searchTimer.current);
  }, [query, allItems, activeType]);

  // Daha fazla yükle
  function loadMore() {
    const filtered = filterItems(allItems, query);
    const nextPage = page + 1;
    const nextItems = filtered.slice(0, nextPage * PAGE_SIZE);
    setDisplayed(nextItems);
    setPage(nextPage);
    setHasMore(nextItems.length < filtered.length);
  }

  // Scroll ile otomatik yükleme
  function handleScroll(e) {
    if (activeType === 'COMMODITY') return;
    const el = e.currentTarget;
    if (!hasMore || loading) return;
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 40) {
      loadMore();
    }
  }

  async function handleSelect(item) {
    const seq = ++selectSeqRef.current;
    setPriceLoading(true);
    try {
      let price = null;
      let commoditySpot = null;
      let fxBuy = null;   // TCMB alış kuru  (kullanıcı satarken alacağı birim fiyat)
      let fxSell = null;  // TCMB satış kuru (kullanıcı alırken ödeyeceği birim fiyat)
      try {
        if (activeType === 'STOCK') {
          const { data } = await client.get(`/api/market/stocks/${encodeURIComponent(item.symbol)}`);
          price = data.data?.summary?.price ?? null;
        } else if (activeType === 'CRYPTO') {
          const { data } = await client.get('/api/market/crypto/all');
          const list = data.data ?? [];
          const coin = (item.id && list.find(c => c.id === item.id))
            || list.find(c => c.symbol?.toLowerCase() === item.symbol.toLowerCase());
          price = coin?.currentPrice ?? null;
        } else if (activeType === 'FX') {
          const { data } = await client.get('/api/market/fx/tcmb/latest');
          const rate = (data.data?.rates ?? []).find(r => r.symbol === item.symbol.toUpperCase());
          if (rate) {
            const unit = rate.unit && rate.unit > 1 ? rate.unit : 1;
            // Olası alan isimleri için geniş fallback — TCMB için buy/sell yeterli,
            // ileride başka FX kaynağı eklenirse de uyumlu kalır
            const rawSell = rate.sell ?? rate.selling ?? rate.forexSelling ?? rate.sellRate
              ?? rate.ask ?? rate.currencySelling ?? rate.currentSellPrice ?? null;
            const rawBuy  = rate.buy  ?? rate.buying  ?? rate.forexBuying  ?? rate.buyRate
              ?? rate.bid ?? rate.currencyBuying  ?? rate.currentBuyPrice  ?? null;
            fxSell = rawSell != null ? rawSell / unit : null;
            fxBuy  = rawBuy  != null ? rawBuy  / unit : null;
            // BUY varsayılan → kurum dövizi kullanıcıya satar → forexSelling kullanılır
            price  = fxSell;
          }
        } else if (activeType === 'FUTURE') {
          const sym = (item.symbol ?? '').trim();
          const yahooStyle = /^[A-Z0-9.=]{1,15}$/i.test(sym);
          try {
            if (yahooStyle) {
              const { data } = await client.get('/api/commodities/spot', { params: { symbol: sym } });
              commoditySpot = data.data ?? null;
              price = pickCommoditySpotPriceUsd(commoditySpot);
            } else if (sym) {
              const fromList =
                parseTrNumber(item.lastPrice) ??
                parseTrNumber(item.settlementPrice);
              if (fromList != null) {
                price = fromList;
              } else {
                const { data } = await client.get('/api/market/futures/viop', {
                  params: { name: sym },
                });
                const d = data.data;
                price =
                  parseTrNumber(d?.lastPrice) ??
                  parseTrNumber(d?.settlementPrice) ??
                  parseTrNumber(d?.prevSettlementPrice) ??
                  null;
              }
            }
          } catch { /* fiyat bulunamazsa null */ }
        } else if (activeType === 'GOLD') {
          try {
            const { data } = await client.get('/api/gold/spot');
            price = pickGoldSpotPrice(item.symbol, data.data);
          } catch { /* fiyat bulunamazsa null */ }
        } else if (activeType === 'COMMODITY') {
          try {
            if (item.symbol.includes(':')) {
              const [metal, cat] = item.symbol.split(':');
              const metalKey = metal.toLowerCase();
              let spotData = null;
              if (metalKey === 'silver') {
                const [spotRes, histRes] = await Promise.all([
                  client.get('/api/silver/spot'),
                  cat === 'GRAM_TRY'
                    ? client.get('/api/silver/history', { params: { range: '1W', currency: 'TRY' } })
                    : Promise.resolve({ data: { data: null } }),
                ]);
                spotData = spotRes.data?.data;
                if (cat === 'GRAM_TRY') {
                  price = pickSilverGramCloseTry(spotData, histRes.data?.data?.points);
                }
              } else {
                const { data } = await client.get(`/api/precious-metals/${metalKey}/spot`);
                spotData = data.data;
              }
              if (spotData && price == null) {
                if (cat === 'GRAM_TRY') {
                  price = pickSilverGramCloseTry(spotData, null);
                } else if (cat === 'KG_TRY') {
                  price = spotData.closeTryKg ?? spotData.weightedAverageTryKg ?? spotData.tryKg ?? null;
                }
              }
            } else {
              const { data } = await client.get('/api/commodities/spot', { params: { symbol: item.symbol } });
              commoditySpot = data.data ?? null;
              price = pickCommoditySpotPriceTry(commoditySpot);
            }
          } catch { /* fiyat bulunamazsa null */ }
        } else if (activeType === 'FUND') {
          try {
            const sourceCode = item.category === 'BES' ? 'TPF' : item.category === 'OKS' ? 'TAF' : 'TMF';
            const { data } = await client.get(`/api/market/funds/tefas/${encodeURIComponent(item.symbol)}`, {
              params: { sourceCode },
            });
            price = data.data?.price ?? null;
          } catch { /* fiyat bulunamazsa null */ }
        } else if (activeType === 'BOND') {
          try {
            const raw =
              item.indicatorValue ??
              item.evdsValue ??
              item.price;
            if (raw != null && raw !== '') {
              const n = typeof raw === 'number' ? raw : parseFloat(String(raw).replace(/\s/g, '').replace(',', '.'));
              if (Number.isFinite(n) && n > 0) price = n;
            }
            if ((price == null || !Number.isFinite(price)) && item.symbol) {
              const { data } = await client.get(`/api/market/bonds/evds/${encodeURIComponent(item.symbol)}`);
              const d = data?.data;
              if (d?.indicatorValue != null) {
                const n = Number(d.indicatorValue);
                if (Number.isFinite(n) && n > 0) price = n;
              }
            }
          } catch { /* fiyat bulunamazsa null */ }
        }
      } catch { /* fiyat bulunamazsa null */ }

      if (seq !== selectSeqRef.current) return;

      const currency =
        activeType === 'GOLD' || activeType === 'COMMODITY' || activeType === 'BOND'
          ? 'TRY'
          : activeType === 'FX'
            ? item.symbol
            : activeType === 'FUTURE' && isYahooCommoditySymbol(item.symbol)
              ? 'USD'
              : undefined;

      onSelect({
        symbol: item.symbol,
        assetType: activeType,
        name: item.name ?? item.symbol,
        price,
        currency,
        fxBuy,
        fxSell,
        category: item.category,
        ...(item.subType ? { subType: item.subType } : {}),
        // BOND için kategori-bazlı modal uyarıları + manuel kupon ekleme referansı
        ...(activeType === 'BOND' ? {
          cbrtCode: item.cbrtCode,
          maturityDate: item.maturityDate,
          couponRate: item.couponRate,
        } : {}),
        commoditySpot: activeType === 'COMMODITY' && isYahooCommoditySymbol(item.symbol)
          ? commoditySpot
          : null,
      });
    } finally {
      if (seq === selectSeqRef.current) setPriceLoading(false);
    }
  }

  const seeAll = SEE_ALL_LINKS[activeType];
  const currentType = ASSET_TYPES.find(t => t.value === activeType);
  const filtered = filterItems(allItems, query);

  const commodityView = useMemo(
    () =>
      activeType === 'COMMODITY'
        ? buildCommoditySections(allItems, query, commodityExpanded)
        : null,
    [activeType, allItems, query, commodityExpanded],
  );

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
