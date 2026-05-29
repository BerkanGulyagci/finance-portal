import { useEffect, useState, useMemo } from 'react';
import { X, ArrowLeftRight, TrendingUp, TrendingDown } from 'lucide-react';
import { addTransaction, getPriceAtDate } from '../../../api/portfolioApi';
import { getViopChart } from '../../../api/marketApi';
import InstrumentSearchModal from './InstrumentSearchModal';
import CommodityPriceHint from './CommodityPriceHint';
import DateTimeField from './DateTimeField';
import { isYahooCommoditySymbol } from '../../../utils/commodityPriceUtils';
import { getCommodityUnit } from '../utils/commodityUnit';
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
import TransactionSummary from './TransactionSummary';
import {
  extractApiErrorMessage,
  fmtNum,
  guessCurrency,
  getShortSymbol,
  assetConfig,
  safePositivePrice,
  initPrice,
  findAvailableQty,
  defaultInputMode,
  isFundAssetType,
  isBondAssetType,
  isEurobondInstrument,
  isFutureAssetType,
  sanitizeFutureContractQtyInput,
  currencySymbol,
  formatGroupedInput,
  parseGroupedInput,
} from '../utils/transactionFormUtils';

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
  // "Değiştir" ile arama ekranına dönünce son seçilen enstrüman türü hatırlanır
  // (default STOCK'a düşmesin). initialInstrument varsa onun türü, yoksa STOCK.
  const [lastUsedAssetType, setLastUsedAssetType] = useState(
    initialInstrument?.assetType || 'STOCK'
  );

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

  // Fiyat otomatik doldurma durumları (işlem tarihine göre)
  const [priceLoading, setPriceLoading] = useState(false);   // tarihsel fiyat çekiliyor
  const [priceNotFound, setPriceNotFound] = useState(false); // seçilen tarih için veri yok
  const [priceAuto, setPriceAuto] = useState(initialInstrument?.price != null); // fiyat otomatik mi
  const [futureMinDate, setFutureMinDate] = useState(null); // VİOP kontratının ilk işlem günü (YYYY-MM-DD)

  function set(key, val) {
    setForm(f => ({ ...f, [key]: val }));
  }

  const todayStr = localNow.slice(0, 10);

  // Eurobond: fiyat döviz cinsinden → otomatik doldurma yok, kullanıcı TL elle girer.
  const isEurobond = isEurobondInstrument(instrument);

  useEffect(() => {
    if (!initialInstrument) return;
    setInstrument(initialInstrument);
    setStep('form');
    setForm(f => ({
      ...f,
      // Eurobond fiyatı USD/EUR — TL elle girileceği için ön-doldurma yapma.
      price: isEurobondInstrument(initialInstrument) ? '' : initPrice(initialInstrument.price),
      inputMode: defaultInputMode(initialInstrument.assetType),
    }));
  }, [initialInstrument]); // eslint-disable-line react-hooks/exhaustive-deps

  function handleInstrumentSelect(inst) {
    setInstrument(inst);
    if (inst?.assetType) {
      setLastUsedAssetType(inst.assetType);
    }
    const eurobondSel = isEurobondInstrument(inst);
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
      // Eurobond: kote döviz cinsinden → ön-doldurma yapma, TL elle girilecek.
      if (eurobondSel) {
        autoPrice = '';
      }
      return {
        ...f,
        price: autoPrice,
        quantity: '',
        tradeAmount: '',
        inputMode: defaultInputMode(inst.assetType),
      };
    });
    setPriceAuto(!eurobondSel);
    setPriceNotFound(false);
    setStep('form');
  }

  function handleTransactionTypeChange(txType) {
    setForm(f => {
      // Geçmiş tarih seçiliyse fiyat tarihsel kapanıştır (alış/satış ayrımı yok) → korunur.
      const dateOnly = (f.transactionDate || '').slice(0, 10);
      const isPast = dateOnly && dateOnly < todayStr;
      let updatedPrice = f.price;
      if (!isPast) {
        // Bugün/ileri: Döviz/Altın için kurum perspektifli alış/satış spot fiyatı
        if (instrument?.assetType === 'FX') {
          updatedPrice = txType === 'BUY'
            ? initPrice(instrument.fxSell)   // BUY → satış kuru
            : initPrice(instrument.fxBuy);   // SELL → alış kuru
        } else if (isGoldAssetType(instrument?.assetType)) {
          updatedPrice = initPrice(resolveInstrumentTransactionPrice(instrument, txType === 'BUY'));
        }
      }
      return { ...f, transactionType: txType, tradeAmount: '', quantity: '', price: updatedPrice };
    });
  }

  // İşlem tarihine göre fiyatı otomatik doldur (geçmiş tarihte tarihsel kapanış).
  // Bugün/ileri tarihte güncel spot fiyat (instrument-based) korunur, AMA spot fiyat
  // arama listesinde yoksa (PLATINUM/PALLADIUM gibi) bugün için de /price-at çağrılır.
  // Eurobond: backend /price-at TL FX-converted değer döndürür (Model 1: kote × o günün TCMB kuru) →
  // hem geçmiş hem bugün için autofill TL olarak çalışır (USD/EUR fark etmez, backend FX'i bulur).
  useEffect(() => {
    if (step !== 'form' || !instrument) return undefined;
    const dateOnly = (form.transactionDate || '').slice(0, 10);
    if (!dateOnly) return undefined;
    // Bugün/ileri tarih: spot fiyatı instrument'tan geliyorsa erken çık (eski davranış).
    // Aksi halde (eurobond ya da spot fiyat yok ise) /price-at fallback'ine düşeriz.
    const hasSpotPrice = instrument.price != null && Number(instrument.price) > 0;
    if (dateOnly >= todayStr && !isEurobond && hasSpotPrice) {
      setPriceNotFound(false);
      setPriceLoading(false);
      return undefined;
    }
    let cancelled = false;
    setPriceLoading(true);
    setPriceNotFound(false);
    const handle = setTimeout(() => {
      getPriceAtDate(instrument.assetType, instrument.symbol, dateOnly)
        .then(res => {
          if (cancelled) return;
          if (res?.found && res.price != null) {
            set('price', String(res.price));
            setPriceAuto(true);
            setPriceNotFound(false);
          } else {
            // Bu tarih için veri yok → çelişki olmasın diye eski (spot) fiyatı temizle
            set('price', '');
            setPriceAuto(false);
            setPriceNotFound(true);
          }
        })
        .catch(() => { if (!cancelled) setPriceNotFound(true); })
        .finally(() => { if (!cancelled) setPriceLoading(false); });
    }, 450);
    return () => { cancelled = true; clearTimeout(handle); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [instrument, form.transactionDate, step]);

  // VİOP: kontratın ilk işlem gününü bul → o tarihten öncesi seçilemesin (kontrat o gün açıldı).
  useEffect(() => {
    if (step !== 'form' || !instrument || !isFutureAssetType(instrument.assetType)) {
      setFutureMinDate(null);
      return undefined;
    }
    let cancelled = false;
    getViopChart(instrument.symbol, 'ONE_YEAR')
      .then(resp => {
        if (cancelled) return;
        const arr = Array.isArray(resp?.data) ? resp.data : [];
        let earliest = null;
        for (const p of arr) {
          const d = p?.timestamp ? new Date(p.timestamp)
            : (p?.dateTime ? new Date(p.dateTime) : null);
          if (d && !Number.isNaN(d.getTime())) {
            const iso = new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
            if (earliest == null || iso < earliest) earliest = iso;
          }
        }
        setFutureMinDate(earliest);
      })
      .catch(() => { if (!cancelled) setFutureMinDate(null); });
    return () => { cancelled = true; };
  }, [instrument, step]);

  // ── hesaplamalar ────────────────────────────────────────────────────────────

  const cfg = useMemo(() => assetConfig(instrument?.assetType), [instrument?.assetType]);
  const isFund = isFundAssetType(instrument?.assetType);
  const isBond = isBondAssetType(instrument?.assetType);
  const isFuture = isFutureAssetType(instrument?.assetType);
  const isGold = isGoldAssetType(instrument?.assetType);
  const isCommodity = instrument?.assetType === 'COMMODITY';
  const commodityUnit = useMemo(
    () => (isCommodity ? getCommodityUnit(instrument?.symbol, instrument?.commoditySpot) : null),
    [isCommodity, instrument?.symbol, instrument?.commoditySpot],
  );
  const goldMeta = useMemo(
    () => (isGold ? getGoldTransactionMeta(instrument?.symbol) : null),
    [isGold, instrument?.symbol],
  );
  const useQtyFloor = cfg.floor || (isGold && goldMeta?.floorQty);

  const currency = useMemo(
    () => {
      // Eurobond portföye TL olarak eklenir → modalda her zaman ₺/TRY.
      if (isEurobond) return 'TRY';
      // FX: fiyat alanı "1 foreign = X TRY" oranıdır (örn. EUR fiyatı = 53,21 ₺ per EUR).
      // Toplam ödeme de TL. instrument.currency='EUR' gelse bile modal'da ₺ gösterilir.
      if (instrument?.assetType === 'FX') return 'TRY';
      return instrument?.currency || guessCurrency(instrument?.assetType, instrument?.symbol);
    },
    [isEurobond, instrument?.currency, instrument?.assetType, instrument?.symbol],
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

    // VİOP: kontratın ilk işlem gününden öncesine işlem girilemez.
    if (isFuture && futureMinDate) {
      const dOnly = (form.transactionDate || '').slice(0, 10);
      if (dOnly && dOnly < futureMinDate) {
        setError(t('Bu kontrat {date} tarihinde işlem görmeye başladı; daha önceki bir tarih seçilemez.', {
          date: futureMinDate.split('-').reverse().join('.'),
        }));
        return;
      }
    }

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
        initialType={lastUsedAssetType}
      />
    );
  }

  // ── adım 2: form ─────────────────────────────────────────────────────────────

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
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1a1b22]/30 backdrop-blur-sm">
      <div className="bg-white rounded-xl shadow-2xl border border-[#e2e1eb] w-full max-w-md text-[#1a1b22] flex flex-col overflow-hidden">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#e2e1eb]">
          <div>
            <h2 className="font-bold text-lg">{t('İşlem Ekle')}</h2>
            <p className="text-sm text-[#434653]">
              <span className="font-bold text-[#1a1b22]">{instrument?.symbol}</span>
              {instrument?.name && instrument.name !== instrument.symbol && (
                <span className="ml-1 text-[#747684]">· {instrument.name}</span>
              )}
            </p>
          </div>
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setStep('search')}
              title={t('Enstrümanı değiştir')}
              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold text-[#093eaa] bg-[#093eaa]/[0.08] hover:bg-[#093eaa]/[0.15] transition-colors"
            >
              <ArrowLeftRight className="w-3.5 h-3.5" />
              {t('Değiştir')}
            </button>
            <button
              type="button"
              onClick={onClose}
              aria-label={t('Kapat')}
              className="text-[#747684] hover:text-[#1a1b22] rounded-full p-1 hover:bg-[#f3f3fc] transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-4">

          {/* BUY / SELL — M3 segmented */}
          <div className="flex bg-[#eeedf7] rounded-lg p-1">
            {['BUY', 'SELL'].map(txType => (
              <button
                key={txType}
                type="button"
                onClick={() => handleTransactionTypeChange(txType)}
                className={`flex-1 inline-flex items-center justify-center gap-1.5 py-2 rounded-md text-sm font-bold transition-all ${
                  form.transactionType === txType
                    ? txType === 'BUY' ? 'bg-[#10b981] text-white shadow-sm' : 'bg-rose-500 text-white shadow-sm'
                    : 'text-[#434653] hover:bg-[#e2e1eb]'
                }`}
              >
                {txType === 'BUY'
                  ? <><TrendingUp className="w-4 h-4" /> {t('Alış')}</>
                  : <><TrendingDown className="w-4 h-4" /> {t('Satış')}</>}
              </button>
            ))}
          </div>

          {/* Giriş tipi — desteklenen asset tipleri için BUY ve SELL'de göster */}
          {cfg.supports && (
            <div className="flex rounded-xl overflow-hidden border border-[#e2e1eb]">
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
                      ? 'bg-[#093eaa] text-white'
                      : 'bg-[#f3f3fc] text-[#434653] hover:bg-[#e2e1eb]'
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
                  <label className="block text-xs font-semibold text-[#434653] mb-1.5">
                    {isBuy
                      ? `${t('Yatırım Tutarı')}${currency ? ` (${currency})` : ''} *`
                      : `${t('Satış Tutarı')}${currency ? ` (${currency})` : ''} *`}
                  </label>
                  <input
                    type="text" inputMode="decimal"
                    value={formatGroupedInput(form.tradeAmount)}
                    onChange={e => set('tradeAmount', parseGroupedInput(e.target.value))}
                    placeholder="0,00"
                    className="w-full bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl px-4 py-2.5 text-sm text-[#1a1b22] placeholder-[#747684] focus:outline-none focus:border-[#002a7d] focus:ring-1 focus:ring-[#002a7d]"
                  />
                </>
              ) : (
                <>
                  <label className="block text-xs font-semibold text-[#434653] mb-1.5">
                    {isFund
                      ? `${t('Pay Miktarı')} *`
                      : isFuture
                        ? `${t('Kontrat Adedi')} *`
                        : isGold && goldMeta
                          ? `${t(goldMeta.quantityLabel)} *`
                          : isCommodity && commodityUnit
                            ? `${t('Miktar')} (${commodityUnit}) *`
                            : `${t('Miktar')} *`}
                  </label>
                  <input
                    type="text" inputMode="decimal"
                    value={formatGroupedInput(form.quantity)}
                    onChange={e => {
                      const raw = parseGroupedInput(e.target.value);
                      if (isFuture) {
                        set('quantity', sanitizeFutureContractQtyInput(raw));
                      } else if (isGold && goldMeta) {
                        set('quantity', sanitizeGoldQuantityInput(raw, goldMeta.floorQty));
                      } else {
                        set('quantity', raw);
                      }
                    }}
                    placeholder={isFuture || (isGold && goldMeta?.floorQty) ? '1' : '0,00'}
                    className="w-full bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl px-4 py-2.5 text-sm text-[#1a1b22] placeholder-[#747684] focus:outline-none focus:border-[#002a7d] focus:ring-1 focus:ring-[#002a7d]"
                  />
                  {isFuture && (
                    <p className="mt-1 text-[11px] text-[#747684] leading-snug">
                      {t('Kontrat adedi, almak/satmak istediğiniz VİOP sözleşmesi sayısıdır. Her sözleşmenin gerçek büyüklüğü ürüne göre değişebilir.')}
                    </p>
                  )}
                  {isCommodity && commodityUnit && (
                    <p className="mt-1 text-[11px] text-[#747684] leading-snug">
                      {t('Bu ürün için miktar birimi: {unit}', { unit: commodityUnit })}
                    </p>
                  )}
                </>
              )}
            </div>

            {/* Sağ: fiyat */}
            <div>
              <label className="block text-xs font-semibold text-[#434653] mb-1.5">
                {isFund
                  ? `${t('Birim Pay Değeri')} *`
                  : isBond
                    ? `${t('Gösterge Değeri')} *`
                    : isGold && goldMeta
                      ? `${t(goldMeta.priceLabel)} *`
                      : `${t('Fiyat')} *`}
                {priceLoading
                  ? <span className="ml-1 text-[#747684] font-normal">{t('yükleniyor...')}</span>
                  : priceAuto && <span className="ml-1 text-[#10b981] font-normal">{t('otomatik')}</span>}
              </label>
              <div className="relative">
                <input
                  type="text" inputMode="decimal"
                  value={formatGroupedInput(form.price)}
                  onChange={e => { set('price', parseGroupedInput(e.target.value)); setPriceAuto(false); setPriceNotFound(false); }}
                  placeholder="0,00"
                  className={`w-full bg-[#f3f3fc] border rounded-xl pl-4 pr-9 py-2.5 text-sm text-[#1a1b22] placeholder-[#747684] focus:outline-none focus:border-[#002a7d] focus:ring-1 focus:ring-[#002a7d] ${
                    (priceNotFound || isEurobond) ? 'border-amber-300' : priceAuto ? 'border-[#10b981]/60' : 'border-[#e2e1eb]'
                  }`}
                />
                {currencySymbol(currency) && (
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm font-semibold text-[#747684] pointer-events-none">
                    {currencySymbol(currency)}
                  </span>
                )}
              </div>
              {isEurobond && (
                <p className="mt-1 text-[11px] text-amber-600 leading-snug">
                  {t('Lütfen fiyatı TL olarak giriniz.')}
                </p>
              )}
              {priceNotFound && !isEurobond && (
                <p className="mt-1 text-[11px] text-amber-600 leading-snug">
                  {t('Bu tarih için fiyat bulunamadı, lütfen elle girin.')}
                </p>
              )}
              {isFuture && !priceNotFound && (
                <p className="mt-1 text-[11px] text-[#747684] leading-snug">
                  {t('Fiyat, kontratın piyasa fiyatıdır. Toplam değer, gerçek sözleşme büyüklüğüne göre değişebilir.')}
                </p>
              )}
              {isCommodity && commodityUnit && !priceNotFound && (
                <p className="mt-1 text-[11px] text-[#747684] leading-snug">
                  {t('Fiyat, {unit} başına TL değeridir.', { unit: commodityUnit.toLowerCase() })}
                </p>
              )}
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
            <p className="text-[11px] text-[#747684] -mt-2 leading-snug">{t(goldMeta.infoNote)}</p>
          )}

          {/* FX için kur açıklaması — kurum perspektifi nedeniyle çapraz kullanım */}
          {instrument?.assetType === 'FX' && (
            <p className="text-[11px] text-[#747684] -mt-2 leading-snug">
              {isBuy
                ? t('Döviz alış işleminde TCMB satış kuru kullanılır (kurum dövizi size satar).')
                : t('Döviz satış işleminde TCMB alış kuru kullanılır (kurum dövizinizi alır).')}
            </p>
          )}

          {/* Komisyon */}
          <div>
            <label className="block text-xs font-semibold text-[#434653] mb-1.5">
              {isGold || isBond ? t('Komisyon / Masraf') : t('Komisyon')}
            </label>
            <input
              type="text" inputMode="decimal"
              value={formatGroupedInput(form.commission)}
              onChange={e => set('commission', parseGroupedInput(e.target.value))}
              placeholder={t('0,00 (isteğe bağlı)')}
              className="w-full bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl px-4 py-2.5 text-sm text-[#1a1b22] placeholder-[#747684] focus:outline-none focus:border-[#002a7d] focus:ring-1 focus:ring-[#002a7d]"
            />
          </div>

          {/* Tarih */}
          <div>
            <label className="block text-xs font-semibold text-[#434653] mb-1.5">{t('İşlem Tarihi *')}</label>
            <DateTimeField
              value={form.transactionDate}
              onChange={v => set('transactionDate', v)}
              min={isFuture && futureMinDate ? futureMinDate : undefined}
            />
            {isFuture && futureMinDate && (
              <p className="mt-1 text-[11px] text-[#747684] leading-snug">
                {t('Bu kontrat {date} tarihinde işlem görmeye başladı; öncesi seçilemez.', {
                  date: futureMinDate.split('-').reverse().join('.'),
                })}
              </p>
            )}
          </div>

          {/* VIOP kontrat büyüklüğü bilgilendirmesi */}
          {isFuture && (
            <div className="flex items-start gap-2 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
              <svg className="w-4 h-4 text-amber-600 mt-[1px] shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M12 9v2m0 4h.01M5.07 19h13.86c1.54 0 2.5-1.67 1.73-3L13.73 4a2 2 0 00-3.46 0L3.34 16c-.77 1.33.19 3 1.73 3z" />
              </svg>
              <p className="text-[11px] text-amber-800 leading-snug">
                <span className="font-semibold">{t('Not:')}</span>{' '}
                {t('VİOP\'ta 1 kontratın temsil ettiği büyüklük ürüne göre değişir. Bu ekrandaki değerler basitleştirilmiş portföy takibi içindir.')}
              </p>
            </div>
          )}

          {/* ── Özet kutusu ── */}
          <TransactionSummary
            isAmountMode={isAmountMode}
            amountCalc={amountCalc}
            summaryHasError={summaryHasError}
            isBuy={isBuy}
            isGold={isGold}
            goldMeta={goldMeta}
            isBond={isBond}
            commission={commission}
            currency={currency}
            useQtyFloor={useQtyFloor}
            availableQty={availableQty}
            price={price}
            form={form}
            quantityModeTotal={quantityModeTotal}
            quantitySellExceedsFund={quantitySellExceedsFund}
            quantitySellExceedsGold={quantitySellExceedsGold}
            quantitySellExceedsBond={quantitySellExceedsBond}
            goldPieceQtyInvalid={goldPieceQtyInvalid}
            formatSummaryQty={formatSummaryQty}
            t={t}
          />

          {/* Hata */}
          {error && (
            <p className="text-rose-700 text-sm bg-rose-50 px-3 py-2 rounded-lg">{error}</p>
          )}

          {/* Butonlar */}
          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-[#e2e1eb] rounded-xl text-sm font-semibold text-[#434653] hover:bg-[#f3f3fc] transition-colors"
            >
              {t('İptal')}
            </button>
            <button
              type="submit"
              disabled={submitDisabled}
              className={`flex-1 text-white px-4 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-50 transition-colors ${
                isBuy ? 'bg-[#10b981] hover:bg-[#059669]' : 'bg-rose-500 hover:bg-rose-600'
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

