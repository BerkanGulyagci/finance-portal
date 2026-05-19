import { useCallback, useEffect, useState } from 'react';
import { banUser, getUsers, unbanUser } from '../../../api/adminApi';

function mapLoadError(err) {
  if (!err.response) return 'Sunucuya ulaşılamıyor.';
  if (err.response.status === 403) return 'Bu işlem için yönetici yetkisi gerekir.';
  if (err.response.status === 401) return 'Oturum süreniz dolmuş olabilir. Lütfen tekrar giriş yapın.';
  return err.response?.data?.message || `Kullanıcılar yüklenemedi (${err.response.status}).`;
}

export function useAdminUsers() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [actionUserId, setActionUserId] = useState(null);
  const [banTarget, setBanTarget] = useState(null);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getUsers({ search: searchQuery, first: 0, max: 50 });
      setUsers(data?.users ?? []);
    } catch (err) {
      setError(mapLoadError(err));
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, [searchQuery]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  function submitSearch(e) {
    e.preventDefault();
    setSearchQuery(searchInput.trim());
  }

  function requestBan(user) {
    setBanTarget(user);
  }

  function cancelBan() {
    if (actionUserId) return;
    setBanTarget(null);
  }

  async function confirmBan(banPayload) {
    if (!banTarget) return;
    setActionUserId(banTarget.id);
    try {
      await banUser(banTarget.id, banPayload);
      setBanTarget(null);
      await loadUsers();
    } catch (err) {
      alert(err.response?.data?.message || 'Ban işlemi başarısız.');
    } finally {
      setActionUserId(null);
    }
  }

  async function unban(user) {
    if (!window.confirm(`${user.username} kullanıcısının banını kaldırmak istediğinize emin misiniz?`)) return;
    setActionUserId(user.id);
    try {
      await unbanUser(user.id);
      await loadUsers();
    } catch (err) {
      alert(err.response?.data?.message || 'Unban işlemi başarısız.');
    } finally {
      setActionUserId(null);
    }
  }

  return {
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
  };
}
