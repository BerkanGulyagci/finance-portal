import { useState } from 'react';
import { Link } from 'react-router-dom';
import { X, Bell, ChevronDown, AlertTriangle } from 'lucide-react';
import { createAlarm, updateAlarm } from '../../api/alarmApi';
import { useTranslation } from '../../context/LanguageContext';
import { useToast } from '../../context/ToastContext';

const FREQUENCIES = [
  { key: 'ONCE', label: 'Tek Seferlik' },
  { key: 'RECURRING', label: 'Sürekli' },
];

// MARGIN_RATIO için hızlı eşik presetleri (kullanıcı seviye seçer; tipik margin call %25).
const MARGIN_PRESETS = [25, 50, 75];
const MARGIN_MIN = 5;
const MARGIN_MAX = 95;

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
 * Alarm oluşturma/düzenleme modalı.
 *
 * İki alarm metriği:
 *   - PRICE         → her enstrümanda. Eşik = mutlak fiyat. Yön = ABOVE/BELOW.
 *   - MARGIN_RATIO  → SADECE FUTURE (VİOP). Eşik UI'da %25-%95 arası tam sayı; backend'e
 *                     0-1 ondalık olarak gönderilir (örn. UI %25 → API 0.25). Yön = BELOW
 *                     (sabit; teminat oranı eşiğin altına düştüğünde tetiklenir).
 *
 * @param instrument { assetType, symbol, name, price, marginRatio?, marginStatus? }
 * @param alarm      mevcut alarm (verilirse edit modu)
 */
