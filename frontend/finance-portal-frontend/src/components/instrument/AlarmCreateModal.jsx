import { useState } from 'react';
import { Link } from 'react-router-dom';
import { X, Bell, ChevronDown } from 'lucide-react';
import { createAlarm, updateAlarm } from '../../api/alarmApi';
import { useTranslation } from '../../context/LanguageContext';
import { useToast } from '../../context/ToastContext';

const FREQUENCIES = [
  { key: 'ONCE', label: 'Tek Seferlik' },
  { key: 'RECURRING', label: 'Sürekli' },
];

// Sayı çöz: virgül varsa TR biçimi (nokta=binlik, virgül=ondalık); yoksa noktayı ondalık say.
// "3,2"→3.2, "3.2"→3.2, "1.234,56"→1234.56, "₺3,20"→3.2
function parseNumeric(v) {
  if (v == null) return null;
  let s = String(v).trim().replace(/[^\d.,-]/g, '');
  if (s.includes(',')) s = s.replace(/\./g, '').replace(',', '.');
  const n = parseFloat(s);
  return Number.isFinite(n) ? n : null;
}

// Eşik ön-doldurması için TR biçimli gösterim (3.2 → "3,2")
function fmtTr(n) {
  if (n == null || !Number.isFinite(Number(n))) return '';
  return Number(n).toLocaleString('tr-TR', { maximumFractionDigits: 8 });
}

// Para birimi sembolü (backend AlarmCurrency ile aynı kural)
function currencySymbol(assetType, symbol) {
  const s = String(symbol || '').toUpperCase();
  switch (assetType) {
    case 'FX': case 'FUND': case 'CRYPTO': case 'GOLD': return '₺';
    case 'STOCK': return s.endsWith('.IS') ? '₺' : '$';
    case 'FUTURE': return s.endsWith('=F') ? '$' : '₺';
    case 'COMMODITY': return s.includes('TRY') ? '₺' : '$';
    default: return '';
  }
}

/**
 * Alarm oluşturma/düzenleme modalı. Görseldeki mantık: Durum (Fiyat/Değişim/Hacim) +
 * Üzerine Çıkarsa / Altına İnerse + eşik değeri + sıklık (Tek Seferlik / Sürekli).
 *
 * @param instrument { assetType, symbol, name, price }  (oluşturma)
 * @param alarm      mevcut alarm                          (düzenleme — verilirse edit modu)
 * @param onSaved    kaydedildiğinde çağrılır (create veya update)
 */
