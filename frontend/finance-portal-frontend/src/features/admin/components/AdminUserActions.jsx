import { Ban, Eye, UserCheck } from 'lucide-react';
import { useTranslation } from '../../../i18n/LanguageContext';
import { isActiveUser } from '../utils/banDisplay';

function canBanUser(user, currentUserId) {
  if (user.id === currentUserId) return false;
  if (user.roles?.includes('ADMIN')) return false;
  return isActiveUser(user);
}

function canUnbanUser(user) {
  return !isActiveUser(user);
}

export default function AdminUserActions({
  user,
  currentUserId,
  busy,
  onViewDetail,
  onRequestBan,
  onUnban,
}) {
  const { t } = useTranslation();
  const isSelf = user.id === currentUserId;
  const isAdminUser = user.roles?.includes('ADMIN');

  return (
    <section className="flex items-center justify-end gap-2">
      <button
        type="button"
        disabled={busy}
        onClick={() => onViewDetail(user)}
        className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-bold bg-blue-50 text-[#093eaa] border border-blue-200 hover:bg-blue-100 disabled:opacity-50"
      >
        <Eye className="w-3.5 h-3.5" />
        {t('Detay')}
      </button>
      {canBanUser(user, currentUserId) && (
        <button
          type="button"
          disabled={busy}
          onClick={() => onRequestBan(user)}
          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-bold bg-rose-50 text-rose-700 border border-rose-200 hover:bg-rose-100 disabled:opacity-50"
        >
          <Ban className="w-3.5 h-3.5" />
          {t('Ban')}
        </button>
      )}
      {canUnbanUser(user) && (
        <button
          type="button"
          disabled={busy}
          onClick={() => onUnban(user)}
          className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200 hover:bg-emerald-100 disabled:opacity-50"
        >
          <UserCheck className="w-3.5 h-3.5" />
          {t('Unban')}
        </button>
      )}
      {isAdminUser && !isSelf && (
        <span className="text-xs text-gray-400 font-medium">{t('Admin')}</span>
      )}
      {isSelf && <span className="text-xs text-gray-400 font-medium">—</span>}
    </section>
  );
}
