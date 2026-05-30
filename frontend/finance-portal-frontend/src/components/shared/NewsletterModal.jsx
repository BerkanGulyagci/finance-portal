import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { X, Mail, Calendar } from 'lucide-react';
import { getNewsletter, updateNewsletter } from '../../api/newsletterApi';
import { useToast } from '../../context/ToastContext';
import { useTranslation } from '../../context/LanguageContext';

const FREQS = [
  { key: 'DAILY', label: 'Günlük', sub: 'Her sabah' },
  { key: 'WEEKLY', label: 'Haftalık', sub: 'Her Pazartesi' },
  { key: 'MONTHLY', label: 'Aylık', sub: 'Ayın ilk günü' },
];

/**
 * Bülten aboneliği modalı — kullanıcı dashboard özetini hangi sıklıkta e-posta ile
 * almak istediğini seçer. Tercih backend'e (/api/newsletter/me) kaydedilir; seçilen
 * sıklıkta zamanlanmış iş (NewsletterDigestService) özet e-postasını gönderir.
 */
export default function NewsletterModal({ email, onClose, onSaved }) {
  const { t } = useTranslation();
  const toast = useToast();
  const [freq, setFreq] = useState('WEEKLY');
  const [subscribed, setSubscribed] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let on = true;
    getNewsletter()
      .then(d => { if (on && d) { setFreq(d.frequency || 'WEEKLY'); setSubscribed(!!d.subscribed); } })
      .catch(() => { /* abone değil / doğrulanmamış — varsayılan kalsın */ });
    return () => { on = false; };
  }, []);

  function errMsg(e) {
    const m = e?.response?.data?.message || '';
    if (/EMAIL_NOT_VERIFIED/i.test(m)) return t('Önce e-posta adresinizi doğrulamanız gerekiyor.');
    return t('İşlem başarısız oldu.');
  }

  async function commit(enabled) {
    setBusy(true);
    try {
      await updateNewsletter(freq, enabled);
      toast.success(enabled ? t('Bülten tercihiniz kaydedildi.') : t('Bülten aboneliğiniz iptal edildi.'));
      onSaved?.();
      onClose();
    } catch (e) {
      toast.error(errMsg(e));
    } finally {
      setBusy(false);
    }
  }

  return createPortal((
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/30" onClick={onClose}>
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md p-6 m3-scale-in" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="w-10 h-10 rounded-2xl bg-[#093eaa]/10 text-[#093eaa] flex items-center justify-center shrink-0">
              <Mail className="w-5 h-5" />
            </div>
            <div className="min-w-0">
              <h2 className="text-lg font-bold text-gray-900">{t('Bülten Aboneliği')}</h2>
              <p className="text-xs text-gray-500">{t('Panonuzun özetini e-posta ile alın')}</p>
            </div>
          </div>
          <button onClick={onClose} className="p-1 rounded-md text-gray-400 hover:text-gray-700 hover:bg-gray-100 shrink-0">
            <X className="w-5 h-5" />
          </button>
        </div>

        {email && <p className="text-xs text-gray-400 mt-3 truncate">{email}</p>}

        <p className="text-sm font-semibold text-gray-700 mt-4 mb-2">{t('Ne sıklıkla özet almak istersiniz?')}</p>
        <div className="space-y-2">
          {FREQS.map(f => (
            <button
              key={f.key}
              onClick={() => setFreq(f.key)}
              className={`w-full flex items-center justify-between gap-2 rounded-xl border px-3 py-2.5 text-left transition-colors ${
                freq === f.key ? 'border-[#093eaa] bg-[#093eaa]/5' : 'border-gray-200 hover:bg-gray-50'
              }`}
            >
              <span className="flex items-center gap-2">
                <Calendar className={`w-4 h-4 ${freq === f.key ? 'text-[#093eaa]' : 'text-gray-400'}`} />
                <span className="text-sm font-semibold text-gray-900">{t(f.label)}</span>
              </span>
              <span className="text-[11px] text-gray-400">{t(f.sub)}</span>
            </button>
          ))}
        </div>

        <p className="text-[11px] text-gray-400 mt-3">{t('Bu tercihi daha sonra Profil ayarlarından değiştirebilirsiniz.')}</p>

        <div className="flex items-center gap-2 mt-4">
          {subscribed && (
            <button onClick={() => commit(false)} disabled={busy}
              className="text-sm font-semibold text-rose-600 hover:bg-rose-50 rounded-lg px-3 py-2 transition-colors disabled:opacity-50">
              {t('Abonelikten Çık')}
            </button>
          )}
          <button onClick={() => commit(true)} disabled={busy}
            className="ml-auto inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#093eaa] text-white text-sm font-bold hover:bg-[#0a2966] transition-colors disabled:opacity-50">
            {t('Aboneliği Kaydet')}
          </button>
        </div>
      </div>
    </div>
  ), document.body);
}
