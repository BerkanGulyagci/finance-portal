import { useEffect, useState } from 'react';
import { Ban, User, UserCheck, X } from 'lucide-react';
import { getUser } from '../../../api/adminApi';
import { useTranslation } from '../../../i18n/LanguageContext';
import UserStatusBadge from './UserStatusBadge';
import UserRolesCell from './UserRolesCell';
import { formatBanUntil, isActiveUser, isTemporaryBan } from '../utils/banDisplay';

function DetailField({ label, value, mono = false }) {
  return (
    <div className="py-2.5 border-b border-gray-100 last:border-0">
      <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-0.5">{label}</p>
      <p className={`text-sm font-semibold text-gray-900 break-words ${mono ? 'font-mono text-xs' : ''}`}>
        {value ?? '—'}
      </p>
    </div>
  );
}

function canBanUser(user, currentUserId) {
  if (!user) return false;
  if (user.id === currentUserId) return false;
  if (user.roles?.includes('ADMIN')) return false;
  return isActiveUser(user);
}

function canUnbanUser(user) {
  return user && !isActiveUser(user);
}

export default function AdminUserDetailModal({
  userId,
  open,
  currentUserId,
  busy,
  onClose,
  onRequestBan,
  onUnban,
}) {
  const { t } = useTranslation();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open || !userId) {
      setUser(null);
      setError('');
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError('');

    getUser(userId)
      .then(data => {
        if (!cancelled) setUser(data);
      })
      .catch(err => {
        if (!cancelled) {
          setError(err.response?.data?.message || t('Kullanıcı bilgisi yüklenemedi.'));
          setUser(null);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [open, userId, t]);

  if (!open) return null;

  return (
    <section
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#0b1f3a]/50 backdrop-blur-[2px]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="user-detail-title"
      onClick={e => {
        if (e.target === e.currentTarget && !busy) onClose();
      }}
    >
      <article className="bg-white rounded-2xl shadow-2xl border border-gray-200 w-full max-w-md max-h-[90vh] overflow-hidden flex flex-col">
        <header className="relative px-5 py-4 border-b border-gray-100 bg-gradient-to-r from-blue-50/80 to-white shrink-0">
          <div className="flex items-start gap-3 pr-8">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-[#093eaa]/10 text-[#093eaa]">
              <User className="w-5 h-5" />
            </span>
            <div className="min-w-0">
              <h2 id="user-detail-title" className="text-lg font-bold text-gray-900 truncate">
                {t('Kullanıcı Detayı')}
              </h2>
              <p className="text-xs text-gray-500 mt-0.5 truncate">
                {loading ? t('Yükleniyor...') : user?.username ?? '—'}
              </p>
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

        <div className="p-5 overflow-y-auto flex-1">
          {loading && (
            <p className="text-sm text-gray-500 text-center py-8">{t('Kullanıcı bilgileri yükleniyor...')}</p>
          )}

          {!loading && error && (
            <p className="text-sm text-red-600 bg-red-50 border border-red-100 rounded-xl px-4 py-3">{error}</p>
          )}

          {!loading && !error && user && (
            <>
              <div className="flex items-center justify-between gap-2 mb-4">
                <UserStatusBadge user={user} />
                {user.id === currentUserId && (
                  <span className="text-[10px] font-bold text-[#093eaa] uppercase">{t('Siz')}</span>
                )}
              </div>

              <DetailField label={t('Kullanıcı adı')} value={user.username} />
              <DetailField label={t('Ad')} value={user.firstName} />
              <DetailField label={t('Soyad')} value={user.lastName} />
              <DetailField label={t('E-posta')} value={user.email} />
              <DetailField
                label={t('E-posta doğrulama')}
                value={user.emailVerified ? t('Doğrulandı') : t('Doğrulanmadı')}
              />
              <DetailField label={t('Keycloak hesap')} value={user.enabled ? t('Etkin') : t('Devre dışı')} />
              {isTemporaryBan(user) && (
                <DetailField label={t('Ban bitiş')} value={formatBanUntil(user.banUntil) || '—'} />
              )}
              {!isActiveUser(user) && user.banReason && (
                <DetailField label={t('Ban sebebi')} value={user.banReason} />
              )}
              <div className="py-2.5 border-b border-gray-100">
                <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-1">{t('Roller')}</p>
                <UserRolesCell roles={user.roles} />
              </div>
              <DetailField label={t('Kullanıcı ID')} value={user.id} mono />
            </>
          )}
        </div>

        {!loading && !error && user && (
          <footer className="flex flex-wrap gap-2 p-5 border-t border-gray-100 shrink-0">
            {canBanUser(user, currentUserId) && (
              <button
                type="button"
                disabled={busy}
                onClick={() => onRequestBan(user)}
                className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-sm font-bold bg-rose-600 text-white hover:bg-rose-700 disabled:opacity-50"
              >
                <Ban className="w-4 h-4" />
                {t('Banla')}
              </button>
            )}
            {canUnbanUser(user) && (
              <button
                type="button"
                disabled={busy}
                onClick={() => onUnban(user)}
                className="inline-flex items-center gap-1.5 px-4 py-2.5 rounded-xl text-sm font-bold bg-emerald-600 text-white hover:bg-emerald-700 disabled:opacity-50"
              >
                <UserCheck className="w-4 h-4" />
                {t('Unban')}
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              disabled={busy}
              className="ml-auto px-4 py-2.5 rounded-xl text-sm font-bold border border-gray-200 text-gray-700 hover:bg-gray-50 disabled:opacity-50"
            >
              {t('Kapat')}
            </button>
          </footer>
        )}
      </article>
    </section>
  );
}
