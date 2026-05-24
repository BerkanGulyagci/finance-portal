import { useEffect, useState, useCallback } from 'react';
import { Star, Bell } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { getMyPortfolios } from '../../api/portfolioApi';
import { useTranslation } from '../../i18n/LanguageContext';
import InstrumentTargetModal from './InstrumentTargetModal';
import AlarmCreateModal from './AlarmCreateModal';

// Fiyatı sayıya çevir: JS sayısı doğrudan; "1.234,56" (TR) veya "$123.45" gibi metinleri normalize et.
function toNumber(v) {
  if (v == null) return undefined;
  if (typeof v === 'number') return Number.isFinite(v) ? v : undefined;
  let s = String(v).trim().replace(/[^\d.,-]/g, '');
  if (s.includes(',')) s = s.replace(/\./g, '').replace(',', '.');
  const n = parseFloat(s);
  return Number.isFinite(n) ? n : undefined;
}

/**
 * Enstrüman detay sayfalarında "Portföye Ekle" + "Alarm" buton kümesi.
 * YALNIZCA giriş yapmış kullanıcıya gösterilir. "Portföye Ekle" hem varlık portföylerini
 * hem izleme listelerini gösteren birleşik seçiciyi açar.
 *
 * @param assetType STOCK | FUND | FX | FUTURE | CRYPTO | GOLD | COMMODITY | BOND
 * @param symbol    backend sembolü
 * @param name      görünen ad
 * @param price     güncel fiyat (alarm/işlem modalında ön-doldurma)
 */
export default function InstrumentActionButtons({ assetType, symbol, name, price, className = '' }) {
  const { isAuthenticated } = useAuth();
  const { t } = useTranslation();

  const [holdingCount, setHoldingCount] = useState(0);
  const [targetOpen, setTargetOpen] = useState(false);
  const [alarmOpen, setAlarmOpen] = useState(false);

  const sameInstrument = useCallback(
    (h) => h && h.assetType === assetType
      && String(h.symbol).toUpperCase() === String(symbol).toUpperCase(),
    [assetType, symbol],
  );

  const refresh = useCallback(() => {
    if (!isAuthenticated || !symbol) return;
    getMyPortfolios()
      .then(list => {
        const holdings = (list ?? []).filter(p => p.portfolioType !== 'WATCHLIST');
        setHoldingCount(holdings.filter(p => (p.holdings ?? []).some(sameInstrument)).length);
      })
      .catch(() => { /* sessiz */ });
  }, [isAuthenticated, symbol, sameInstrument]);

  useEffect(() => { refresh(); }, [refresh]);

  if (!isAuthenticated || !symbol) return null;

  const added = holdingCount > 0;
  const instrument = { assetType, symbol, name: name || symbol, price: toNumber(price) };

  return (
    <>
      <div className={`flex items-center gap-2 ${className}`}>
        <button
          onClick={() => setTargetOpen(true)}
          title={added ? t('Portföyde — düzenle / yeni işlem ekle') : t('Portföye Ekle')}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold bg-[#093eaa] text-white hover:bg-[#0a2966] transition-all"
        >
          <Star className={`w-3.5 h-3.5 ${added ? 'fill-amber-400 text-amber-400' : ''}`} />
          {added ? t('Eklendi') : t('Portföye Ekle')}
          {added && holdingCount > 0 && (
            <span className="ml-0.5 inline-flex items-center justify-center min-w-[18px] h-[18px] px-1 rounded-full bg-white/25 text-[10px] font-bold">
              {holdingCount}
            </span>
          )}
        </button>

        <button
          onClick={() => setAlarmOpen(true)}
          title={t('Alarm Oluştur')}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border border-[#093eaa]/25 text-[#093eaa] bg-[#093eaa]/5 hover:bg-[#093eaa]/10 transition-all"
        >
          <Bell className="w-3.5 h-3.5" /> {t('Alarm')}
        </button>
      </div>

      {targetOpen && (
        <InstrumentTargetModal
          instrument={instrument}
          onClose={() => setTargetOpen(false)}
          onChanged={refresh}
        />
      )}

      {alarmOpen && (
        <AlarmCreateModal
          instrument={instrument}
          onClose={() => setAlarmOpen(false)}
          onCreated={() => setAlarmOpen(false)}
        />
      )}
    </>
  );
}
