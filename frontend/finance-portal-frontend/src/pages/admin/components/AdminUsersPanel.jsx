import AdminUsersSearchBar from './AdminUsersSearchBar';
import AdminUsersTable from './AdminUsersTable';
import AdminUsersPagination from './AdminUsersPagination';
import { AdminLoadingState, AdminErrorState, AdminEmptyState } from './AdminListStates';

export default function AdminUsersPanel({
  users,
  loading,
  error,
  searchInput,
  onSearchInputChange,
  onSearchClear,
  statusFilter,
  onStatusFilterChange,
  page,
  hasMore,
  onPrevPage,
  onNextPage,
  onRetry,
  currentUserId,
  actionUserId,
  onViewDetail,
  onRequestBan,
  onUnban,
}) {
  return (
    <article className="bg-white rounded-2xl shadow-sm border border-gray-200 overflow-hidden">
      <AdminUsersSearchBar
        value={searchInput}
        onChange={onSearchInputChange}
        onClear={onSearchClear}
        statusFilter={statusFilter}
        onStatusFilterChange={onStatusFilterChange}
        resultCount={users.length}
        page={page}
        hasMore={hasMore}
      />

      {loading && <AdminLoadingState />}

      {!loading && error && <AdminErrorState message={error} onRetry={onRetry} />}

      {!loading && !error && users.length === 0 && <AdminEmptyState />}

      {!loading && !error && users.length > 0 && (
        <>
          <AdminUsersTable
            users={users}
            currentUserId={currentUserId}
            actionUserId={actionUserId}
            onViewDetail={onViewDetail}
            onRequestBan={onRequestBan}
            onUnban={onUnban}
          />
          <AdminUsersPagination
            page={page}
            hasMore={hasMore}
            loading={loading}
            onPrev={onPrevPage}
            onNext={onNextPage}
          />
        </>
      )}
    </article>
  );
}
