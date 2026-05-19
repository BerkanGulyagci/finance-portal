import AdminUsersSearchBar from './AdminUsersSearchBar';
import AdminUsersTable from './AdminUsersTable';
import { AdminLoadingState, AdminErrorState, AdminEmptyState } from './AdminListStates';

export default function AdminUsersPanel({
  users,
  loading,
  error,
  searchInput,
  onSearchInputChange,
  onSearchSubmit,
  onRetry,
  currentUserId,
  actionUserId,
  onRequestBan,
  onUnban,
}) {
  return (
    <article className="bg-white rounded-2xl shadow-sm border border-gray-200 overflow-hidden">
      <AdminUsersSearchBar
        value={searchInput}
        onChange={onSearchInputChange}
        onSubmit={onSearchSubmit}
        resultCount={users.length}
      />

      {loading && <AdminLoadingState />}

      {!loading && error && <AdminErrorState message={error} onRetry={onRetry} />}

      {!loading && !error && users.length === 0 && <AdminEmptyState />}

      {!loading && !error && users.length > 0 && (
        <AdminUsersTable
          users={users}
          currentUserId={currentUserId}
          actionUserId={actionUserId}
          onRequestBan={onRequestBan}
          onUnban={onUnban}
        />
      )}
    </article>
  );
}
