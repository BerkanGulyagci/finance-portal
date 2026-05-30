import { useState } from 'react';
import { ArrowLeftRight, ChevronDown } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';

// Çevirici chip'i (döviz çeviricisindeki CurrencyChip ile aynı tasarım)
function GoldTypeChip({ value, onChange, types }) {
  return (
    <div className="relative shrink-0">
      <select
        value={value}
        onChange={e => onChange(e.target.value)}
        className="appearance-none bg-[#eef2f8] hover:bg-[#e3eaf6] text-[#093eaa] font-bold text-xs rounded-full pl-2.5 pr-6 py-1 max-w-[120px] cursor-pointer focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 transition-colors"
      >
        {types.map(tp => <option key={tp.key} value={tp.key}>{tp.short}</option>)}
      </select>
      <ChevronDown className="w-3 h-3 absolute right-1.5 top-1/2 -translate-y-1/2 text-[#093eaa] pointer-events-none" />
    </div>
  );
}

const TROY_OUNCE_GRAMS = 31.1034768; // 1 ons altın = 31.1034768 gram saf altın

export default function GoldCalculator({ spot }) {
  const { t } = useTranslation();
  const gramTry = parseFloat(spot?.gramGoldTry ?? spot?.gramTl ?? 0);
  const onsUsd  = parseFloat(spot?.onsUsd ?? spot?.price ?? 0);
  const usdTry  = parseFloat(spot?.usdTry ?? 0);
  // 1 ons'un TL değeri: backend onsTry (onsUsd × USD/TRY); yoksa onsUsd×kur; o da yoksa gram×31.10
  const onsTry  = parseFloat(spot?.onsTry ?? 0)
    || (onsUsd && usdTry ? onsUsd * usdTry : 0)
    || (gramTry ? gramTry * TROY_OUNCE_GRAMS : 0);

  const GOLD_TYPES = [
    { key: 'ons',        label: t('ALTIN/ONS'),             short: t('ONS'),        priceTl: onsTry },
    { key: 'gram',       label: t('GRAM ALTIN (₺)'),         short: t('GRAM (₺)'),   priceTl: gramTry },
    { key: 'ceyrek',     label: t('ÇEYREK ALTIN (₺)'),       short: t('ÇEYREK'),     priceTl: parseFloat(spot?.quarterGoldTry ?? spot?.ceyrekTl ?? 0) },
    { key: 'yarim',      label: t('YARIM ALTIN (₺)'),        short: t('YARIM'),      priceTl: parseFloat(spot?.halfGoldTry ?? spot?.yarimTl ?? 0) },
    { key: 'ziynet',     label: t('ZİYNET ALTINI (₺)'),      short: t('ZİYNET'),     priceTl: parseFloat(spot?.ziynetGoldTry ?? spot?.tamTl ?? 0) },
    { key: 'cumhuriyet', label: t('CUMHURİYET ALTINI (₺)'),  short: t('CUMHUR.'),    priceTl: parseFloat(spot?.republicGoldTry ?? spot?.cumhuriyetTl ?? 0) },
    { key: '14ayar',     label: t('14 AYAR BİLEZİK (₺/gr)'), short: t('14 AYAR'),    priceTl: parseFloat(spot?.fourteenKBraceletTry ?? spot?.ayar14Tl ?? 0) },
    { key: '22ayar',     label: t('22 AYAR BİLEZİK (₺/gr)'), short: t('22 AYAR'),    priceTl: parseFloat(spot?.twentyTwoKBraceletTry ?? spot?.ayar22Tl ?? 0) },
    { key: 'try',        label: t('TÜRK LİRASI (₺)'),        short: t('TRY (₺)'),    priceTl: 1 },
  ];

  const [fromKey, setFromKey] = useState('ons');
  const [toKey,   setToKey]   = useState('try');
  const [amount,  setAmount]  = useState('1');

  function getPrice(key) {
    const tp = GOLD_TYPES.find(x => x.key === key);
    if (!tp || isNaN(tp.priceTl) || tp.priceTl === 0) return 1;
    return tp.priceTl;
  }

  const fromType = GOLD_TYPES.find(x => x.key === fromKey);
  const toType   = GOLD_TYPES.find(x => x.key === toKey);
  const fromP = getPrice(fromKey);
  const toP   = getPrice(toKey);

  function calculate() {
    const a = parseFloat(amount || 0);
    if (!a || isNaN(a) || !toP) return '-';
    return ((a * fromP) / toP).toLocaleString('tr-TR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 6,
    });
  }

  const unitRate = (fromP && toP)
    ? (fromP / toP).toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 6 })
    : null;

  function swap() { setFromKey(toKey); setToKey(fromKey); }

  return (
    <section className="bg-white rounded-3xl border border-[#e2e8f0] shadow-[0_1px_3px_rgba(15,23,42,0.06)] p-5">
      <h2 className="font-bold text-[#1a1c1e] mb-3 flex items-center gap-2 text-sm">
        <ArrowLeftRight className="w-4 h-4 text-[#093eaa]" /> {t('Altın Hesaplama')}
      </h2>

      <div className="space-y-2">
        {/* Kaynak: tutar + tür chip'i */}
        <div className="relative">
          <input
            type="number" value={amount} onChange={e => setAmount(e.target.value)} placeholder="1"
            className="w-full pl-3 pr-28 py-2.5 rounded-xl border border-[#e2e8f0] bg-white text-[#1a1c1e] font-semibold tabular-nums focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa] transition-all"
          />
          <div className="absolute right-2 top-1/2 -translate-y-1/2">
            <GoldTypeChip value={fromKey} onChange={setFromKey} types={GOLD_TYPES} />
          </div>
        </div>

        {/* Ters çevir */}
        <div className="flex justify-center">
          <button onClick={swap} title={t('Ters çevir')}
            className="m3-state w-8 h-8 rounded-full bg-[#093eaa] text-white flex items-center justify-center shadow-[0_3px_10px_-3px_rgba(9,62,170,0.6)] hover:rotate-180 transition-transform duration-500">
            <ArrowLeftRight className="w-4 h-4" />
          </button>
        </div>

        {/* Hedef: sonuç + tür chip'i */}
        <div className="relative">
          <input
            type="text" readOnly value={calculate()}
            className="w-full pl-3 pr-28 py-2.5 rounded-xl border border-[#eef2f8] bg-[#f6f8fc] text-[#1a1c1e] font-semibold tabular-nums"
          />
          <div className="absolute right-2 top-1/2 -translate-y-1/2">
            <GoldTypeChip value={toKey} onChange={setToKey} types={GOLD_TYPES} />
          </div>
        </div>
      </div>

      {/* Birim kur */}
      <div className="mt-3 p-3 rounded-xl bg-[#eef4ff] border border-[#dbe6ff] text-center">
        {unitRate ? (
          <p className="text-sm font-black text-[#093eaa] tabular-nums">
            1 {fromType?.short} = {unitRate} {toType?.short}
          </p>
        ) : (
          <p className="text-xs text-[#5a6472]">{t('Bu çift için fiyat bulunamadı.')}</p>
        )}
      </div>

      <p className="text-xs text-gray-400 text-center mt-3">
        {t('Teorik referans fiyatlar kullanılmaktadır. Gösterge niteliğindedir.')}
      </p>
    </section>
  );
}
