import { useEffect, useState } from 'react';
import { Ban, Clock, Infinity, User, X } from 'lucide-react';
import { useTranslation } from '../../../i18n/LanguageContext';

const BAN_TYPE = {
  PERMANENT: 'PERMANENT',
  TEMPORARY: 'TEMPORARY',
};

const DURATION_UNIT = {
  MINUTES: 'MINUTES',
  HOURS: 'HOURS',
  DAYS: 'DAYS',
};

function ModeButton({ active, onClick, disabled, children, className = '' }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`rounded-xl text-sm font-bold transition-all border disabled:opacity-50 ${className} ${
        active
          ? 'border-[#093eaa] bg-[#093eaa]/10 text-[#093eaa] shadow-sm'
          : 'border-gray-200 bg-white text-gray-600 hover:border-gray-300 hover:bg-gray-50'
      }`}
    >
      {children}
    </button>
  );
}

export default function BanUserModal({ user, open, busy, onClose, onConfirm }) {
  const { t } = useTranslation();
  const [banType, setBanType] = useState(BAN_TYPE.TEMPORARY);
  const [durationValue, setDurationValue] = useState('8');
  const [durationUnit, setDurationUnit] = useState(DURATION_UNIT.HOURS);
  const [reason, setReason] = useState('');
  const [formError, setFormError] = useState('');

  const UNIT_OPTIONS = [
    { value: DURATION_UNIT.MINUTES, label: t('Dakika') },
    { value: DURATION_UNIT.HOURS, label: t('Saat') },
    { value: DURATION_UNIT.DAYS, label: t('Gün') },
  ];

  useEffect(() => {
    if (open) {
      setBanType(BAN_TYPE.TEMPORARY);
      setDurationValue('8');
      setDurationUnit(DURATION_UNIT.HOURS);
      setReason('');
      setFormError('');
    }
  }, [open, user?.id]);

  if (!open || !user) return null;

  function handleSubmit(e) {
    e.preventDefault();
    setFormError('');

    const trimmedReason = reason.trim() || undefined;

    if (banType === BAN_TYPE.PERMANENT) {
      onConfirm({ banType: BAN_TYPE.PERMANENT, reason: trimmedReason });
      return;
    }

    const value = Number.parseInt(durationValue, 10);
    if (!Number.isInteger(value) || value <= 0) {
      setFormError(t('Süre pozitif tam sayı olmalıdır.'));
      return;
    }

    onConfirm({
      banType: BAN_TYPE.TEMPORARY,
      durationValue: value,
      durationUnit,
      reason: trimmedReason,
    });
  }

  const summary =
    banType === BAN_TYPE.PERMANENT
      ? {
          tone: 'permanent',
          title: t('Kalıcı ban uygulanacak'),
          description: t('Kullanıcı hesabı süresiz olarak devre dışı bırakılır ve oturumları sonlandırılır.'),
        }
      : {
          tone: 'temporary',
          title: `${t('Geçici ban')}: ${durationValue || '—'} ${
            UNIT_OPTIONS.find(o => o.value === durationUnit)?.label?.toLowerCase() ?? ''
          }`,
          description: t('Süre dolduğunda hesap otomatik olarak yeniden etkinleştirilir.'),
        };

  const summaryStyles = {
    permanent: 'bg-rose-50 border-rose-200 text-rose-800',
    temporary: 'bg-amber-50 border-amber-200 text-amber-900',
  };

  return (
    <section
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#0b1f3a]/50 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="ban-modal-title"
      onClick={e => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <article className="bg-white rounded-2xl shadow-2xl border border-gray-200 w-full max-w-lg overflow-hidden">
        <header className="relative px-5 py-4 border-b border-gray-100 bg-gradient-to-r from-rose-50/80 to-white">
          <div className="flex items-start gap-3 pr-8">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-rose-100 text-rose-600">
              <Ban className="w-5 h-5" />
            </span>
            <div>
              <h2 id="ban-modal-title" className="text-lg font-bold text-gray-900">
                {t('Kullanıcıyı Banla')}
              </h2>
              <p className="text-xs text-gray-500 mt-0.5">{t('Kalıcı veya süreli ban uygulayın')}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={busy}
            className="absolute top-4 right-4 p-1.5 rounded-lg text-gray-400 hover:bg-white hover:text-gray-600"
            aria-label={t('Kapat')}
          >
            <X className="w-5 h-5" />
          </button>
        </header>

        <form onSubmit={handleSubmit} className="p-5 space-y-5">
          <section className="flex items-center gap-3 rounded-xl border border-gray-100 bg-gray-50/80 px-4 py-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[#093eaa]/10 text-[#093eaa]">
              <User className="w-4 h-4" />
            </span>
            <div className="min-w-0">
              <p className="font-bold text-gray-900 truncate">{user.username ?? '—'}</p>
              <p className="text-xs text-gray-600 truncate">
                {[user.firstName, user.lastName].filter(Boolean).join(' ') || t('Ad soyad yok')}
              </p>
              <p className="text-xs text-gray-500 truncate">{user.email ?? t('E-posta yok')}</p>
            </div>
          </section>

          <section className="space-y-2">
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Ban türü')}</p>
            <div className="grid grid-cols-2 gap-2">
              <ModeButton
                active={banType === BAN_TYPE.TEMPORARY}
                disabled={busy}
                onClick={() => {
                  setBanType(BAN_TYPE.TEMPORARY);
                  setFormError('');
                }}
                className="flex items-center justify-center gap-2 px-3 py-3"
              >
                <Clock className="w-4 h-4" />
                {t('Geçici ban')}
              </ModeButton>
              <ModeButton
                active={banType === BAN_TYPE.PERMANENT}
                disabled={busy}
                onClick={() => {
                  setBanType(BAN_TYPE.PERMANENT);
                  setFormError('');
                }}
                className="flex items-center justify-center gap-2 px-3 py-3"
              >
                <Infinity className="w-4 h-4" />
                {t('Kalıcı ban')}
              </ModeButton>
            </div>
          </section>

          {banType === BAN_TYPE.TEMPORARY && (
            <section className="space-y-2">
              <p className="text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Süre')}</p>
              <div className="flex items-center gap-2 rounded-xl border border-gray-200 bg-gray-50 px-3 py-2">
                <input
                  type="number"
                  min={1}
                  step={1}
                  value={durationValue}
                  onChange={e => {
                    setDurationValue(e.target.value);
                    setFormError('');
                  }}
                  placeholder={t('Örn. 8')}
                  className="w-full bg-transparent text-sm font-semibold text-gray-900 focus:outline-none"
                />
                <select
                  value={durationUnit}
                  onChange={e => {
                    setDurationUnit(e.target.value);
                    setFormError('');
                  }}
                  className="shrink-0 text-xs font-bold text-gray-600 bg-white border border-gray-200 rounded-lg px-2 py-1.5 focus:outline-none focus:ring-2 focus:ring-[#093eaa]"
                >
                  {UNIT_OPTIONS.map(option => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
            </section>
          )}

          <section className="space-y-2">
            <p className="text-xs font-bold text-gray-500 uppercase tracking-wider">{t('Ban sebebi')}</p>
            <textarea
              value={reason}
              onChange={e => setReason(e.target.value)}
              disabled={busy}
              rows={3}
              maxLength={512}
              placeholder={t('Kullanıcıya iletilecek ban sebebi (opsiyonel)')}
              className="w-full rounded-xl border border-gray-200 bg-gray-50 px-3 py-2 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-[#093eaa] resize-none disabled:opacity-50"
            />
            <p className="text-[11px] text-gray-400">
              {t('Bu sebep kullanıcıya e-posta ile bildirilir ve admin panelinde saklanır.')}
            </p>
          </section>

          <section className={`rounded-xl border px-4 py-3 text-sm ${summaryStyles[summary.tone]}`}>
            <p className="font-bold">{summary.title}</p>
            <p className="mt-1 text-xs leading-relaxed opacity-90">{summary.description}</p>
          </section>

          {formError && (
            <p className="text-xs font-semibold text-rose-600 bg-rose-50 border border-rose-100 rounded-lg px-3 py-2">
              {formError}
            </p>
          )}

          <footer className="flex gap-2 pt-1">
            <button
              type="button"
              onClick={onClose}
              disabled={busy}
              className="flex-1 px-4 py-2.5 rounded-xl text-sm font-bold border border-gray-200 text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              {t('İptal')}
            </button>
            <button
              type="submit"
              disabled={busy}
              className="flex-1 px-4 py-2.5 rounded-xl text-sm font-bold bg-rose-600 text-white hover:bg-rose-700 shadow-sm disabled:opacity-50"
            >
              {busy ? t('Banlanıyor...') : t('Banı Uygula')}
            </button>
          </footer>
        </form>
      </article>
    </section>
  );
}
