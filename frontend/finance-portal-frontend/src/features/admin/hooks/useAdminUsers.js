import { useCallback, useEffect, useState } from 'react';
import { banUser, getUsers, unbanUser } from '../../../api/adminApi';
import { useToast } from '../../../context/ToastContext';
import { useTranslation } from '../../../context/LanguageContext';
import { BAN_STATUS_FILTER } from '../utils/banDisplay';

const PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 400;

function mapLoadError(err, t) {
  if (!err.response) return t('Sunucuya ulaşılamıyor.');
  if (err.response.status === 403) return t('Bu işlem için yönetici yetkisi gerekir.');
  if (err.response.status === 401) return t('Oturum süreniz dolmuş olabilir. Lütfen tekrar giriş yapın.');
  return err.response?.data?.message || t('Kullanıcılar yüklenemedi ({status}).', { status: err.response.status });
}

export function useAdminUsers() {
  const { t } = useTranslation();
  const toast = useToast();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState(BAN_STATUS_FILTER.ALL);
  const [withTickets, setWithTickets] = useState(false);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [actionUserId, setActionUserId] = useState(null);
  const [banTarget, setBanTarget] = useState(null);
  const [detailUserId, setDetailUserId] = useState(null);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getUsers({
        search: searchQuery,
        first: page * PAGE_SIZE,
        max: PAGE_SIZE,
        status: statusFilter,
        withTickets,
      });
      setUsers(data?.users ?? []);
      setHasMore(Boolean(data?.hasMore));
    } catch (err) {
      setError(mapLoadError(err, t));
      setUsers([]);
      setHasMore(false);
    } finally {
      setLoading(false);
    }
  }, [searchQuery, page, statusFilter, withTickets, t]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setSearchQuery(searchInput.trim());
      setPage(0);
    }, SEARCH_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [searchInput]);

  function clearSearch() {
    setSearchInput('');
    setSearchQuery('');
    setPage(0);
  }

  function changeStatusFilter(next) {
    setStatusFilter(next);
    setPage(0);
  }

  function toggleWithTickets() {
    setWithTickets(v => !v);
    setPage(0);
  }

  function goPrevPage() {
    setPage(current => Math.max(0, current - 1));
  }

  function goNextPage() {
    if (!hasMore) return;
    setPage(current => current + 1);
  }

  function openDetail(user) {
    setDetailUserId(user.id);
  }

  function closeDetail() {
    if (actionUserId) return;
    setDetailUserId(null);
  }

  function requestBan(user) {
    setDetailUserId(null);
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
      const res = await banUser(banTarget.id, banPayload);
      toast.success(res?.message || t('Ban işlemi başarılı.'));
      setBanTarget(null);
      await loadUsers();
    } catch (err) {
      toast.error(err.response?.data?.message || t('Ban işlemi başarısız.'));
    } finally {
      setActionUserId(null);
    }
  }

  async function unban(user) {
    if (!window.confirm(t('{username} kullanıcısının banını kaldırmak istediğinize emin misiniz?', { username: user.username }))) return;
    setDetailUserId(null);
    setActionUserId(user.id);
    try {
      const res = await unbanUser(user.id);
      toast.success(res?.message || t('Ban kaldırıldı.'));
      await loadUsers();
    } catch (err) {
      toast.error(err.response?.data?.message || t('Unban işlemi başarısız.'));
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
  };
}
