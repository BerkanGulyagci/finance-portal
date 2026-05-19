import AdminUsersTableRow from './AdminUsersTableRow';

const COLUMNS = [
  { key: 'username', label: 'Kullanıcı' },
  { key: 'email', label: 'E-posta' },
  { key: 'firstName', label: 'Ad' },
  { key: 'lastName', label: 'Soyad' },
  { key: 'enabled', label: 'Durum' },
  { key: 'roles', label: 'Roller' },
  { key: 'actions', label: 'İşlem', align: 'right' },
];

export default function AdminUsersTable({
  users,
  currentUserId,
  actionUserId,
  onViewDetail,
  onRequestBan,
  onUnban,
}) {
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
