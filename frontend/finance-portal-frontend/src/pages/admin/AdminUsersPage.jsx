import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useAdminUsers } from './hooks/useAdminUsers';
import AdminPageHeader from './components/AdminPageHeader';
import AdminUsersPanel from './components/AdminUsersPanel';
import BanUserModal from './components/BanUserModal';

export default function AdminUsersPage() {
  const { userId: currentUserId } = useAuth();
  const {
    users,
    loading,
    error,
    searchInput,
    setSearchInput,
    submitSearch,
    loadUsers,
    actionUserId,
    banTarget,
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
        onSearchSubmit={submitSearch}
        onRetry={loadUsers}
        currentUserId={currentUserId}
        actionUserId={actionUserId}
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