export default function AlarmCreateModal({ instrument, alarm, onClose, onSaved, onCreated }) {
  const { t } = useTranslation();
  const toast = useToast();

  const editMode = !!alarm;
  const inst = editMode
    ? { assetType: alarm.assetType, symbol: alarm.symbol, name: alarm.instrumentName || alarm.symbol, price: null }
    : instrument;

  const isFuture = String(inst?.assetType ?? '').toUpperCase() === 'FUTURE';
  const currentPrice = inst?.price != null && Number.isFinite(Number(inst.price)) ? Number(inst.price) : null;
  const currentMarginRatio = !editMode && inst?.marginRatio != null && Number.isFinite(Number(inst.marginRatio))
    ? Number(inst.marginRatio)
    : null;

  const [metric, setMetric] = useState(alarm?.metric || 'PRICE');
  const [direction, setDirection] = useState(alarm?.direction || 'ABOVE');
  // Eşik string olarak tutulur (TR/EN biçim toleransı için). MARGIN_RATIO durumunda 0-100
  // yüzde olarak girilir; backend'e gönderirken /100 ile 0-1 ondalığa çevrilir.
  const [threshold, setThreshold] = useState(() => {
    if (alarm?.threshold != null) {
      if (alarm.metric === 'MARGIN_RATIO') {
        return fmtTr(Number(alarm.threshold) * 100);
      }
      return fmtTr(alarm.threshold);
    }
    return currentPrice != null ? fmtTr(currentPrice) : '';
  });
  const [frequency, setFrequency] = useState(alarm?.frequency || 'ONCE');
  const [note, setNote] = useState(alarm?.note || '');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const cur = currencySymbol(inst?.assetType, inst?.symbol);
  const suffix = metric === 'MARGIN_RATIO' ? '%' : cur;
  const isMarginMetric = metric === 'MARGIN_RATIO';
  const nonFutureMarginWarning = isMarginMetric && !isFuture;

  const displayName = inst?.name || inst?.symbol;

  // Metrik değişimini idare et: MARGIN_RATIO'ya geçince yönü BELOW'a kilitle, eşiği %50 yap.
  // PRICE'a geri dönüşte mevcut fiyatı tekrar öner.
  function onMetricChange(next) {
    setMetric(next);
    setError('');
    if (next === 'MARGIN_RATIO') {
      setDirection('BELOW');
      setThreshold(prev => {
        const parsed = parseNumeric(prev);
        // Eğer mevcut değer fiyat artığı (büyük sayı) ise temizle
        if (parsed == null || parsed > MARGIN_MAX || parsed < MARGIN_MIN) return '50';
        return prev;
      });
    } else if (next === 'PRICE') {
      if (currentPrice != null) setThreshold(fmtTr(currentPrice));
    }
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');

    if (isMarginMetric && !isFuture) {
      setError(t('Teminat oranı alarmı yalnızca VİOP (FUTURE) enstrümanları için kurulabilir.'));
      return;
    }

    const value = parseNumeric(threshold);
    if (value == null) {
      setError(t('Lütfen geçerli bir değer girin.'));
      return;
    }

    let payloadThreshold = value;
    if (isMarginMetric) {
      if (value < MARGIN_MIN || value > MARGIN_MAX) {
        setError(t('Eşik {min}% ile {max}% arasında olmalı.', { min: MARGIN_MIN, max: MARGIN_MAX }));
        return;
      }
      payloadThreshold = value / 100; // backend 0-1 ondalık bekliyor
    }

    setLoading(true);
    try {
      const trimmedNote = note.trim();
      // MARGIN_RATIO her zaman BELOW; PRICE için kullanıcının seçtiği yön.
      const finalDirection = isMarginMetric ? 'BELOW' : direction;
      if (editMode) {
        await updateAlarm(alarm.id, { metric, direction: finalDirection, threshold: payloadThreshold, frequency, note: trimmedNote });
        toast.success(t('Alarm güncellendi.'));
      } else {
        await createAlarm({
          assetType: inst.assetType,
          symbol: inst.symbol,
          instrumentName: displayName,
          metric,
          direction: finalDirection,
          threshold: payloadThreshold,
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
          {/* Alarm türü — MARGIN_RATIO yalnızca edit modunda (eski sistem) gösterilir; yeni oluşturmada PRICE sabit. */}
          {editMode && isMarginMetric && (
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1.5">{t('Alarm Türü')}</label>
              <div className="relative">
                <select
                  value={metric}
                  disabled
                  className="w-full appearance-none px-3 py-2.5 pr-9 border border-gray-200 rounded-xl text-sm bg-gray-50 text-gray-500 focus:outline-none"
                >
                  <option value="MARGIN_RATIO">{t('Teminat Oranı (VİOP) — Eski Sistem')}</option>
                </select>
                <ChevronDown className="w-4 h-4 absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
              </div>
              <p className="mt-1 text-[11px] text-gray-400">
                {t('Teminat uyarıları artık Profil sayfasından tek noktadan yönetilir.')}
              </p>
            </div>
          )}

          {!editMode && isFuture && (
            <div className="flex items-start gap-2 text-xs text-[#093eaa] bg-[#093eaa]/5 border border-[#093eaa]/15 rounded-lg px-3 py-2">
              <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
              <span>
                {t('Teminat uyarıları artık Profil sayfasından tek noktadan yönetilir.')}{' '}
                <Link to="/profile" onClick={onClose} className="font-semibold underline">
                  {t('Profil')}
                </Link>
              </span>
            </div>
          )}

          {/* Yön + eşik */}
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1.5">{t('Koşul')}</label>
              <div className="relative">
                <select
                  value={isMarginMetric ? 'BELOW' : direction}
                  onChange={e => setDirection(e.target.value)}
                  disabled={isMarginMetric}
                  className="w-full appearance-none px-3 py-2.5 pr-9 border border-gray-200 rounded-xl text-sm bg-white focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa] disabled:bg-gray-50 disabled:text-gray-500"
                >
                  <option value="ABOVE">{t('Üzerine Çıkarsa')}</option>
                  <option value="BELOW">{t('Altına İnerse')}</option>
                </select>
                <ChevronDown className="w-4 h-4 absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 pointer-events-none" />
              </div>
              {isMarginMetric && (
                <p className="mt-1 text-[10px] text-gray-400">
                  {t('Teminat alarmları yalnız "altına inerse" mantığıyla çalışır.')}
                </p>
              )}
            </div>
            <div>
              <label className="block text-xs font-semibold text-gray-600 mb-1.5">{t('Değer')}</label>
              <div className="relative">
                <input
                  type="text"
                  inputMode="decimal"
                  value={threshold}
                  onChange={e => setThreshold(e.target.value)}
                  placeholder={isMarginMetric ? '50' : '0,00'}
                  className="w-full px-3 py-2.5 border border-gray-200 rounded-xl text-sm tabular-nums focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]"
                />
                {suffix && (
                  <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-gray-400 pointer-events-none">{suffix}</span>
                )}
              </div>
            </div>
          </div>

          {/* MARGIN_RATIO hızlı eşik presetleri */}
          {isMarginMetric && (
            <div className="-mt-2">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-[11px] text-gray-500 font-semibold mr-1">{t('Hızlı seç:')}</span>
                {MARGIN_PRESETS.map(p => {
                  const active = parseNumeric(threshold) === p;
                  return (
                    <button
                      key={p}
                      type="button"
                      onClick={() => setThreshold(String(p))}
                      className={`px-2.5 py-1 rounded-md text-[11px] font-bold border transition-colors ${
                        active
                          ? 'border-[#093eaa] bg-[#093eaa]/5 text-[#093eaa]'
                          : 'border-gray-200 text-gray-600 hover:bg-gray-50'
                      }`}
                    >
                      %{p}
                    </button>
                  );
                })}
              </div>
              <p className="mt-1.5 text-[11px] text-gray-400">
                {t('Teminat oranı bu eşiğin altına düşerse uyarı e-postası ve bildirim alacaksınız.')}
              </p>
            </div>
          )}

          {!isMarginMetric && currentPrice != null && (
            <p className="-mt-2 text-xs text-gray-400">
              {t('Şu anki fiyat:')} <span className="font-semibold text-gray-600">{currentPrice.toLocaleString('tr-TR')}{cur ? ` ${cur}` : ''}</span>
            </p>
          )}

          {isMarginMetric && currentMarginRatio != null && (
            <p className="-mt-2 text-xs text-gray-400">
              {t('Şu anki teminat oranı:')}{' '}
              <span className="font-semibold text-gray-600">%{(currentMarginRatio * 100).toLocaleString('tr-TR', { maximumFractionDigits: 1 })}</span>
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
            disabled={loading || nonFutureMarginWarning}
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
