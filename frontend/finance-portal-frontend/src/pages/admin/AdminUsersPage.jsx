import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useAdminUsers } from './hooks/useAdminUsers';
import AdminPageHeader from './components/AdminPageHeader';
import AdminUsersPanel from './components/AdminUsersPanel';
import BanUserModal from './components/BanUserModal';
import AdminUserDetailModal from './components/AdminUserDetailModal';

export default function AdminUsersPage() {
  const { userId: currentUserId } = useAuth();
  const {
    users,
    loading,
    error,
    searchInput,
    setSearchInput,
    clearSearch,
    statusFilter,
    changeStatusFilter,
    page,
    hasMore,
    goPrevPage,
    goNextPage,
    loadUsers,
    actionUserId,
    banTarget,
    detailUserId,
    openDetail,
    closeDetail,
    requestBan,
    cancelBan,
    confirmBan,
    unban,
  } = useAdminUsers();

  return (
    <section>
      <AdminPageHeader
        title="Kullanıcı Yönetimi"
        description="Keycloak üzerinden kayıtlı kullanıcıları görüntüleyin ve yönetin."
        loading={loading}
        onRefresh={loadUsers}
      />

      <AdminUsersPanel
        users={users}
        loading={loading}
        error={error}
        searchInput={searchInput}
        onSearchInputChange={setSearchInput}
        onSearchClear={clearSearch}
        statusFilter={statusFilter}
        onStatusFilterChange={changeStatusFilter}
        page={page}
        hasMore={hasMore}
        onPrevPage={goPrevPage}
        onNextPage={goNextPage}
        onRetry={loadUsers}
        currentUserId={currentUserId}
        actionUserId={actionUserId}
        onViewDetail={openDetail}
        onRequestBan={requestBan}
        onUnban={unban}
      />

      <AdminUserDetailModal
        userId={detailUserId}
        open={!!detailUserId}
        currentUserId={currentUserId}
        busy={!!actionUserId}
        onClose={closeDetail}
        onRequestBan={requestBan}
        onUnban={unban}
      />

      <BanUserModal
        user={banTarget}
        open={!!banTarget}
        busy={!!actionUserId}
        onClose={cancelBan}
        onConfirm={confirmBan}
      />

      <p className="text-xs text-gray-400 mt-4">
        <Link to="/portfolio" className="hover:text-[#093eaa]">← Portföye dön</Link>
      </p>
    </section>
  );
}
