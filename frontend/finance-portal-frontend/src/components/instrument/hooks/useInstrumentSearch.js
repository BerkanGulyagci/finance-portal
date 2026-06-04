import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import client from '../../../lib/http';
import { parseTrNumber } from '../../../utils/numberFormat';
import { pickCommoditySpotPriceTry, pickCommoditySpotPriceUsd, isYahooCommoditySymbol } from '../../../utils/commodityPriceUtils';
import { pickSilverGramCloseTry } from '../../../utils/silverPriceUtils';
import {
  ASSET_TYPES, SEE_ALL_LINKS, PAGE_SIZE, TYPE_CACHE,
  pickGoldSpotPrice, fetchAll, filterItems, buildCommoditySections,
} from '../utils/instrumentSearchUtils';

/**
 * InstrumentSearchModal'in TÜM durum + veri-çekme mantığı: tür-bazlı liste yükleme (cache'li, progressive),
 * arama filtresi (debounce), sonsuz kaydırma sayfalama ve seçim akışı (handleSelect — her varlık türü için
 * canlı fiyat/kur çekip onSelect'e iletir). Modal'i ince bir görünüm katmanına indirir. Davranış orijinaliyle
 * BİREBİR aynıdır — saf mantık taşıma; tek satır hesap/koşul/akış değişmedi.
 */
export function useInstrumentSearch({ initialType, onSelect }) {
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
          const { data } = await client.get(`/api/v1/market/stocks/${encodeURIComponent(item.symbol)}`);
          price = data.data?.summary?.price ?? null;
        } else if (activeType === 'CRYPTO') {
          const { data } = await client.get('/api/v1/market/crypto/all');
          const list = data.data ?? [];
          const coin = (item.id && list.find(c => c.id === item.id))
            || list.find(c => c.symbol?.toLowerCase() === item.symbol.toLowerCase());
          price = coin?.currentPrice ?? null;
        } else if (activeType === 'FX') {
          const { data } = await client.get('/api/v1/market/fx/tcmb/latest');
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
              const { data } = await client.get('/api/v1/commodities/spot', { params: { symbol: sym } });
              commoditySpot = data.data ?? null;
              price = pickCommoditySpotPriceUsd(commoditySpot);
            } else if (sym) {
              const fromList =
                parseTrNumber(item.lastPrice) ??
                parseTrNumber(item.settlementPrice);
              if (fromList != null) {
                price = fromList;
              } else {
                const { data } = await client.get('/api/v1/market/futures/viop', {
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
            const { data } = await client.get('/api/v1/gold/spot');
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
                  client.get('/api/v1/silver/spot'),
                  cat === 'GRAM_TRY'
                    ? client.get('/api/v1/silver/history', { params: { range: '1W', currency: 'TRY' } })
                    : Promise.resolve({ data: { data: null } }),
                ]);
                spotData = spotRes.data?.data;
                if (cat === 'GRAM_TRY') {
                  price = pickSilverGramCloseTry(spotData, histRes.data?.data?.points);
                }
              } else {
                const { data } = await client.get(`/api/v1/precious-metals/${metalKey}/spot`);
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
              const { data } = await client.get('/api/v1/commodities/spot', { params: { symbol: item.symbol } });
              commoditySpot = data.data ?? null;
              price = pickCommoditySpotPriceTry(commoditySpot);
            }
          } catch { /* fiyat bulunamazsa null */ }
        } else if (activeType === 'FUND') {
          try {
            const sourceCode = item.category === 'BES' ? 'TPF' : item.category === 'OKS' ? 'TAF' : 'TMF';
            const { data } = await client.get(`/api/v1/market/funds/tefas/${encodeURIComponent(item.symbol)}`, {
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
              const { data } = await client.get(`/api/v1/market/bonds/evds/${encodeURIComponent(item.symbol)}`);
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

  return {
    activeType, query, setQuery, allItems, displayed, hasMore, loading, priceLoading,
    commodityExpanded, setCommodityExpanded, listRef, searchRef,
    handleTypeChange, handleScroll, handleSelect, loadMore,
    seeAll, currentType, filtered, commodityView,
  };
}
