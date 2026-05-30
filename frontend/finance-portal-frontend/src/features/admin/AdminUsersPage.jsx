import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useTranslation } from '../../context/LanguageContext';
import { useAdminUsers } from './hooks/useAdminUsers';
import AdminPageHeader from './components/AdminPageHeader';
import AdminUsersPanel from './components/AdminUsersPanel';
import BanUserModal from './components/BanUserModal';
import AdminUserDetailModal from './components/AdminUserDetailModal';

export default function AdminUsersPage() {
  const { t } = useTranslation();
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
    withTickets,
    toggleWithTickets,
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
        title={t('Kullanıcı Yönetimi')}
        description={t('Keycloak üzerinden kayıtlı kullanıcıları görüntüleyin ve yönetin.')}
        loading={loading}
        onRefresh={loadUsers}
      />

      <div className="flex gap-2 mb-5">
        <span className="px-3 py-2 rounded-xl text-sm font-bold bg-[#093eaa] text-white">{t('Kullanıcılar')}</span>
        <Link to="/admin/eurobonds" className="px-3 py-2 rounded-xl text-sm font-bold bg-white border border-gray-200 text-gray-600 hover:bg-gray-50">{t('Eurobond')}</Link>
      </div>

      <AdminUsersPanel
        users={users}
        loading={loading}
        error={error}
        searchInput={searchInput}
        onSearchInputChange={setSearchInput}
        onSearchClear={clearSearch}
        statusFilter={statusFilter}
        onStatusFilterChange={changeStatusFilter}
        withTickets={withTickets}
        onToggleWithTickets={toggleWithTickets}
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
        <Link to="/portfolio" className="hover:text-[#093eaa]">← {t('Portföye dön')}</Link>
      </p>
    </section>
  );
}
