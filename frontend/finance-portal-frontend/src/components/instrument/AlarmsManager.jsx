import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { Bell, Plus, Pencil, Trash2, Pause, Play, ChevronRight } from 'lucide-react';
import { getAlarms, updateAlarm, deleteAlarm } from '../../api/alarmApi';
import { useTranslation } from '../../context/LanguageContext';
import { useToast } from '../../context/ToastContext';
import AlarmCreateModal from './AlarmCreateModal';
import InstrumentSearchModal from './InstrumentSearchModal';

const METRIC_LABEL = { PRICE: 'Fiyat', CHANGE_PERCENT: 'Değişim', VOLUME: 'Hacim', MARGIN_RATIO: 'Teminat Oranı' };
const METRIC_WORD = { PRICE: 'fiyat', CHANGE_PERCENT: 'değişim', VOLUME: 'hacim', MARGIN_RATIO: 'teminat oranı' };
const ASSET_LABEL = {
  STOCK: 'Hisse', FUND: 'Fon', FX: 'Döviz', FUTURE: 'Vadeli',
  CRYPTO: 'Kripto', GOLD: 'Altın', COMMODITY: 'Emtia', BOND: 'Tahvil',
};

function fmt(v) {
  if (v == null) return '-';
  const n = Number(v);
  return Number.isFinite(n) ? n.toLocaleString('tr-TR', { maximumFractionDigits: 6 }) : '-';
}

// Eşik/değer son eki: % (değişim ve teminat oranı), boş (hacim) ya da " ₺"/" $" (fiyat).
function unitSuffix(a) {
  if (a.metric === 'CHANGE_PERCENT') return '%';
  if (a.metric === 'MARGIN_RATIO') return '%';
  if (a.metric === 'VOLUME') return '';
  return a.currencySymbol ? ` ${a.currencySymbol}` : '';
}

// MARGIN_RATIO için backend 0-1 ondalık tutar; ekrana yüzde olarak çevirmek gerekir.
function displayValueFor(metric, v) {
  if (v == null) return v;
  const n = Number(v);
  if (!Number.isFinite(n)) return v;
  if (metric === 'MARGIN_RATIO') return n * 100;
  return n;
}