export default function AlarmCreateModal({ instrument, alarm, onClose, onSaved, onCreated }) {
  const { t } = useTranslation();
  const toast = useToast();

  const editMode = !!alarm;
  const inst = editMode
    ? { assetType: alarm.assetType, symbol: alarm.symbol, name: alarm.instrumentName || alarm.symbol, price: null }
    : instrument;

  const currentPrice = inst?.price != null && Number.isFinite(Number(inst.price)) ? Number(inst.price) : null;

  // Alarm yalnız fiyat üzerinedir (değişim/hacim kaldırıldı). Düzenlemede mevcut metrik korunur.
  const metric = alarm?.metric || 'PRICE';
  const [direction, setDirection] = useState(alarm?.direction || 'ABOVE');
  const [threshold, setThreshold] = useState(
    alarm?.threshold != null ? fmtTr(alarm.threshold) : (currentPrice != null ? fmtTr(currentPrice) : '')
  );
  const [frequency, setFrequency] = useState(alarm?.frequency || 'ONCE');
  const [note, setNote] = useState(alarm?.note || '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const cur = currencySymbol(inst?.assetType, inst?.symbol);
  const suffix = metric === 'CHANGE_PERCENT' ? '%' : (metric === 'VOLUME' ? '' : cur);

  const displayName = inst?.name || inst?.symbol;

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    const value = parseNumeric(threshold);
    if (value == null) {
      setError(t('Lütfen geçerli bir değer girin.'));
      return;
    }
    setLoading(true);
    try {
      const trimmedNote = note.trim();
      if (editMode) {
        await updateAlarm(alarm.id, { metric, direction, threshold: value, frequency, note: trimmedNote });
        toast.success(t('Alarm güncellendi.'));
      } else {
        await createAlarm({
          assetType: inst.assetType,
          symbol: inst.symbol,
          instrumentName: displayName,
          metric,
          direction,
          threshold: value,
          frequency,
          note: trimmedNote,
        });
        toast.success(t('Alarm oluşturuldu.'));
      }
      onSaved?.();
      onCreated?.();
      onClose();
    } catch (err) {
      const msg = err?.response?.data?.message;
      setError(msg || t('Alarm kaydedilemedi.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1a1b22]/30 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl border border-[#e2e1eb] w-full max-w-md overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-3 py-3 sm:px-6 sm:py-4 border-b border-gray-100">
          <h2 className="font-bold text-gray-900 flex items-center gap-2 min-w-0">
            <Bell className="w-4 h-4 text-[#093eaa] flex-shrink-0" />
            <span className="truncate">{displayName} {editMode ? t('— Alarmı Düzenle') : t('İçin Alarm')}</span>
          </h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 rounded-full p-2 sm:p-1 hover:bg-gray-50 flex-shrink-0">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-3 sm:p-6 space-y-4 sm:space-y-5">
          {/* Yön + eşik */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1.5">{t('Koşul')}</label>
              <div className="relative">
                <select
                  value={direction}
                  onChange={e => setDirection(e.target.value)}
                  className="w-full appearance-none px-3 py-2.5 pr-9 border border-gray-200 rounded-xl text-sm bg-white focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]"
                >
                  <option value="ABOVE">{t('Üzerine Çıkarsa')}</option>
                  <option value="BELOW">{t('Altına İnerse')}</option>
                </select>
                <ChevronDown className="w-4 h-4 absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
              </div>
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1.5">{t('Değer')}</label>
              <div className="relative">
                <input
                  type="text"
                  inputMode="decimal"
                  value={threshold}
                  onChange={e => setThreshold(e.target.value)}
                  placeholder="0,00"
                  className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm tabular-nums focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]"
                />
                {suffix && (
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-gray-400 pointer-events-none">{suffix}</span>
                )}
              </div>
            </div>
          </div>

          {metric === 'PRICE' && currentPrice != null && (
            <p className="-mt-2 text-xs text-gray-400">
              {t('Şu anki fiyat:')} <span className="font-semibold text-gray-600">{currentPrice.toLocaleString('tr-TR')}{cur ? ` ${cur}` : ''}</span>
            </p>
          )}

          {/* Sıklık */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1.5">{t('Sıklık')}</label>
            <div className="flex gap-2">
              {FREQUENCIES.map(f => (
                <button
                  key={f.key}
                  type="button"
                  onClick={() => setFrequency(f.key)}
                  className={`flex-1 px-3 py-2 rounded-lg border text-sm font-semibold transition-all ${
                    frequency === f.key
                      ? 'border-[#093eaa] bg-[#093eaa]/5 text-[#093eaa]'
                      : 'border-gray-200 text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  {t(f.label)}
                </button>
              ))}
            </div>
            <p className="mt-1.5 text-[11px] text-gray-400">
              {frequency === 'ONCE'
                ? t('Koşul bir kez sağlandığında tetiklenir ve durur.')
                : t('Koşul her sağlandığında tetiklenir (saatte en fazla bir kez).')}
            </p>
          </div>

          {/* Not (isteğe bağlı) — alarm tetiklenince e-postaya eklenir */}
          <div>
            <label className="block text-xs font-semibold text-gray-600 mb-1.5">
              {t('Not')} <span className="font-normal text-gray-400">({t('isteğe bağlı')})</span>
            </label>
            <textarea
              value={note}
              onChange={e => setNote(e.target.value)}
              rows={2}
              maxLength={255}
              placeholder={t('ör. Hedef satış fiyatım — alarm e-postasına eklenir')}
              className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm resize-none focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]"
            />
          </div>

          {error && (
            <p className="text-rose-700 text-sm bg-rose-50 px-3 py-2 rounded-lg">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full px-4 py-2.5 rounded-xl bg-[#093eaa] text-white font-bold text-sm hover:bg-[#0a2966] disabled:opacity-50 transition-colors"
          >
            {loading ? t('Kaydediliyor…') : editMode ? t('Kaydet') : t('Oluştur')}
          </button>

          <div className="text-center">
            <Link to="/alarms" onClick={onClose} className="text-xs font-semibold text-[#093eaa] hover:underline">
              {t('Alarmlarımı Düzenle')}
            </Link>
          </div>
        </form>
      </div>
    </div>
  );
}
