import { useTranslation } from '../../../context/LanguageContext';
import AdminUsersTableRow from './AdminUsersTableRow';

export default function AdminUsersTable({
  users,
  currentUserId,
  actionUserId,
  onViewDetail,
  onRequestBan,
  onUnban,
}) {
  const { t } = useTranslation();

  const COLUMNS = [
    { key: 'username', label: t('Kullanıcı') },
    { key: 'email', label: t('E-posta') },
    { key: 'firstName', label: t('Ad') },
    { key: 'lastName', label: t('Soyad') },
    { key: 'enabled', label: t('Durum') },
    { key: 'roles', label: t('Roller') },
    { key: 'actions', label: t('İşlem'), align: 'right' },
  ];

  return (
    <section className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="bg-gray-50 text-left text-xs font-bold text-gray-500 uppercase tracking-wider">
            {COLUMNS.map(col => (
              <th
                key={col.key}
                className={`px-4 py-3${col.align === 'right' ? ' text-right' : ''}`}
              >
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-100">
          {users.map(user => (
            <AdminUsersTableRow
              key={user.id}
              user={user}
              currentUserId={currentUserId}
              actionUserId={actionUserId}
              onViewDetail={onViewDetail}
              onRequestBan={onRequestBan}
              onUnban={onUnban}
            />
          ))}
        </tbody>
      </table>
    </section>
  );
}
