import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ShieldAlert, Save } from 'lucide-react';
import { prefGet, prefSet } from '../../../api/prefs';
import { useTranslation } from '../../../context/LanguageContext';
import { useToast } from '../../../context/ToastContext';

const PREF_KEY = 'margin_alert_threshold_pct';
const DEFAULT_PCT = 50;
const MIN_PCT = 0;
const MAX_PCT = 95;
const STEP = 5;
const PRESETS = [25, 50, 75];

// Sayıyı 0-95 aralığına ve 5'in katına kelepçele. NaN → null.
function clampInt(n) {
  if (n == null || !Number.isFinite(Number(n))) return null;
  let v = Math.round(Number(n));
  if (v < MIN_PCT) v = MIN_PCT;
  if (v > MAX_PCT) v = MAX_PCT;
  return v;
}

/**
 * VİOP teminat uyarı eşiği (kullanıcı seviyesi). Kart, prefs.js üzerinden
 * `margin_alert_threshold_pct` anahtarını okur/yazar; sunucuya debounce'lu PUT.
 * 0 = kapalı; >0 ise tüm açık VİOP pozisyonlarının marjin oranı bu eşiğin
 * altına düştüğünde uyarı e-postası + uygulama-içi bildirim alınır.
 */
export default function MarginAlertSettings() {
  const { t } = useTranslation();
  const toast = useToast();

  const initial = useMemo(() => {
    const raw = prefGet(PREF_KEY, DEFAULT_PCT);
    const c = clampInt(raw);
    return c == null ? DEFAULT_PCT : c;
  }, []);

  const [pct, setPct] = useState(initial);
  const [saved, setSaved] = useState(initial);

  // Başka cihazda değişip hydrate sırasında localStorage güncellenirse senkronla.
  useEffect(() => {
    const onStorage = (e) => {
      if (e.key !== PREF_KEY) return;
      const c = clampInt(prefGet(PREF_KEY, DEFAULT_PCT));
      if (c != null) { setPct(c); setSaved(c); }
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const dirty = pct !== saved;
  const disabled = pct === 0;

  function handleSave() {
    const v = clampInt(pct);
    if (v == null) return;
    prefSet(PREF_KEY, v);
    setSaved(v);
    toast.success(t('Teminat uyarısı kaydedildi.'));
  }

  return (
    <section className="m3-card p-3 sm:p-6">
      <div className="flex items-center gap-3 mb-4">
        <span className="m3-icon-chip"><ShieldAlert className="w-5 h-5" /></span>
        <div className="min-w-0">
          <h3 className="font-bold text-gray-900 leading-tight">{t('Teminat Uyarısı (VİOP)')}</h3>
          <p className="text-xs text-gray-500 mt-0.5">
            {t('Açık VİOP pozisyonlarının marjin oranı bu eşiğin altına düşerse bildirim ve e-posta alırsınız. 0 = kapalı.')}
          </p>
        </div>
      </div>

      <div className="space-y-4">
        {/* Eşik göstergesi + durum rozeti */}
        <div className="flex items-center justify-between gap-3 flex-wrap">
          <label className="text-xs font-semibold text-gray-600">
            {t('Marjin oranı eşik (%)')}
          </label>
          <span
            className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${
              disabled
                ? 'bg-gray-100 text-gray-500 border-gray-200'
                : 'bg-emerald-50 text-emerald-700 border-emerald-200'
            }`}
          >
            {disabled ? t('Kapalı') : `%${pct}`}
          </span>
        </div>

        {/* Slider */}
        <div>
          <input
            type="range"
            min={MIN_PCT}
            max={MAX_PCT}
            step={STEP}
            value={pct}
            onChange={(e) => setPct(clampInt(e.target.value) ?? DEFAULT_PCT)}
            className="w-full accent-[#093eaa]"
          />
          <div className="flex justify-between text-[10px] text-gray-400 mt-1 tabular-nums">
            <span>0</span>
            <span>25</span>
            <span>50</span>
            <span>75</span>
            <span>95</span>
          </div>
        </div>

        {/* Hızlı seçim */}
        <div className="flex items-center gap-2 flex-wrap">
          <span className="text-[11px] text-gray-500 font-semibold mr-1">{t('Hızlı seç:')}</span>
          <button
            type="button"
            onClick={() => setPct(0)}
            className={`px-2.5 py-1 rounded-md text-[11px] font-bold border transition-colors ${
              pct === 0
                ? 'border-gray-400 bg-gray-100 text-gray-700'
                : 'border-gray-200 text-gray-600 hover:bg-gray-50'
            }`}
          >
            {t('Kapalı')}
          </button>
          {PRESETS.map((p) => {
            const active = pct === p;
            return (
              <button
                key={p}
                type="button"
                onClick={() => setPct(p)}
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

        {/* Sayısal giriş + Kaydet */}
        <div className="flex items-center gap-2">
          <div className="relative flex-1">
            <input
              type="number"
              min={MIN_PCT}
              max={MAX_PCT}
              step={STEP}
              value={pct}
              onChange={(e) => {
                const c = clampInt(e.target.value);
                setPct(c == null ? 0 : c);
              }}
              className="w-full px-3 py-2 pr-8 border border-gray-200 rounded-xl text-sm tabular-nums focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]"
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-sm text-gray-400 pointer-events-none">%</span>
          </div>
          <button
            type="button"
            onClick={handleSave}
            disabled={!dirty}
            className="inline-flex items-center gap-1.5 px-3 py-2 rounded-xl bg-[#093eaa] text-white text-xs font-bold hover:bg-[#0a2966] disabled:opacity-50 transition-colors"
          >
            <Save className="w-3.5 h-3.5" />
            {t('Kaydet')}
          </button>
        </div>

        <p className="text-[11px] text-gray-400">
          {t('Eski sistemden kalan MARGIN_RATIO alarmlarınızı')}{' '}
          <Link to="/alarms" className="font-semibold text-[#093eaa] hover:underline">
            {t('Alarmlarım sayfasından')}
          </Link>{' '}
          {t('temizleyebilirsiniz.')}
        </p>
      </div>
    </section>
  );
}
