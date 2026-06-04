/* eslint-disable react-refresh/only-export-components -- render-helper (renderCell) + küçük sunum component'leri bir arada; fast-refresh dev uyarısı, davranışı etkilemez */
// HoldingsTable'dan çıkarılan hücre-render katmanı: küçük sunum component'leri (Dash/PnlAmt/PnlPct/
// BeatChip/ReelDash) + renderCell (kolon anahtarına göre tablo hücresini üretir). JSX/Tailwind ve
// mantık orijinaliyle birebir aynıdır (taşıma). `t` (çeviri) parametre olarak gelir.

import { Link } from 'react-router-dom';
import { TrendingUp, TrendingDown, Info, AlertTriangle } from 'lucide-react';
import TrendBadge from '../../../components/common/TrendBadge';
import { getWatchlistDetailPath } from '../constants/watchlistMarketRoutes';
import { MASK_MONEY, MASK_PERCENT, MASK_QTY } from '../utils/portfolioFormatUtils';
import { getCommodityUnit } from '../utils/commodityUnit';
import {
  ASSET_LABELS, resolveHoldingName, num, positionMarketValue, unrealizedGainLoss,
  positionDailyGainLoss, fmtNum, fmtQty, fmtQtyFund, fmtFundNavPrice, fmtPrice,
  fmtMoneyTwoDecimals, formatPercentWithSuffix, fmtDate, fmtVol,
} from '../utils/holdingsTableUtils';

// ── Küçük UI parçaları ────────────────────────────────────────────────────────

export function Dash() {
  return <span className="text-gray-300">-</span>;
}

/** Reel/enflasyon kolonları boşken nedenini açıklayan tire (örn. bu ay alınmış pozisyon). */
function ReelDash({ t }) {
  return (
    <span
      className="text-gray-300 cursor-help"
      title={t('Enflasyon faktörü hesaplanamadı: pozisyon çok yeni ya da bu dönemin enflasyonu (TÜFE) henüz açıklanmadı.')}
    >
      -
    </span>
  );
}

