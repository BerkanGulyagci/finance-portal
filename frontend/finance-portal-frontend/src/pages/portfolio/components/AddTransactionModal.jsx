import { useEffect, useState, useMemo } from 'react';
import { X } from 'lucide-react';
import { addTransaction } from '../../../api/portfolioApi';
import InstrumentSearchModal from './InstrumentSearchModal';
import CommodityPriceHint from './CommodityPriceHint';
import { isYahooCommoditySymbol } from '../../../utils/commodityPriceUtils';
import {
  isGoldAssetType,
  getGoldTransactionMeta,
  resolveInstrumentTransactionPrice,
  sanitizeGoldQuantityInput,
  isValidGoldQuantity,
  formatGoldQuantity,
  goldQuantitySuffix,
} from '../utils/goldTransactionMeta';
import { useTranslation } from '../../../i18n/LanguageContext';

// ── helpers ────────────────────────────────────────────────────────────────────

function extractApiErrorMessage(err) {
  const body = err?.response?.data;
  if (!body) return err?.message || '';
  if (typeof body.message === 'string' && body.message.trim()) return body.message.trim();
  if (body.data && typeof body.data === 'object') {
    const parts = Object.values(body.data).filter(v => typeof v === 'string' && v.trim());
    if (parts.length) return parts.join(' ');
  }
  return '';
}

function fmtNum(n, dec = 2) {
  if (n == null || !Number.isFinite(n)) return '-';
  return n.toLocaleString('tr-TR', { minimumFractionDigits: dec, maximumFractionDigits: dec });
}

function fmtWithCcy(n, ccy, dec = 2) {
  const s = fmtNum(n, dec);
  return ccy ? `${s} ${ccy}` : s;
}

function guessCurrency(assetType, symbol) {
  switch (assetType) {
    case 'STOCK':
      return (symbol ?? '').toUpperCase().endsWith('.IS') ? 'TRY' : 'USD';
    case 'CRYPTO':
    case 'FX':
    case 'FUND':
    case 'BOND':
    case 'GOLD':
    case 'COMMODITY':
      return 'TRY';
    case 'FUTURE':
      return /^[A-Z0-9.=]{1,15}$/i.test(symbol ?? '') ? 'USD' : 'TRY';
    default:
      return '';
  }
}

function getShortSymbol(sym) {
  if (!sym) return '';
  const s = sym.toUpperCase();
  const paren = s.indexOf(' (');
  return paren > 0 ? s.slice(0, paren) : s;
}

/**
 * BUY: supports = tutar ile gösterilsin mi, floor = tam adede indir
 * SELL: aynı kural geçerli
 */
function assetConfig(assetType) {
  const t = String(assetType ?? '').toUpperCase();
  switch (t) {
    case 'STOCK':
      return { supports: true, floor: true };
    case 'CRYPTO':
    case 'FX':
    case 'FUND':
    case 'GOLD':
    case 'COMMODITY':
      return { supports: true, floor: false };
    case 'BOND':
    case 'FUTURE':
    default:
      return { supports: false, floor: false };
  }
}

function safePositivePrice(p) {
  const n = Number(p);
  return Number.isFinite(n) && n > 0 ? n : null;
}

function initPrice(p) {
  if (p == null || p === '') return '';
  const n = Number(p);
  return Number.isFinite(n) && n > 0 ? String(p) : '';
}

/** holdings listesinden sembol + assetType eşleşen açık pozisyon miktarını döndürür */
function findAvailableQty(holdings, symbol, assetType) {
  if (!holdings?.length || !symbol || !assetType) return null;
  const sym = symbol.toUpperCase();
  const match = holdings.find(
    h =>
      (h.symbol ?? '').toUpperCase() === sym &&
      (h.assetType ?? '') === assetType,
  );
  if (!match) return null;
  const n = parseFloat(match.totalQuantity);
  return Number.isFinite(n) && n > 0 ? n : 0;
}

/** Fon: kullanıcı genelde tutar ile işlem yapar → varsayılan "Tutar ile". Diğerleri miktar. */
function defaultInputMode(assetType) {
  return String(assetType ?? '').toUpperCase() === 'FUND' ? 'amount' : 'quantity';
}

function isFundAssetType(assetType) {
  return String(assetType ?? '').toUpperCase() === 'FUND';
}

function isBondAssetType(assetType) {
  return String(assetType ?? '').toUpperCase() === 'BOND';
}

function isFutureAssetType(assetType) {
  return String(assetType ?? '').toUpperCase() === 'FUTURE';
}