function StatusBadge({ status, t }) {
  const map = {
    ACTIVE: { label: 'Aktif', cls: 'bg-emerald-50 text-emerald-700 border-emerald-200' },
    TRIGGERED: { label: 'Tetiklendi', cls: 'bg-[#093eaa]/10 text-[#093eaa] border-[#093eaa]/20' },
    DISABLED: { label: 'Duraklatıldı', cls: 'bg-gray-100 text-gray-500 border-gray-200' },
  };
  const s = map[status] ?? map.ACTIVE;
  return <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full border ${s.cls}`}>{t(s.label)}</span>;
}

/**
 * Alarm listesi + yönetim (yeni / düzenle / duraklat / sil). Hem /alarms sayfasında
 * (full) hem portföy sayfasında (compact) kullanılır.
 *
 * @param compact true → kısaltılmış liste + "Tümünü Yönet" linki
 */
export default function AlarmsManager({ compact = false }) {
  const { t } = useTranslation();
  const toast = useToast();

  const [alarms, setAlarms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchOpen, setSearchOpen] = useState(false);
  const [newInstrument, setNewInstrument] = useState(null);
  const [editAlarm, setEditAlarm] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const refresh = useCallback(() => {
    setLoading(true);
    getAlarms()
      .then(setAlarms)
      .catch(() => setAlarms([]))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { refresh(); }, [refresh]);

  async function togglePause(a) {
    setBusyId(a.id);
    try {
      await updateAlarm(a.id, { status: a.status === 'DISABLED' ? 'ACTIVE' : 'DISABLED' });
      refresh();
    } catch {
      toast.error(t('İşlem başarısız.'));
    } finally {
      setBusyId(null);
    }
  }

  async function remove(a) {
    if (!window.confirm(t('Bu alarmı silmek istediğinize emin misiniz?'))) return;
    setBusyId(a.id);
    try {
      await deleteAlarm(a.id);
      toast.success(t('Alarm silindi.'));
      refresh();
    } catch {
      toast.error(t('Alarm silinemedi.'));
    } finally {
      setBusyId(null);
    }
  }

  const list = compact ? alarms.slice(0, 4) : alarms;

  return (
    <div>
      {/* Başlık + Yeni Alarm */}
      <div className="flex items-center justify-between gap-2 mb-4 flex-wrap">
        <h2 className="font-bold text-gray-900 flex items-center gap-2">
          <Bell className="w-4 h-4 text-[#093eaa]" /> {t('Alarmlarım')}
          {alarms.length > 0 && <span className="text-xs font-semibold text-gray-400">({alarms.length})</span>}
        </h2>
        <button
          onClick={() => setSearchOpen(true)}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-[#093eaa] text-white text-xs font-bold hover:bg-[#0a2966] transition-colors"
        >
          <Plus className="w-3.5 h-3.5" /> {t('Yeni Alarm')}
        </button>
      </div>

      {loading ? (
        <p className="text-sm text-gray-400 py-6 text-center">{t('Yükleniyor...')}</p>
      ) : alarms.length === 0 ? (
        <div className="text-center py-8 text-sm text-gray-400">
          <Bell className="w-8 h-8 mx-auto mb-2 text-gray-300" />
          {t('Henüz alarm kurmadınız.')}
        </div>
      ) : (
        <div className="space-y-2">
          {list.map(a => (
            <div key={a.id} className="flex items-center justify-between gap-3 p-3 rounded-xl border border-gray-200 hover:border-gray-300 transition-all">
              <div className="min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-bold text-gray-900 truncate">{a.instrumentName || a.symbol}</span>
                  <span className="text-[10px] font-semibold text-gray-400 bg-gray-100 px-1.5 py-0.5 rounded">
                    {t(ASSET_LABEL[a.assetType] ?? a.assetType)}
                  </span>
                  <StatusBadge status={a.status} t={t} />
                </div>
                <p className="text-xs text-gray-500 mt-0.5">
                  {t('Bu alarm')} {t(METRIC_WORD[a.metric] ?? 'fiyat')}{' '}
                  <span className="font-semibold text-gray-700">{fmt(displayValueFor(a.metric, a.threshold))}{unitSuffix(a)}</span>{t("'nin")}{' '}
                  {a.direction === 'ABOVE' ? t('üzerine çıkarsa') : t('altına inerse')} {t('tetiklenecektir.')}
                  {a.frequency === 'RECURRING' && <span className="ml-1 text-gray-400">· {t('sürekli')}</span>}
                  {a.lastObservedValue != null && (
                    <span className="ml-1 text-gray-400">· {t('şu an')}: {fmt(displayValueFor(a.metric, a.lastObservedValue))}{unitSuffix(a)}</span>
                  )}
                </p>
                {a.note && (
                  <p className="text-[11px] text-gray-400 mt-0.5 truncate">{t('Alarm Notunuz:')} {a.note}</p>
                )}
              </div>
              <div className="flex items-center gap-1 flex-shrink-0">
                <button onClick={() => setEditAlarm(a)} disabled={busyId === a.id}
                  title={t('Düzenle')} className="p-2 sm:p-1.5 rounded-lg text-gray-400 hover:text-[#093eaa] hover:bg-[#093eaa]/5 transition-colors disabled:opacity-40">
                  <Pencil className="w-4 h-4" />
                </button>
                <button onClick={() => togglePause(a)} disabled={busyId === a.id || a.status === 'TRIGGERED'}
                  title={a.status === 'DISABLED' ? t('Devam Et') : t('Duraklat')}
                  className="p-2 sm:p-1.5 rounded-lg text-gray-400 hover:text-amber-600 hover:bg-amber-50 transition-colors disabled:opacity-40">
                  {a.status === 'DISABLED' ? <Play className="w-4 h-4" /> : <Pause className="w-4 h-4" />}
                </button>
                <button onClick={() => remove(a)} disabled={busyId === a.id}
                  title={t('Sil')} className="p-2 sm:p-1.5 rounded-lg text-gray-400 hover:text-rose-600 hover:bg-rose-50 transition-colors disabled:opacity-40">
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {compact && alarms.length > list.length && (
        <Link to="/alarms" className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-[#093eaa] hover:underline">
          {t('Tümünü Yönet')} <ChevronRight className="w-4 h-4" />
        </Link>
      )}

      {/* Yeni alarm: enstrüman seç → alarm formu */}
      {searchOpen && (
        <InstrumentSearchModal
          portfolioName={t('alarm')}
          onSelect={(inst) => { setSearchOpen(false); setNewInstrument(inst); }}
          onClose={() => setSearchOpen(false)}
        />
      )}
      {newInstrument && (
        <AlarmCreateModal
          instrument={newInstrument}
          onClose={() => setNewInstrument(null)}
          onSaved={refresh}
        />
      )}
      {editAlarm && (
        <AlarmCreateModal
          alarm={editAlarm}
          onClose={() => setEditAlarm(null)}
          onSaved={refresh}
        />
      )}
    </div>
  );
}