function PnlAmt({ value, currency, valuesHidden }) {
  if (valuesHidden) {
    return <span className="font-bold text-gray-500 tracking-widest">{MASK_MONEY}</span>;
  }
  if (value == null) return <Dash />;
  const n = parseFloat(value);
  if (!Number.isFinite(n)) return <Dash />;
  if (Math.abs(n) < 1e-14) {
    return <span className="font-bold text-gray-500">{fmtMoneyTwoDecimals(0, currency)}</span>;
  }
  const pos = n > 0;
  return (
    <span className={`font-bold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? '+' : ''}{fmtMoneyTwoDecimals(n, currency) ?? String(n)}
    </span>
  );
}

/** Enflasyonu yendi mi? rozeti — reel K/Z işaretine göre. */
function BeatChip({ beat, t }) {
  return beat ? (
    <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-700 bg-emerald-50 border border-emerald-200 px-2 py-0.5 rounded-md whitespace-nowrap">
      <TrendingUp className="w-3 h-3" /> {t('Yendi')}
    </span>
  ) : (
    <span className="inline-flex items-center gap-1 text-xs font-bold text-rose-700 bg-rose-50 border border-rose-200 px-2 py-0.5 rounded-md whitespace-nowrap">
      <TrendingDown className="w-3 h-3" /> {t('Yenildi')}
    </span>
  );
}

function PnlPct({ value, valuesHidden }) {
  if (valuesHidden) {
    return <span className="font-semibold text-gray-500 tracking-widest">{MASK_PERCENT}</span>;
  }
  if (value == null) return <Dash />;
  const n = parseFloat(value);
  if (!Number.isFinite(n)) return <Dash />;
  const display = formatPercentWithSuffix(n);
  if (Math.abs(n) < 1e-14) {
    return <span className="font-semibold text-gray-500">{display}</span>;
  }
  const pos = n > 0;
  return (
    <span className={`font-semibold ${pos ? 'text-emerald-600' : 'text-rose-600'}`}>
      {pos ? '+' : ''}{display}
    </span>
  );
}

// ── Hücre render ─────────────────────────────────────────────────────────────

export function renderCell(key, h, commoditySpots, valuesHidden, t) {
  const cur = h.currency;
  const isFund = String(h.assetType ?? '').toUpperCase() === 'FUND';

  switch (key) {
    case 'name': {
      const name = resolveHoldingName(h);
      const detailPath = getWatchlistDetailPath(h.assetType, h.symbol);
      if (detailPath) {
        return (
          <Link
            to={detailPath}
            className="text-sm text-gray-800 block max-w-[180px] truncate font-medium hover:text-[#093eaa] hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-[#093eaa]/40 rounded"
            title={`${name} — ${t('Piyasa detayı')}`}
          >
            {name}
          </Link>
        );
      }
      return (
        <span className="text-sm text-gray-800 block max-w-[180px] truncate" title={name}>
          {name}
        </span>
      );
    }

    case 'symbol':
      return <span className="font-bold text-[#093eaa] text-sm whitespace-nowrap">{h.symbol}</span>;

    case 'assetType': {
      const cat = String(h.category ?? '');
      const isInfBond = h.assetType === 'BOND' && cat.startsWith('INFLATION_');
      const isFxBond = h.assetType === 'BOND' && (cat === 'FX_DENOMINATED_BOND' || cat === 'FX_LEASE_CERTIFICATE');
      const isGoldBond = h.assetType === 'BOND' && (cat === 'GOLD_INDEXED_BOND' || cat === 'GOLD_INDEXED_LEASE_CERTIFICATE');
      const isSukuk = h.assetType === 'BOND' && cat.includes('LEASE_CERTIFICATE');
      const isFutureRow = h.assetType === 'FUTURE';
      const viopDir = isFutureRow ? String(h.viopDirection ?? 'LONG').toUpperCase() : null;
      const isShortPos = viopDir === 'SHORT';
      return (
        <div className="inline-flex items-center gap-1 whitespace-nowrap">
          <span className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded-md font-semibold">
            {ASSET_LABELS[h.assetType] ? t(ASSET_LABELS[h.assetType]) : h.assetType}
          </span>
          {isFutureRow && (
            <span
              className={`text-[10px] px-1.5 py-0.5 rounded font-bold ${
                isShortPos
                  ? 'bg-rose-100 text-rose-700'
                  : 'bg-emerald-100 text-emerald-700'
              }`}
              title={isShortPos ? t('Açığa satış (SHORT) — fiyat düşerse kar') : t('Uzun pozisyon (LONG) — fiyat artarsa kar')}
            >
              {isShortPos ? 'S' : 'L'}
            </span>
          )}
          {isInfBond && (
            <span
              className="text-[10px] bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded font-semibold"
              title={t('TÜFE-endeksli — günlük kotasyon 100 TL nominal başına temiz fiyattır; enflasyon endekslemesi kupon ve vade ödemesine yansır')}
            >
              {t('TÜFE')}
            </span>
          )}
          {isFxBond && (
            <span
              className="text-[10px] bg-blue-100 text-blue-700 px-1.5 py-0.5 rounded font-semibold"
              title={t('Yabancı para cinsli — piyasa değeri TCMB satış kuruyla TL\'ye çevrilir')}
            >
              {h.currency ?? 'FX'}
            </span>
          )}
          {isGoldBond && (
            <span
              className="text-[10px] bg-yellow-100 text-yellow-800 px-1.5 py-0.5 rounded font-semibold"
              title={t('Altına dayalı — piyasa değeri gram has altın TL fiyatı ile hesaplanır')}
            >
              {t('Altın')}
            </span>
          )}
          {isSukuk && (
            <span
              className="text-[10px] bg-emerald-100 text-emerald-700 px-1.5 py-0.5 rounded font-semibold"
              title={t('Kira Sertifikası (Sukuk) — kira getirisi öder')}
            >
              {t('Sukuk')}
            </span>
          )}
        </div>
      );
    }

    case 'qty': {
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_QTY}</span>;
      }
      const qtyText = isFund ? fmtQtyFund(h.totalQuantity) : fmtQty(h.totalQuantity);
      if (qtyText == null) return <Dash />;
      const commodityUnit = h.assetType === 'COMMODITY'
        ? getCommodityUnit(h.symbol, null)
        : null;
      // BOND (non-gold) için: TCMB konvansiyonunda miktar = TL yüz değeri — satırda "nominal değer"
      // etiketi gösterilir ki "10.000"in nakit TL değil NOMİNAL (yüz) değer olduğu belli olsun.
      // (Birim TL ama etiket "değer" — gram/USD nominalli senetlerle karışmasın.) Altın-bond
      // (adet/gram) ve diğer asset tiplerinde suffix verilmez.
      const isBondRow = h.assetType === 'BOND';
      const isGoldBondRow = isBondRow
        && (h.category === 'GOLD_INDEXED_BOND' || h.category === 'GOLD_INDEXED_LEASE_CERTIFICATE');
      const bondNominalSuffix = (isBondRow && !isGoldBondRow) ? t('nominal değer') : null;
      return (
        <span className="text-sm font-mono">
          {qtyText}
          {commodityUnit && (
            <span className="ml-1 text-[11px] text-gray-500 font-sans">{commodityUnit}</span>
          )}
          {bondNominalSuffix && (
            <span className="ml-1 text-[11px] text-gray-500 font-sans">{bondNominalSuffix}</span>
          )}
        </span>
      );
    }

    case 'avgCost': {
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_MONEY}</span>;
      }
      // BOND: averageCost = totalCost/qty (1 nominal başına). Mevcut Fiyat ve işlem "Birim Fiyat (/100)"
      // 100 nominal başına kote olduğundan, tutarlılık için non-gold bondlarda ×100 gösterilir
      // (altın bond adet/gram bazlı → /100 yok, olduğu gibi).
      const isGoldBondRow = h.assetType === 'BOND'
        && (h.category === 'GOLD_INDEXED_BOND' || h.category === 'GOLD_INDEXED_LEASE_CERTIFICATE');
      const avgCostPar = (h.assetType === 'BOND' && !isGoldBondRow) ? 100 : 1;
      const avgCostDisplay = h.averageCost != null ? h.averageCost * avgCostPar : h.averageCost;
      return (
        <span className="text-sm">
          {(isFund ? fmtFundNavPrice(h.averageCost, cur) : fmtPrice(avgCostDisplay, cur)) ?? <Dash />}
        </span>
      );
    }

    case 'currentPrice':
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_MONEY}</span>;
      }
      return (
        <span className="text-sm font-semibold">
          {(isFund ? fmtFundNavPrice(h.currentPrice, cur) : fmtPrice(h.currentPrice, cur)) ?? <Dash />}
        </span>
      );

    case 'marketValue': {
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_MONEY}</span>;
      }
      const mv = positionMarketValue(h);
      const s = fmtMoneyTwoDecimals(mv, cur);
      const isViop = h.assetType === 'FUTURE';
      return (
        <span className="text-sm font-semibold inline-flex items-center gap-1">
          {s ?? <Dash />}
          {isViop && s != null && (
            <span
              title={t('VİOP değerleri kontrat adedi × fiyat mantığıyla basitleştirilmiş olarak hesaplanır. Gerçek nominal değer sözleşme büyüklüğüne göre farklı olabilir.')}
              className="inline-flex items-center justify-center w-3.5 h-3.5 rounded-full bg-amber-100 text-amber-700 text-[9px] font-bold cursor-help"
              aria-label="VIOP bilgi"
            >i</span>
          )}
        </span>
      );
    }

    case 'totalCost':
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_MONEY}</span>;
      }
      return <span className="text-sm">{fmtMoneyTwoDecimals(h.totalCost, cur) ?? <Dash />}</span>;

    case 'unrealizedPnl': {
      const pnl = unrealizedGainLoss(h);
      return <PnlAmt value={pnl} currency={cur} valuesHidden={valuesHidden} />;
    }

    case 'unrealizedPct': {
      const pnl  = unrealizedGainLoss(h);
      const cost = num(h, 'totalCost');
      const pct  = cost != null && cost > 0 && pnl != null ? (pnl / cost) * 100 : null;
      return <PnlPct value={pct} valuesHidden={valuesHidden} />;
    }

    case 'dailyPnl': {
      const v = positionDailyGainLoss(h);
      return <PnlAmt value={v} currency={cur} valuesHidden={valuesHidden} />;
    }

    case 'dailyPct': {
      const v =
        h.dailyGainLossPercent ??
        h.dailyChangePercent ??
        h.changePercent ??
        h.returnOneDay ??
        null;
      return <PnlPct value={v} valuesHidden={valuesHidden} />;
    }

    case 'realizedPnl':
      return <PnlAmt value={h.realizedGainLoss ?? null} currency={cur} valuesHidden={valuesHidden} />;

    case 'realizedPct':
      return <PnlPct value={h.realizedGainLossPercent ?? null} valuesHidden={valuesHidden} />;

    case 'realPnl':
      if (!valuesHidden && h.realProfitLoss == null) return <ReelDash t={t} />;
      return <PnlAmt value={h.realProfitLoss ?? null} currency={cur} valuesHidden={valuesHidden} />;

    case 'realPct':
      if (!valuesHidden && h.realProfitLossPercent == null) return <ReelDash t={t} />;
      return <PnlPct value={h.realProfitLossPercent ?? null} valuesHidden={valuesHidden} />;

    case 'realPnlTry':
      if (!valuesHidden && h.realProfitLossTry == null) return <ReelDash t={t} />;
      return <PnlAmt value={h.realProfitLossTry ?? null} currency={'TL'} valuesHidden={valuesHidden} />;

    case 'realPctTry':
      if (!valuesHidden && h.realProfitLossPercentTry == null) return <ReelDash t={t} />;
      return <PnlPct value={h.realProfitLossPercentTry ?? null} valuesHidden={valuesHidden} />;

    case 'beatInflation': {
      if (valuesHidden) return <Dash />;
      const rv = h.realProfitLoss ?? h.realProfitLossTry;
      if (rv == null) return <ReelDash t={t} />;
      const n = parseFloat(rv);
      if (!Number.isFinite(n)) return <ReelDash t={t} />;
      return <BeatChip beat={n > 0} t={t} />;
    }

    case 'inflationSince': {
      if (valuesHidden) {
        return <span className="text-sm text-gray-500 tracking-widest">{MASK_PERCENT}</span>;
      }
      const v = h.inflationSincePercent;
      if (v == null) return <ReelDash t={t} />;
      const n = parseFloat(v);
      if (!Number.isFinite(n)) return <Dash />;
      const src = h.inflationSource || t('TÜFE');
      // Enflasyon nötr bir büyüklük (iyi/kötü değil) → amber, +/- renklendirme yok
      return (
        <span
          className="text-sm font-medium text-amber-600 whitespace-nowrap"
          title={t('İlk alış tarihinden bugüne birikimli enflasyon ({src})', { src })}
        >
          {formatPercentWithSuffix(n)}
          {h.inflationSource && (
            <span className="ml-1 text-[10px] font-semibold text-gray-400">{src}</span>
          )}
        </span>
      );
    }

    case 'currency':
      return <span className="text-sm text-gray-700">{h.currency ?? <Dash />}</span>;

    case 'firstBuyDate': {
      const d = h.firstBuyDate ?? h.firstTransactionDate ?? null;
      const s = fmtDate(d);
      return <span className="text-sm text-gray-700">{s ?? <Dash />}</span>;
    }

    case 'lastTxDate': {
      const s = fmtDate(h.lastTransactionDate ?? null);
      return <span className="text-sm text-gray-700">{s ?? <Dash />}</span>;
    }

    case 'volume': {
      const isFuture = String(h.assetType ?? '').toUpperCase() === 'FUTURE';
      const isBond = String(h.assetType ?? '').toUpperCase() === 'BOND';
      const s = fmtVol(h.volume ?? null);
      return (
        <span
          className="text-sm font-mono text-gray-700"
          title={
            isBond
              ? t('DİBS için borsa işlem hacmi verisi yok (EVDS gösterge serisi).')
              : isFuture
                ? t('Toplam açık pozisyon (kontrat)')
                : undefined
          }
        >
          {s ?? <Dash />}
        </span>
      );
    }

    case 'week52': {
      // Backend STOCK → StockDetail.fiftyTwoWeekHigh/Low, önce bunlara bak
      const lo = h.fiftyTwoWeekLow  ?? h.week52Low  ?? h.fiftyTwoWeekRange?.split?.('–')?.[0] ?? null;
      const hi = h.fiftyTwoWeekHigh ?? h.week52High ?? h.fiftyTwoWeekRange?.split?.('–')?.[1] ?? null;
      if (h.fiftyTwoWeekRange) return <span className="text-sm text-gray-700 whitespace-nowrap">{h.fiftyTwoWeekRange}</span>;
      if (h.week52Range)       return <span className="text-sm text-gray-700 whitespace-nowrap">{h.week52Range}</span>;
      if (lo != null && hi != null) {
        if (isFund) {
          return (
            <span className="text-sm text-gray-700 whitespace-nowrap">
              {fmtFundNavPrice(lo, null)} – {fmtFundNavPrice(hi, null)}
            </span>
          );
        }
        return (
          <span className="text-sm text-gray-700 whitespace-nowrap">
            {fmtNum(lo)} – {fmtNum(hi)}
          </span>
        );
      }
      return <Dash />;
    }


    case 'trend':
      return <TrendBadge item={h} />;

    case 'viopDirection': {
      if (h.assetType !== 'FUTURE') return <Dash />;
      const dir = String(h.viopDirection ?? 'LONG').toUpperCase();
      const isShort = dir === 'SHORT';
      return (
        <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-bold whitespace-nowrap ${
          isShort
            ? 'bg-rose-50 text-rose-700 border border-rose-200'
            : 'bg-emerald-50 text-emerald-700 border border-emerald-200'
        }`}>
          {isShort ? <TrendingDown className="w-3 h-3" /> : <TrendingUp className="w-3 h-3" />}
          {isShort ? t('SHORT') : t('LONG')}
        </span>
      );
    }

    case 'viopMargin': {
      if (h.assetType !== 'FUTURE') return <Dash />;
      if (valuesHidden) return <span className="text-sm text-gray-500 tracking-widest">{MASK_MONEY}</span>;
      const m = num(h, 'viopMarginPosted');
      if (m == null) return <Dash />;
      const mr = num(h, 'viopMarginRate');
      return (
        <span
          className="text-sm font-semibold whitespace-nowrap"
          title={mr != null ? t('Marjin oranı: %{pct}', { pct: (mr * 100).toFixed(1) }) : undefined}
        >
          {fmtMoneyTwoDecimals(m, cur) ?? <Dash />}
        </span>
      );
    }

    case 'viopNominal': {
      if (h.assetType !== 'FUTURE') return <Dash />;
      if (valuesHidden) return <span className="text-sm text-gray-500 tracking-widest">{MASK_MONEY}</span>;
      const notional = num(h, 'viopNotional');
      if (notional == null) return <Dash />;
      return (
        <span
          className="inline-flex items-center gap-1 text-sm text-slate-600 whitespace-nowrap tabular-nums cursor-help"
          title={t('Sözleşmenin tam piyasa büyüklüğü (adet × fiyat × çarpan). Sadece referans değer — portföy toplamına EKLENMEZ.')}
        >
          {fmtMoneyTwoDecimals(notional, cur) ?? <Dash />}
          <Info className="w-3 h-3 text-slate-400" />
        </span>
      );
    }

    case 'viopLeverage': {
      if (h.assetType !== 'FUTURE') return <Dash />;
      const lev = num(h, 'viopLeverage');
      if (lev == null || lev <= 0) return <Dash />;
      const mult = num(h, 'viopMultiplier');
      return (
        <span
          className="inline-flex items-center px-1.5 py-0.5 rounded-md bg-amber-50 text-amber-700 border border-amber-200 text-xs font-bold tabular-nums"
          title={mult != null ? t('Sözleşme çarpanı: {mult}', { mult }) : undefined}
        >
          {lev.toFixed(1)}x
        </span>
      );
    }

    case 'marginStatus': {
      if (h.assetType !== 'FUTURE') return <Dash />;
      const status = h.marginStatus;
      const ratio = num(h, 'marginRatio');
      if (!status) return <Dash />;
      const pct = ratio != null ? (ratio * 100) : null;
      const pctText = pct != null
        ? pct.toLocaleString('tr-TR', { minimumFractionDigits: 0, maximumFractionDigits: 1 })
        : null;
      const tooltip = pct != null
        ? t('Teminat oranı: %{pct}. Gerçek brokerlerde {brokerThreshold}% altında margin call gelir.', {
            pct: pctText,
            brokerThreshold: 25,
          })
        : t('Teminat sağlığı bilinmiyor.');

      if (status === 'HEALTHY') {
        return (
          <span
            className="inline-flex items-center gap-1.5 text-xs text-emerald-700 whitespace-nowrap"
            title={tooltip}
          >
            <span className="w-2 h-2 rounded-full bg-emerald-500 flex-shrink-0" />
            {pctText != null && <span className="tabular-nums font-semibold">%{pctText}</span>}
          </span>
        );
      }
      if (status === 'WARNING') {
        return (
          <span
            className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-bold bg-amber-50 text-amber-700 border border-amber-200 whitespace-nowrap"
            title={tooltip}
          >
            <AlertTriangle className="w-3 h-3" />
            {pctText != null ? <span className="tabular-nums">%{pctText}</span> : null}
            <span>— {t('Teminat Eriyor')}</span>
          </span>
        );
      }
      // CRITICAL
      return (
        <span
          className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-xs font-bold bg-rose-50 text-rose-700 border border-rose-300 whitespace-nowrap fp-margin-pulse"
          title={tooltip}
        >
          <AlertTriangle className="w-3 h-3" />
          {pctText != null ? <span className="tabular-nums">%{pctText}</span> : null}
          <span>— {t('TEHLİKE')}</span>
        </span>
      );
    }

    default:
      return <Dash />;
  }
}