/** Vadeli: sadece pozitif tam sayı (küsurat yok). */
function sanitizeFutureContractQtyInput(raw) {
  const s = String(raw ?? '');
  if (!s) return '';
  const head = s.split(/[.,]/)[0].replace(/\D/g, '');
  if (head === '') return '';
  const n = parseInt(head, 10);
  if (!Number.isFinite(n) || n < 0) return '';
  return String(n);
}

// ── component ──────────────────────────────────────────────────────────────────

/**
 * Props:
 *   portfolioId: string
 *   portfolioName: string
 *   holdings: PortfolioHoldingResponse[]   ← açık pozisyon miktarı için
 *   onClose(): void
 *   onAdded(updatedPortfolio): void
 *   initialInstrument?: { symbol, assetType, name, price }
 */
export default function AddTransactionModal({
  portfolioId,
  portfolioName,
  holdings = [],
  onClose,
  onAdded,
  initialInstrument = null,
}) {
  const { t } = useTranslation();
  const [step, setStep] = useState(initialInstrument ? 'form' : 'search');
  const [instrument, setInstrument] = useState(initialInstrument);

  const now = new Date();
  const localNow = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);

  const [form, setForm] = useState({
    transactionType: 'BUY',
    inputMode: defaultInputMode(initialInstrument?.assetType), // 'quantity' | 'amount'
    quantity: '',
    tradeAmount: '',         // tutar ile modunda girilen değer (BUY veya SELL)
    price: initPrice(initialInstrument?.price),
    commission: '',
    transactionDate: localNow,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function set(key, val) {
    setForm(f => ({ ...f, [key]: val }));
  }

  useEffect(() => {
    if (!initialInstrument) return;
    setInstrument(initialInstrument);
    setStep('form');
    setForm(f => ({
      ...f,
      price: initPrice(initialInstrument.price),
      inputMode: defaultInputMode(initialInstrument.assetType),
    }));
  }, [initialInstrument]); // eslint-disable-line react-hooks/exhaustive-deps

  function handleInstrumentSelect(inst) {
    setInstrument(inst);
    setForm(f => {
      // Döviz: TCMB "Alış/Satış" kurum perspektifindedir →
      //  - Kullanıcı ALIŞ yapıyor (BUY)  → kurum kullanıcıya satar  → forexSelling kullanılır
      //  - Kullanıcı SATIŞ yapıyor (SELL) → kurum kullanıcıdan alır → forexBuying  kullanılır
      const isBuyNow = f.transactionType === 'BUY';
      let autoPrice = isGoldAssetType(inst.assetType)
        ? initPrice(resolveInstrumentTransactionPrice(inst, isBuyNow))
        : initPrice(inst.price);
      if (inst.assetType === 'FX') {
        autoPrice = isBuyNow
          ? initPrice(inst.fxSell)   // BUY → satış kuru
          : initPrice(inst.fxBuy);   // SELL → alış kuru
      }
      return {
        ...f,
        price: autoPrice,
        quantity: '',
        tradeAmount: '',
        inputMode: defaultInputMode(inst.assetType),
      };
    });
    setStep('form');
  }

  function handleTransactionTypeChange(t) {
    setForm(f => {
      // Döviz: işlem tipi değişince TCMB kuru kurum perspektifine göre tersine güncellenir
      let updatedPrice = f.price;
      if (instrument?.assetType === 'FX') {
        updatedPrice = t === 'BUY'
          ? initPrice(instrument.fxSell)   // BUY → satış kuru
          : initPrice(instrument.fxBuy);   // SELL → alış kuru
      } else if (isGoldAssetType(instrument?.assetType)) {
        updatedPrice = initPrice(resolveInstrumentTransactionPrice(instrument, t === 'BUY'));
      }
      return { ...f, transactionType: t, tradeAmount: '', quantity: '', price: updatedPrice };
    });
  }

  // ── hesaplamalar ────────────────────────────────────────────────────────────

  const cfg = useMemo(() => assetConfig(instrument?.assetType), [instrument?.assetType]);
  const isFund = isFundAssetType(instrument?.assetType);
  const isBond = isBondAssetType(instrument?.assetType);
  const isFuture = isFutureAssetType(instrument?.assetType);
  const isGold = isGoldAssetType(instrument?.assetType);
  const goldMeta = useMemo(
    () => (isGold ? getGoldTransactionMeta(instrument?.symbol) : null),
    [isGold, instrument?.symbol],
  );
  const useQtyFloor = cfg.floor || (isGold && goldMeta?.floorQty);

  const currency = useMemo(
    () => instrument?.currency || guessCurrency(instrument?.assetType, instrument?.symbol),
    [instrument?.currency, instrument?.assetType, instrument?.symbol],
  );

  const symShort = getShortSymbol(instrument?.symbol);

  const price      = safePositivePrice(form.price);
  const commission = Math.max(0, parseFloat(form.commission) || 0);
  const isBuy      = form.transactionType === 'BUY';
  const isAmountMode = form.inputMode === 'amount';

  /** Eldeki açık pozisyon (SELL validasyonu için) */
  const availableQty = useMemo(
    () => findAvailableQty(holdings, instrument?.symbol, instrument?.assetType),
    [holdings, instrument?.symbol, instrument?.assetType],
  );

  /**
   * "Tutar ile" modunda hesaplamalar — BUY ve SELL için ayrı mantık.
   *
   * BUY (FUND hariç):
   *   tradeAmount = toplam ödeme (komisyon dahil)
   *   investable  = tradeAmount - commission
   *   quantity    = investable / price
   *
   * BUY (FUND):
   *   quantity = investmentAmount / unitPrice (komisyon pay adedinden düşülmez)
   *
   * SELL:
   *   tradeAmount = brüt satış tutarı (hedeflenen gelir)
   *   quantity    = tradeAmount / price   (komisyon quantity'ye yansımaz)
   *   netIncome   = tradeAmount - commission
   */
  const amountCalc = useMemo(() => {
    if (!isAmountMode || !price) return null;
    const amt = parseFloat(form.tradeAmount);
    if (!amt || amt <= 0) return null;

    // Komisyon >= tutar kontrolü
    if (commission > 0 && commission >= amt) {
      return {
        qty: 0, isZero: true,
        commissionOverflow: true,
        isBuy,
      };
    }

    if (isBuy) {
      // FON: quantity = yatırım tutarı / birim pay (komisyon ayrıca, adede bölünmez)
      if (isFund) {
        const rawQty = amt / price;
        const used = rawQty * price;
        const totalOut = used + commission;
        return {
          qty: rawQty, rawQty,
          used,
          remaining: 0,
          totalPayment: totalOut,
          isZero: rawQty <= 0,
          commissionOverflow: false,
          isBuy: true,
        };
      }
      // BUY: komisyon yatırım tutarından düşülür
      const investable = amt - commission;
      const rawQty = investable / price;
      if (useQtyFloor) {
        const qty = Math.floor(rawQty);
        const used = qty * price;
        const remaining = investable - used;
        return {
          qty, rawQty, used, remaining,
          totalPayment: amt,
          isZero: qty === 0,
          commissionOverflow: false,
          isBuy: true,
        };
      }
      return {
        qty: rawQty, rawQty,
        used: rawQty * price,
        remaining: 0,
        totalPayment: amt,
        isZero: rawQty <= 0,
        commissionOverflow: false,
        isBuy: true,
      };
    } else {
      // SELL: quantity = tradeAmount / price (komisyon ayrı)
      const rawQty = amt / price;
      const exceedsAvailable =
        availableQty != null && rawQty > availableQty + 1e-10;

      if (useQtyFloor) {
        const qty = Math.floor(rawQty);
        const grossSell = qty * price;
        const remaining = amt - grossSell;
        const netIncome = grossSell - commission;
        return {
          qty, rawQty,
          grossSell, remaining,
          netIncome,
          isZero: qty === 0,
          commissionOverflow: false,
          exceedsAvailable: availableQty != null && qty > availableQty,
          availableQty,
          isBuy: false,
        };
      }
      return {
        qty: rawQty, rawQty,
        grossSell: amt,
        remaining: 0,
        netIncome: amt - commission,
        isZero: rawQty <= 0,
        commissionOverflow: false,
        exceedsAvailable,
        availableQty,
        isBuy: false,
      };
    }
  }, [isAmountMode, form.tradeAmount, price, commission, isBuy, useQtyFloor, availableQty, isFund]);

  /** Altın adet bazlı: küsuratlı miktar */
  const goldPieceQtyInvalid = useMemo(() => {
    if (!isGold || isAmountMode || !goldMeta?.floorQty) return false;
    const q = parseFloat(form.quantity);
    if (!Number.isFinite(q) || q <= 0) return false;
    return !Number.isInteger(q);
  }, [isGold, isAmountMode, goldMeta?.floorQty, form.quantity]);

  /** Altın + SATIŞ + miktar modu */
  const quantitySellExceedsGold = useMemo(() => {
    if (!isGold || isBuy || isAmountMode || availableQty == null || !price) return false;
    const q = parseFloat(form.quantity);
    if (!Number.isFinite(q) || q <= 0) return false;
    return q > availableQty + 1e-10;
  }, [isGold, isBuy, isAmountMode, availableQty, form.quantity, price]);

  /** DİBS / Tahvil: SATIŞ + miktar modu — miktar eldeki nominali aşamaz */
  const quantitySellExceedsBond = useMemo(() => {
    if (!isBond || isBuy || isAmountMode || availableQty == null || !price) return false;
    const q = parseFloat(form.quantity);
    if (!Number.isFinite(q) || q <= 0) return false;
    return q > availableQty + 1e-10;
  }, [isBond, isBuy, isAmountMode, availableQty, form.quantity, price]);

  /** Fon + SATIŞ + miktar modu: girilen pay eldeki payı aşamaz */
  const quantitySellExceedsFund = useMemo(() => {
    if (!isFund || isBuy || isAmountMode || availableQty == null || !price) return false;
    const q = parseFloat(form.quantity);
    if (!Number.isFinite(q) || q <= 0) return false;
    return q > availableQty + 1e-10;
  }, [isFund, isBuy, isAmountMode, availableQty, form.quantity, price]);

  /** "Miktar ile" modunda özet — BUY satır gelirleri veya SELL net geliri */
  const quantityModeTotal = useMemo(() => {
    if (isAmountMode) return null;
    const qty = parseFloat(form.quantity);
    if (!qty || !price) return null;
    const gross = qty * price;
    return isBuy
      ? { gross, total: gross + commission }
      : { gross, netIncome: gross - commission };
  }, [isAmountMode, form.quantity, price, commission, isBuy]);

  // ── submit ──────────────────────────────────────────────────────────────────

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');

    if (!price) {
      setError(
        isFund ? t("Birim pay değeri 0'dan büyük olmalıdır.")
          : isBond ? t("Gösterge değeri 0'dan büyük olmalıdır.")
            : t("Fiyat 0'dan büyük olmalıdır."),
      );
      return;
    }

    let finalQuantity;

    if (isAmountMode) {
      if (!amountCalc) {
        setError(isBuy ? t("Yatırım tutarı 0'dan büyük olmalıdır.") : t("Satış tutarı 0'dan büyük olmalıdır."));
        return;
      }
      if (amountCalc.commissionOverflow) {
        setError(t('Komisyon tutar değerinden büyük veya eşit olamaz.'));
        return;
      }
      if (amountCalc.isZero) {
        setError(
          isBuy
            ? t('Bu tutar ile en az 1 adet alınamıyor.')
            : t('Bu tutar ile en az 1 adet satılamıyor.'),
        );
        return;
      }
      if (amountCalc.exceedsAvailable) {
        const qtyFmt = isGold && goldMeta
          ? `${formatGoldQuantity(amountCalc.qty, goldMeta)} ${goldQuantitySuffix(goldMeta)}`
          : `${fmtNum(amountCalc.qty, useQtyFloor ? 0 : 8)} ${symShort}`;
        const availFmt = isGold && goldMeta
          ? `${formatGoldQuantity(availableQty, goldMeta)} ${goldQuantitySuffix(goldMeta)}`
          : `${fmtNum(availableQty, useQtyFloor ? 0 : 8)} ${symShort}`;
        setError(t('Satış miktarı ({qty}) eldeki miktarı ({avail}) aşıyor.', { qty: qtyFmt, avail: availFmt }));
        return;
      }
      finalQuantity = amountCalc.qty;
    } else {
      const q = parseFloat(form.quantity);
      if (!q || q <= 0) {
        setError(
          isFund ? t("Pay miktarı 0'dan büyük olmalıdır.")
            : isFuture ? t("Kontrat adedi 0'dan büyük tam sayı olmalıdır.")
              : isGold && goldMeta
                ? t("{label} 0'dan büyük olmalıdır.", { label: t(goldMeta.quantityLabel) })
                : t("Miktar 0'dan büyük olmalıdır."),
        );
        return;
      }
      if (isFuture) {
        if (!Number.isInteger(q)) {
          setError(t('Kontrat adedi tam sayı olmalıdır; küsuratlı kontrat girilemez.'));
          return;
        }
      }
      if (isGold && goldMeta && !isValidGoldQuantity(q, goldMeta.floorQty)) {
        setError(t('Bu altın türünde miktar tam adet olmalıdır.'));
        return;
      }
      if (quantitySellExceedsBond) {
        setError(t('Satış miktarı eldeki miktarı aşamaz.'));
        return;
      }
      if (quantitySellExceedsFund) {
        setError(t('Satış miktarı eldeki pay miktarını aşamaz.'));
        return;
      }
      if (quantitySellExceedsGold) {
        setError(t('Satış miktarı eldeki miktarı aşamaz.'));
        return;
      }
      finalQuantity = isFuture ? parseInt(String(q), 10) : q;
    }

    setLoading(true);
    try {
      const pricePayload =
        isFund && Number.isFinite(price) ? Math.round(price * 1e6) / 1e6 : price;
      const updated = await addTransaction(portfolioId, {
        symbol: instrument.symbol,
        assetType: instrument.assetType,
        transactionType: form.transactionType,
        quantity: finalQuantity,
        price: pricePayload,
        commission,
        transactionDate: new Date(form.transactionDate).toISOString().replace('Z', ''),
      });
      onAdded(updated);
      onClose();
    } catch (err) {
      setError(extractApiErrorMessage(err) || t('İşlem eklenemedi.'));
    } finally {
      setLoading(false);
    }
  }

  // ── adım 1: enstrüman arama ─────────────────────────────────────────────────

  if (step === 'search') {
    return (
      <InstrumentSearchModal
        portfolioName={portfolioName}
        onSelect={handleInstrumentSelect}
        onClose={onClose}
      />
    );
  }

  // ── adım 2: form ─────────────────────────────────────────────────────────────

  const hasPriceAutoFill = instrument?.price != null;

  const submitDisabled =
    loading ||
    quantitySellExceedsFund ||
    quantitySellExceedsGold ||
    quantitySellExceedsBond ||
    goldPieceQtyInvalid ||
    (isAmountMode && (
      amountCalc?.isZero === true ||
      amountCalc?.commissionOverflow === true ||
      amountCalc?.exceedsAvailable === true
    ));

  // Özet kutusu rengi: hata varsa kırmızı, normal ise koyu gri
  const summaryHasError =
    quantitySellExceedsFund ||
    quantitySellExceedsGold ||
    quantitySellExceedsBond ||
    goldPieceQtyInvalid ||
    (isAmountMode && amountCalc && (
      amountCalc.isZero || amountCalc.commissionOverflow || amountCalc.exceedsAvailable
    ));

  function formatSummaryQty(qty) {
    if (isGold && goldMeta) {
      const unit = goldQuantitySuffix(goldMeta);
      return unit ? `${formatGoldQuantity(qty, goldMeta)} ${unit}` : formatGoldQuantity(qty, goldMeta);
    }
    if (useQtyFloor) return `${fmtNum(qty, 0)} ${t('adet')}`;
    return `${fmtNum(qty, 8).replace(/\.?0+$/, '')} ${symShort}`;
  }

  return (
    <div className="fixed inset-0 bg-black/70 z-50 flex items-center justify-center p-4">
      <div className="bg-[#1a1f2e] rounded-2xl shadow-2xl w-full max-w-md text-white">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#3a4155]">
          <div>
            <h2 className="font-bold text-lg">{t('İşlem Ekle')}</h2>
            <p className="text-sm text-gray-400">
              <span className="font-bold text-white">{instrument?.symbol}</span>
              {instrument?.name && instrument.name !== instrument.symbol && (
                <span className="ml-1 text-gray-500">· {instrument.name}</span>
              )}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button type="button" onClick={() => setStep('search')} className="text-xs text-[#4a6cf7] hover:underline">
              {t('Değiştir')}
            </button>
            <button type="button" onClick={onClose} className="text-gray-400 hover:text-white transition-colors">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">

          {/* BUY / SELL */}
          <div className="grid grid-cols-2 gap-2">
            {['BUY', 'SELL'].map(txType => (
              <button
                key={txType}
                type="button"
                onClick={() => handleTransactionTypeChange(txType)}
                className={`py-2.5 rounded-xl text-sm font-bold transition-all ${
                  form.transactionType === txType
                    ? txType === 'BUY' ? 'bg-emerald-500 text-white' : 'bg-rose-500 text-white'
                    : 'bg-[#252b3b] text-gray-400 hover:bg-[#2f3650]'
                }`}
              >
                {txType === 'BUY' ? `📈 ${t('Alış')}` : `📉 ${t('Satış')}`}
              </button>
            ))}
          </div>

          {/* Giriş tipi — desteklenen asset tipleri için BUY ve SELL'de göster */}
          {cfg.supports && (
            <div className="flex rounded-xl overflow-hidden border border-[#3a4155]">
              {[
                {
                  value: 'quantity',
                  label: isFund
                    ? (isBuy ? t('Pay Miktarı ile') : t('Pay Miktarı ile satış'))
                    : t('Miktar ile'),
                },
                {
                  value: 'amount',
                  label: isBuy ? t('Tutar ile') : t('Satış Tutarı ile'),
                },
              ].map(opt => (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => set('inputMode', opt.value)}
                  className={`flex-1 py-2 text-xs font-bold transition-all ${
                    form.inputMode === opt.value
                      ? 'bg-[#4a6cf7] text-white'
                      : 'bg-[#252b3b] text-gray-400 hover:bg-[#2f3650]'
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          )}

          {/* Ana girişler */}
          <div className="grid grid-cols-2 gap-3">
            {/* Sol: miktar veya tutar */}
            <div>
              {isAmountMode ? (
                <>
                  <label className="block text-xs font-semibold text-gray-400 mb-1.5">
                    {isBuy
                      ? `${t('Yatırım Tutarı')}${currency ? ` (${currency})` : ''} *`
                      : `${t('Satış Tutarı')}${currency ? ` (${currency})` : ''} *`}
                  </label>
                  <input
                    type="number" step="any" min="0"
                    value={form.tradeAmount}
                    onChange={e => set('tradeAmount', e.target.value)}
                    placeholder="0.00"
                    className="w-full bg-[#252b3b] border border-[#3a4155] rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-[#4a6cf7]"
                  />
                </>
              ) : (
                <>
                  <label className="block text-xs font-semibold text-gray-400 mb-1.5">
                    {isFund
                      ? `${t('Pay Miktarı')} *`
                      : isFuture
                        ? `${t('Kontrat Adedi')} *`
                        : isGold && goldMeta
                          ? `${t(goldMeta.quantityLabel)} *`
                          : `${t('Miktar')} *`}
                  </label>
                  <input
                    type="number"
                    step={isFuture || (isGold && goldMeta?.floorQty) ? 1 : 'any'}
                    min={0}
                    value={form.quantity}
                    onChange={e => {
                      if (isFuture) {
                        set('quantity', sanitizeFutureContractQtyInput(e.target.value));
                      } else if (isGold && goldMeta) {
                        set('quantity', sanitizeGoldQuantityInput(e.target.value, goldMeta.floorQty));
                      } else {
                        set('quantity', e.target.value);
                      }
                    }}
                    placeholder={isFuture || (isGold && goldMeta?.floorQty) ? '1' : '0.00'}
                    className="w-full bg-[#252b3b] border border-[#3a4155] rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-[#4a6cf7]"
                  />
                </>
              )}
            </div>

            {/* Sağ: fiyat */}
            <div>
              <label className="block text-xs font-semibold text-gray-400 mb-1.5">
                {isFund
                  ? `${t('Birim Pay Değeri')} *`
                  : isBond
                    ? `${t('Gösterge Değeri')} *`
                    : isGold && goldMeta
                      ? `${t(goldMeta.priceLabel)} *`
                      : `${t('Fiyat')} *`}
                {hasPriceAutoFill && <span className="ml-1 text-emerald-400 font-normal">{t('otomatik')}</span>}
              </label>
              <input
                type="number" step="any" min="0"
                value={form.price}
                onChange={e => set('price', e.target.value)}
                placeholder="0.00"
                className={`w-full bg-[#252b3b] border rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-[#4a6cf7] ${
                  hasPriceAutoFill ? 'border-emerald-500/50' : 'border-[#3a4155]'
                }`}
              />
              {instrument?.assetType === 'COMMODITY'
                && isYahooCommoditySymbol(instrument?.symbol)
                && instrument?.commoditySpot && (
                <CommodityPriceHint
                  spot={instrument.commoditySpot}
                  unit={instrument.commoditySpot?.unit}
                  className="mt-1.5"
                />
              )}
            </div>
          </div>

          {isGold && goldMeta?.infoNote && (
            <p className="text-[11px] text-gray-500 -mt-2 leading-snug">{t(goldMeta.infoNote)}</p>
          )}

          {/* FX için kur açıklaması — kurum perspektifi nedeniyle çapraz kullanım */}
          {instrument?.assetType === 'FX' && (
            <p className="text-[11px] text-gray-500 -mt-2 leading-snug">
              {isBuy
                ? t('Döviz alış işleminde TCMB satış kuru kullanılır (kurum dövizi size satar).')
                : t('Döviz satış işleminde TCMB alış kuru kullanılır (kurum dövizinizi alır).')}
            </p>
          )}

          {/* Komisyon */}
          <div>
            <label className="block text-xs font-semibold text-gray-400 mb-1.5">
              {isGold || isBond ? t('Komisyon / Masraf') : t('Komisyon')}
            </label>
            <input
              type="number" step="any" min="0"
              value={form.commission}
              onChange={e => set('commission', e.target.value)}
              placeholder={t('0.00 (isteğe bağlı)')}
              className="w-full bg-[#252b3b] border border-[#3a4155] rounded-xl px-4 py-2.5 text-sm text-white placeholder-gray-600 focus:outline-none focus:border-[#4a6cf7]"
            />
          </div>

          {/* Tarih */}
          <div>
            <label className="block text-xs font-semibold text-gray-400 mb-1.5">{t('İşlem Tarihi *')}</label>
            <input
              type="datetime-local"
              value={form.transactionDate}
              onChange={e => set('transactionDate', e.target.value)}
              className="w-full bg-[#252b3b] border border-[#3a4155] rounded-xl px-4 py-2.5 text-sm text-white focus:outline-none focus:border-[#4a6cf7]"
            />
          </div>

          {/* ── Özet kutusu ── */}
          {isAmountMode && amountCalc ? (
            <div className={`rounded-xl p-3 text-xs space-y-1.5 ${
              summaryHasError
                ? 'bg-rose-500/10 border border-rose-500/30'
                : 'bg-[#252b3b]'
            }`}>
              {amountCalc.commissionOverflow ? (
                <p className="text-rose-400 font-semibold">
                  {t('Komisyon tutar değerinden büyük veya eşit olamaz.')}
                </p>
              ) : amountCalc.exceedsAvailable ? (
                <p className="text-rose-400 font-semibold">
                  {t('Satış miktarı eldeki miktarı aşıyor.')}
                  {availableQty != null && ` (${t('Eldeki:')} ${formatSummaryQty(availableQty)})`}
                </p>
              ) : amountCalc.isZero ? (
                <p className="text-rose-400 font-semibold">
                  {isBuy
                    ? (isGold && goldMeta?.floorQty
                      ? t('Bu tutar ile en az 1 adet alınamıyor.')
                      : t('Bu tutar ile işlem yapılamıyor.'))
                    : (isGold && goldMeta?.floorQty
                      ? t('Bu tutar ile en az 1 adet satılamıyor.')
                      : t('Bu tutar ile işlem yapılamıyor.'))}
                </p>
              ) : isBuy ? (
                /* BUY özeti */
                <>
                  {isGold ? (
                    <>
                      <SummaryRow label={t('Kullanılan tutar')} value={fmtWithCcy(amountCalc.used, currency)} />
                      <SummaryRow label={t('Hesaplanan miktar')} value={formatSummaryQty(amountCalc.qty)} highlight />
                      {commission > 0 && (
                        <SummaryRow label={t('Komisyon / Masraf')} value={fmtWithCcy(commission, currency)} />
                      )}
                      <SummaryRow
                        label={t('Toplam ödeme')}
                        value={fmtWithCcy(
                          amountCalc.totalPayment != null ? amountCalc.totalPayment : parseFloat(form.tradeAmount),
                          currency,
                        )}
                        highlight
                      />
                    </>
                  ) : (
                    <>
                      <SummaryRow label={t('Hesaplanan miktar')} value={formatSummaryQty(amountCalc.qty)} highlight />
                      <SummaryRow label={t('Kullanılan tutar')} value={fmtWithCcy(amountCalc.used, currency)} />
                      {commission > 0 && <SummaryRow label={t('Komisyon')} value={fmtWithCcy(commission, currency)} />}
                      {useQtyFloor && (
                        <SummaryRow
                          label={t('Kalan nakit')}
                          value={fmtWithCcy(amountCalc.remaining, currency)}
                          dim={amountCalc.remaining <= 0}
                        />
                      )}
                      <SummaryRow
                        label={t('Toplam ödeme')}
                        value={fmtWithCcy(
                          amountCalc.totalPayment != null ? amountCalc.totalPayment : parseFloat(form.tradeAmount),
                          currency,
                        )}
                        highlight
                      />
                    </>
                  )}
                </>
              ) : (
                /* SELL özeti */
                <>
                  <SummaryRow label={t('Satış tutarı')} value={fmtWithCcy(amountCalc.grossSell, currency)} />
                  <SummaryRow label={t('Hesaplanan miktar')} value={formatSummaryQty(amountCalc.qty)} highlight />
                  {commission > 0 && (
                    <SummaryRow
                      label={isGold ? t('Komisyon / Masraf') : t('Komisyon')}
                      value={fmtWithCcy(commission, currency)}
                    />
                  )}
                  <SummaryRow label={t('Tahmini net gelir')} value={fmtWithCcy(amountCalc.netIncome, currency)} highlight />
                  {availableQty != null && (
                    <SummaryRow label={t('Eldeki miktar')} value={formatSummaryQty(availableQty)} dim />
                  )}
                </>
              )}
            </div>
          ) : !isAmountMode && (quantityModeTotal != null || quantitySellExceedsFund || quantitySellExceedsGold || quantitySellExceedsBond || goldPieceQtyInvalid) ? (
            <div className={`rounded-xl p-3 text-xs space-y-1.5 ${
              summaryHasError && !quantityModeTotal
                ? 'bg-rose-500/10 border border-rose-500/30'
                : 'bg-[#252b3b]'
            }`}>
              {goldPieceQtyInvalid && (
                <p className="text-rose-400 font-semibold">{t('Bu altın türünde miktar tam adet olmalıdır.')}</p>
              )}
              {quantitySellExceedsFund && (
                <p className="text-rose-400 font-semibold">
                  {t('Satış miktarı eldeki pay miktarını aşamaz.')}
                  {availableQty != null && ` (${t('Eldeki:')} ${fmtNum(availableQty, 8).replace(/\.?0+$/, '')} ${t('pay')})`}
                </p>
              )}
              {quantitySellExceedsGold && (
                <p className="text-rose-400 font-semibold">
                  {t('Satış miktarı eldeki miktarı aşamaz.')}
                  {availableQty != null && ` (${t('Eldeki:')} ${formatSummaryQty(availableQty)})`}
                </p>
              )}
              {quantitySellExceedsBond && (
                <p className="text-rose-400 font-semibold">
                  {t('Satış miktarı eldeki miktarı aşamaz.')}
                  {availableQty != null && ` (${t('Eldeki:')} ${fmtNum(availableQty, 8).replace(/\.?0+$/, '')})`}
                </p>
              )}
              {quantityModeTotal != null && !quantitySellExceedsFund && !quantitySellExceedsGold && !quantitySellExceedsBond && !goldPieceQtyInvalid && (
                <>
                  {isBuy ? (
                    <>
                      <SummaryRow
                        label={isGold && goldMeta ? t(goldMeta.quantityLabel.replace(' *', '')) : t('Miktar')}
                        value={formatSummaryQty(parseFloat(form.quantity))}
                      />
                      <SummaryRow
                        label={
                          isBond
                            ? t('Gösterge Değeri')
                            : isGold && goldMeta
                              ? t(goldMeta.priceLabel.replace(' *', ''))
                              : t('Fiyat')
                        }
                        value={fmtWithCcy(price, currency)}
                      />
                      {(commission > 0 || isBond) && (
                        <SummaryRow
                          label={isGold || isBond ? t('Komisyon / Masraf') : t('Komisyon')}
                          value={fmtWithCcy(commission, currency)}
                        />
                      )}
                      <SummaryRow label={t('Toplam ödeme')} value={fmtWithCcy(quantityModeTotal.total, currency)} highlight />
                    </>
                  ) : (
                    <>
                      <SummaryRow
                        label={isGold && goldMeta ? t(goldMeta.quantityLabel.replace(' *', '')) : t('Miktar')}
                        value={formatSummaryQty(parseFloat(form.quantity))}
                      />
                      <SummaryRow
                        label={
                          isBond
                            ? t('Gösterge Değeri')
                            : isGold && goldMeta
                              ? t(goldMeta.priceLabel.replace(' *', ''))
                              : t('Fiyat')
                        }
                        value={fmtWithCcy(price, currency)}
                      />
                      {(commission > 0 || isBond) && (
                        <SummaryRow
                          label={isGold || isBond ? t('Komisyon / Masraf') : t('Komisyon')}
                          value={fmtWithCcy(commission, currency)}
                        />
                      )}
                      <SummaryRow
                        label={isBond ? t('Net Tahsilat') : t('Tahmini net gelir')}
                        value={fmtWithCcy(quantityModeTotal.netIncome, currency)}
                        highlight
                      />
                    </>
                  )}
                </>
              )}
            </div>
          ) : null}

          {/* Hata */}
          {error && (
            <p className="text-rose-400 text-sm bg-rose-500/10 px-3 py-2 rounded-lg">{error}</p>
          )}

          {/* Butonlar */}
          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-[#3a4155] rounded-xl text-sm font-semibold text-gray-400 hover:bg-[#252b3b] transition-colors"
            >
              {t('İptal')}
            </button>
            <button
              type="submit"
              disabled={submitDisabled}
              className={`flex-1 text-white px-4 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-50 transition-colors ${
                isBuy ? 'bg-emerald-500 hover:bg-emerald-600' : 'bg-rose-500 hover:bg-rose-600'
              }`}
            >
              {loading ? t('Ekleniyor…') : isBuy ? t('Alış Ekle') : t('Satış Ekle')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

// ── yardımcı bileşen ───────────────────────────────────────────────────────────

function SummaryRow({ label, value, highlight = false, dim = false }) {
  return (
    <div className="flex justify-between items-center">
      <span className="text-gray-400">{label}</span>
      <span className={`font-bold ${
        highlight ? 'text-white text-sm' : dim ? 'text-gray-600' : 'text-gray-200'
      }`}>
        {value}
      </span>
    </div>
  );
}
