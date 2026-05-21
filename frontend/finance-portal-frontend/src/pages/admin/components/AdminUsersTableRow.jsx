import { useTranslation } from '../../../i18n/LanguageContext';
import UserStatusBadge from './UserStatusBadge';
import UserRolesCell from './UserRolesCell';
import AdminUserActions from './AdminUserActions';

export default function AdminUsersTableRow({
  user,
  currentUserId,
  actionUserId,
  onViewDetail,
  onRequestBan,
  onUnban,
}) {
  const { t } = useTranslation();
  const isSelf = user.id === currentUserId;
  const busy = actionUserId === user.id;

  return (
    <tr className="hover:bg-gray-50/80 transition-colors">
      <td className="px-4 py-3 font-semibold text-gray-900">
        {user.username ?? '—'}
        {isSelf && (
          <span className="ml-2 text-[10px] font-bold text-[#093eaa] uppercase">{t('Siz')}</span>
        )}
      </td>
      <td className="px-4 py-3 text-gray-600">{user.email ?? '—'}</td>
      <td className="px-4 py-3 text-gray-600">{user.firstName ?? '—'}</td>
      <td className="px-4 py-3 text-gray-600">{user.lastName ?? '—'}</td>
      <td className="px-4 py-3">
        <UserStatusBadge user={user} />
      </td>
      <td className="px-4 py-3">
        <UserRolesCell roles={user.roles} />
      </td>
      <td className="px-4 py-3 text-right">
        <AdminUserActions
          user={user}
          currentUserId={currentUserId}
          busy={busy}
          onViewDetail={onViewDetail}
          onRequestBan={onRequestBan}
          onUnban={onUnban}
        />
      </td>
    </tr>
  );
}
