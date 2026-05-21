import { useState } from 'react';
import { Calculator } from 'lucide-react';
import { useTranslation } from '../../../../i18n/LanguageContext';

export default function GoldCalculator({ spot }) {
  const { t } = useTranslation();
  const gramTry = parseFloat(spot?.gramGoldTry ?? spot?.gramTl ?? 0);
  const onsUsd  = parseFloat(spot?.onsUsd ?? spot?.price ?? 0);

  const GOLD_TYPES = [
    { key: 'ons',        label: t('ALTIN/ONS ($)'),         priceTl: onsUsd },
    { key: 'gram',       label: t('GRAM ALTIN (₺)'),         priceTl: gramTry },
    { key: 'ceyrek',     label: t('ÇEYREK ALTIN (₺)'),       priceTl: parseFloat(spot?.quarterGoldTry ?? spot?.ceyrekTl ?? 0) },
    { key: 'yarim',      label: t('YARIM ALTIN (₺)'),        priceTl: parseFloat(spot?.halfGoldTry ?? spot?.yarimTl ?? 0) },
    { key: 'ziynet',     label: t('ZİYNET ALTINI (₺)'),      priceTl: parseFloat(spot?.ziynetGoldTry ?? spot?.tamTl ?? 0) },
    { key: 'cumhuriyet', label: t('CUMHURİYET ALTINI (₺)'),  priceTl: parseFloat(spot?.republicGoldTry ?? spot?.cumhuriyetTl ?? 0) },
    { key: '14ayar',     label: t('14 AYAR BİLEZİK (₺/gr)'), priceTl: parseFloat(spot?.fourteenKBraceletTry ?? spot?.ayar14Tl ?? 0) },
    { key: '22ayar',     label: t('22 AYAR BİLEZİK (₺/gr)'), priceTl: parseFloat(spot?.twentyTwoKBraceletTry ?? spot?.ayar22Tl ?? 0) },
    { key: 'try',        label: t('TÜRK LİRASI (₺)'),        priceTl: 1 },
  ];

  const [fromKey, setFromKey] = useState('ons');
  const [toKey,   setToKey]   = useState('try');
  const [amount,  setAmount]  = useState('1');

  function getPrice(key) {
    const t = GOLD_TYPES.find(x => x.key === key);
    if (!t || isNaN(t.priceTl) || t.priceTl === 0) return 1;
    return t.priceTl;
  }

  function calculate() {
    const a = parseFloat(amount || 0);
    if (!a || isNaN(a)) return '-';
    const from = getPrice(fromKey);
    const to   = getPrice(toKey);
    if (!to) return '-';
    return ((a * from) / to).toLocaleString('tr-TR', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 6,
    });
  }

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4 flex items-center gap-2">
        <Calculator className="w-5 h-5 text-[#093eaa]" /> {t('Altın Hesaplama')}
      </h2>
      <div className="space-y-4">
        <div>
          <label className="block text-xs font-bold text-gray-500 mb-1.5">{t('KAYNAK')}</label>
          <select
            value={fromKey}
            onChange={e => setFromKey(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 bg-white"
          >
            {GOLD_TYPES.map(t => <option key={t.key} value={t.key}>{t.label}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-bold text-gray-500 mb-1.5">{t('HEDEF')}</label>
          <select
            value={toKey}
            onChange={e => setToKey(e.target.value)}
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 bg-white"
          >
            {GOLD_TYPES.map(t => <option key={t.key} value={t.key}>{t.label}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-bold text-gray-500 mb-1.5">{t('TUTAR')}</label>
          <input
            type="number"
            value={amount}
            onChange={e => setAmount(e.target.value)}
            placeholder="1"
            className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30"
          />
        </div>
        <div className="bg-[#093eaa] rounded-xl p-4 text-center">
          <p className="text-white/70 text-xs mb-1">
            {amount || '0'} {GOLD_TYPES.find(t => t.key === fromKey)?.label} =
          </p>
          <p className="text-white text-2xl font-black">{calculate()}</p>
          <p className="text-white/70 text-xs mt-1">
            {GOLD_TYPES.find(t => t.key === toKey)?.label}
          </p>
        </div>
        <p className="text-xs text-gray-400 text-center">
          {t('Teorik referans fiyatlar kullanılmaktadır. Gösterge niteliğindedir.')}
        </p>
      </div>
    </div>
  );
}
