import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

// ── Bağımlılıklar mock'lanır (test __tests__/ içinde → kaynak 4 üst dizinde) ──
// adminApi: gerçek HTTP client'a inmesin, fonksiyonlar doğrudan stub'lanır.
vi.mock('../../../../api/adminApi', () => ({
  getUsers: vi.fn(),
  banUser: vi.fn(),
  unbanUser: vi.fn(),
}));

// ToastContext: gerçek useToast provider'sız throw eder → mock'la spy'lanabilir success/error.
const toastSuccess = vi.fn();
const toastError = vi.fn();
vi.mock('../../../../context/ToastContext', () => ({
  useToast: () => ({ success: toastSuccess, error: toastError }),
}));

// LanguageContext: t(key, vars) → anahtarı döndür, {var} yer tutucularını doldur (gerçek davranışı taklit).
vi.mock('../../../../context/LanguageContext', () => ({
  useTranslation: () => ({
    t: (key, vars) => {
      if (key == null) return '';
      let out = String(key);
      if (vars && typeof vars === 'object') {
        out = out.replace(/\{(\w+)\}/g, (m, name) =>
          Object.prototype.hasOwnProperty.call(vars, name) ? String(vars[name]) : m
        );
      }
      return out;
    },
  }),
}));

import { getUsers, banUser, unbanUser } from '../../../../api/adminApi';
import { BAN_STATUS_FILTER } from '../../utils/banDisplay';
import { useAdminUsers } from '../useAdminUsers';

// İlk mount'taki async loadUsers'ın oturmasını bekleyen yardımcı.
// loading başlangıçta true, ilk loadUsers (microtask) bitince false olur.
async function renderSettled() {
  const hook = renderHook(() => useAdminUsers());
  await waitFor(() => expect(hook.result.current.loading).toBe(false));
  return hook;
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  // Varsayılan: başarılı, boş liste — her test isterse override eder.
  getUsers.mockResolvedValue({ users: [], hasMore: false });
});

// ── Başlangıç state'i + ilk yükleme ─────────────────────────────────────────
describe('useAdminUsers — başlangıç state ve ilk yükleme', () => {
  it('mount edilince getUsers varsayılan parametrelerle çağrılır ve state dolar', async () => {
    const payload = [{ id: 1, username: 'ali' }, { id: 2, username: 'veli' }];
    getUsers.mockResolvedValue({ users: payload, hasMore: true });

    const { result } = await renderSettled();

    expect(getUsers).toHaveBeenCalledWith({
      search: '',
      first: 0,
      max: 20,
      status: BAN_STATUS_FILTER.ALL,
      withTickets: false,
    });
    expect(result.current.users).toEqual(payload);
    expect(result.current.hasMore).toBe(true);
    expect(result.current.error).toBe('');
    expect(result.current.loading).toBe(false);
  });

  it('varsayılan filtre/sayfa/seçim değerleri doğru başlar', async () => {
    const { result } = await renderSettled();

    expect(result.current.statusFilter).toBe(BAN_STATUS_FILTER.ALL);
    expect(result.current.withTickets).toBe(false);
    expect(result.current.page).toBe(0);
    expect(result.current.searchInput).toBe('');
    expect(result.current.actionUserId).toBeNull();
    expect(result.current.banTarget).toBeNull();
    expect(result.current.detailUserId).toBeNull();
  });

  it('data null/undefined dönerse users=[] ve hasMore=false (?? ve Boolean dalları)', async () => {
    getUsers.mockResolvedValue(undefined);

    const { result } = await renderSettled();

    expect(result.current.users).toEqual([]);
    expect(result.current.hasMore).toBe(false);
  });

  it('data var ama users alanı yoksa boş diziye düşer', async () => {
    getUsers.mockResolvedValue({ hasMore: 1 }); // hasMore truthy ama bool değil

    const { result } = await renderSettled();

    expect(result.current.users).toEqual([]);
    expect(result.current.hasMore).toBe(true); // Boolean(1) === true
  });
});

// ── Hata haritalama (mapLoadError) tüm dalları ──────────────────────────────
describe('useAdminUsers — loadUsers hata dalları', () => {
  it('err.response yoksa "Sunucuya ulaşılamıyor." mesajı set edilir', async () => {
    getUsers.mockRejectedValue({}); // response yok

    const { result } = await renderSettled();

    expect(result.current.error).toBe('Sunucuya ulaşılamıyor.');
    expect(result.current.users).toEqual([]);
    expect(result.current.hasMore).toBe(false);
  });

  it('403 → yönetici yetkisi mesajı', async () => {
    getUsers.mockRejectedValue({ response: { status: 403 } });

    const { result } = await renderSettled();

    expect(result.current.error).toBe('Bu işlem için yönetici yetkisi gerekir.');
  });

  it('401 → oturum süresi mesajı', async () => {
    getUsers.mockRejectedValue({ response: { status: 401 } });

    const { result } = await renderSettled();

    expect(result.current.error).toBe('Oturum süreniz dolmuş olabilir. Lütfen tekrar giriş yapın.');
  });

  it('backend message varsa onu kullanır', async () => {
    getUsers.mockRejectedValue({ response: { status: 500, data: { message: 'patladı' } } });

    const { result } = await renderSettled();

    expect(result.current.error).toBe('patladı');
  });

  it('message yoksa status enterpolasyonlu fallback mesajı', async () => {
    getUsers.mockRejectedValue({ response: { status: 503 } });

    const { result } = await renderSettled();

    expect(result.current.error).toBe('Kullanıcılar yüklenemedi (503).');
  });
});

// ── Arama: debounce + clearSearch ───────────────────────────────────────────
describe('useAdminUsers — arama (debounce/clear)', () => {
  it('setSearchInput anlık searchInput state\'ini günceller', async () => {
    const { result } = await renderSettled();

    act(() => result.current.setSearchInput('ahmet'));

    expect(result.current.searchInput).toBe('ahmet');
  });

  it('debounce dolunca trimlenmiş searchQuery ile yeniden yükleme yapılır', async () => {
    // setTimeout senkron-anında stub (beforeEach) → debounce hemen ateşlenir.
    const { result } = await renderSettled();
    getUsers.mockClear();

    act(() => result.current.setSearchInput('  ahmet  '));
    // Debounce makrotask + sonraki loadUsers'ı bekle.
    await waitFor(() =>
      expect(getUsers).toHaveBeenCalledWith(
        expect.objectContaining({ search: 'ahmet', first: 0 })
      )
    );
  });

  it('clearSearch input/query/page sıfırlar ve yeniden yükler', async () => {
    const { result } = await renderSettled();
    act(() => result.current.setSearchInput('x'));
    getUsers.mockClear();

    act(() => { result.current.clearSearch(); });

    expect(result.current.searchInput).toBe('');
    expect(result.current.page).toBe(0);
    await waitFor(() =>
      expect(getUsers).toHaveBeenCalledWith(expect.objectContaining({ search: '', first: 0 }))
    );
  });
});

// ── Filtreler ───────────────────────────────────────────────────────────────
describe('useAdminUsers — filtreler', () => {
  it('changeStatusFilter statusFilter\'ı değiştirir, page\'i sıfırlar ve yeniden yükler', async () => {
    const { result } = await renderSettled();
    getUsers.mockClear();

    act(() => { result.current.changeStatusFilter(BAN_STATUS_FILTER.BANNED); });

    await waitFor(() => expect(result.current.statusFilter).toBe(BAN_STATUS_FILTER.BANNED));
    expect(result.current.page).toBe(0);
    await waitFor(() =>
      expect(getUsers).toHaveBeenCalledWith(
        expect.objectContaining({ status: BAN_STATUS_FILTER.BANNED })
      )
    );
  });

  it('toggleWithTickets withTickets\'i tersine çevirir ve page\'i sıfırlar', async () => {
    const { result } = await renderSettled();

    act(() => { result.current.toggleWithTickets(); });
    await waitFor(() => expect(result.current.withTickets).toBe(true));

    act(() => { result.current.toggleWithTickets(); });
    await waitFor(() => expect(result.current.withTickets).toBe(false));
    expect(result.current.page).toBe(0);
  });
});

// ── Sayfalama ────────────────────────────────────────────────────────────────
describe('useAdminUsers — sayfalama', () => {
  it('goNextPage hasMore=false iken page\'i artırmaz (erken return)', async () => {
    getUsers.mockResolvedValue({ users: [], hasMore: false });
    const { result } = await renderSettled();

    act(() => result.current.goNextPage());

    expect(result.current.page).toBe(0);
  });

  it('goNextPage hasMore=true iken page\'i 1 artırır', async () => {
    getUsers.mockResolvedValue({ users: [], hasMore: true });
    const { result } = await renderSettled();

    act(() => { result.current.goNextPage(); });

    await waitFor(() => expect(result.current.page).toBe(1));
  });

  it('goPrevPage page\'i azaltır ama 0\'ın altına inmez (Math.max)', async () => {
    getUsers.mockResolvedValue({ users: [], hasMore: true });
    const { result } = await renderSettled();

    // Önce 1'e çık.
    act(() => { result.current.goNextPage(); });
    await waitFor(() => expect(result.current.page).toBe(1));

    // Geri 0'a.
    act(() => { result.current.goPrevPage(); });
    await waitFor(() => expect(result.current.page).toBe(0));

    // 0'da kalır.
    act(() => result.current.goPrevPage());
    expect(result.current.page).toBe(0);
  });
});

// ── Detay aç/kapa ────────────────────────────────────────────────────────────
describe('useAdminUsers — detay aç/kapa', () => {
  it('openDetail detailUserId\'i kullanıcı id\'sine set eder', async () => {
    const { result } = await renderSettled();

    act(() => result.current.openDetail({ id: 42 }));

    expect(result.current.detailUserId).toBe(42);
  });

  it('closeDetail actionUserId yokken detailUserId\'i null yapar', async () => {
    const { result } = await renderSettled();
    act(() => result.current.openDetail({ id: 42 }));

    act(() => result.current.closeDetail());

    expect(result.current.detailUserId).toBeNull();
  });

  it('closeDetail actionUserId varken kapatmaz (erken return)', async () => {
    // banUser'ı asılı bırakarak actionUserId'yi set durumda tut.
    let resolveBan;
    banUser.mockReturnValue(new Promise((r) => { resolveBan = r; }));
    const { result } = await renderSettled();

    act(() => result.current.requestBan({ id: 7 }));
    act(() => { result.current.confirmBan({ reason: 'x' }); });
    // actionUserId = 7 olmasını bekle (confirmBan, asılı banUser'dan önce set eder).
    await waitFor(() => expect(result.current.actionUserId).toBe(7));

    // Bu sırada detay aç ve kapatmayı dene → erken-return, kapanmamalı.
    act(() => result.current.openDetail({ id: 99 }));
    act(() => result.current.closeDetail());
    expect(result.current.detailUserId).toBe(99); // kapanmadı

    // Temizlik: asılı ban'ı çöz (sonraki loadUsers'ı beklemeden — act dışı).
    act(() => { resolveBan({ message: 'ok' }); });
    await waitFor(() => expect(result.current.actionUserId).toBeNull());
  });
});

// ── Ban akışı (requestBan / cancelBan / confirmBan) ─────────────────────────
describe('useAdminUsers — ban akışı', () => {
  it('requestBan banTarget\'ı set eder ve açık detayı kapatır', async () => {
    const { result } = await renderSettled();
    act(() => result.current.openDetail({ id: 5 }));

    act(() => result.current.requestBan({ id: 5, username: 'foo' }));

    expect(result.current.banTarget).toEqual({ id: 5, username: 'foo' });
    expect(result.current.detailUserId).toBeNull();
  });

  it('cancelBan actionUserId yokken banTarget\'ı temizler', async () => {
    const { result } = await renderSettled();
    act(() => result.current.requestBan({ id: 5 }));

    act(() => result.current.cancelBan());

    expect(result.current.banTarget).toBeNull();
  });

  it('confirmBan banTarget yokken hiçbir şey yapmaz (erken return)', async () => {
    const { result } = await renderSettled();

    await act(async () => { await result.current.confirmBan({ reason: 'x' }); });

    expect(banUser).not.toHaveBeenCalled();
    expect(toastSuccess).not.toHaveBeenCalled();
  });

  it('confirmBan başarılı: banUser çağrılır, success toast, banTarget temizlenir, liste yenilenir', async () => {
    banUser.mockResolvedValue({ message: 'Banlandı!' });
    const { result } = await renderSettled();
    act(() => result.current.requestBan({ id: 9 }));
    getUsers.mockClear();

    // confirmBan içinde await loadUsers() var → await act yerine başlat+waitFor (act kilitlenmesin).
    act(() => { result.current.confirmBan({ reason: 'spam', days: 7 }); });

    await waitFor(() => expect(banUser).toHaveBeenCalledWith(9, { reason: 'spam', days: 7 }));
    await waitFor(() => expect(toastSuccess).toHaveBeenCalledWith('Banlandı!'));
    await waitFor(() => expect(result.current.banTarget).toBeNull());
    await waitFor(() => expect(result.current.actionUserId).toBeNull());
    expect(getUsers).toHaveBeenCalled(); // loadUsers tekrar
  });

  it('confirmBan mesajsız yanıtta fallback başarı metnini kullanır', async () => {
    banUser.mockResolvedValue({});
    const { result } = await renderSettled();
    act(() => result.current.requestBan({ id: 9 }));

    act(() => { result.current.confirmBan({}); });

    await waitFor(() => expect(toastSuccess).toHaveBeenCalledWith('Ban işlemi başarılı.'));
  });

  it('confirmBan hata: error toast (backend mesajı), actionUserId sıfırlanır, banTarget kalır', async () => {
    banUser.mockRejectedValue({ response: { data: { message: 'Olmadı' } } });
    const { result } = await renderSettled();
    act(() => result.current.requestBan({ id: 9 }));

    act(() => { result.current.confirmBan({}); });

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Olmadı'));
    await waitFor(() => expect(result.current.actionUserId).toBeNull());
    expect(result.current.banTarget).toEqual({ id: 9 }); // başarısızda temizlenmez
  });

  it('confirmBan hata + backend mesajı yoksa fallback hata metni', async () => {
    banUser.mockRejectedValue(new Error('boom')); // response yok
    const { result } = await renderSettled();
    act(() => result.current.requestBan({ id: 9 }));

    act(() => { result.current.confirmBan({}); });

    await waitFor(() => expect(toastError).toHaveBeenCalledWith('Ban işlemi başarısız.'));
  });
});

// ── Unban akışı (window.confirm dalları) ────────────────────────────────────
describe('useAdminUsers — unban akışı', () => {
  it('confirm iptal edilirse unbanUser çağrılmaz (erken return)', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { result } = await renderSettled();

    await act(async () => { await result.current.unban({ id: 3, username: 'foo' }); });

    expect(unbanUser).not.toHaveBeenCalled();
    expect(result.current.actionUserId).toBeNull();
  });

  it('confirm onaylanırsa unbanUser çağrılır, success toast, detay kapanır, liste yenilenir', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    unbanUser.mockResolvedValue({ message: 'Ban kalktı' });
    const { result } = await renderSettled();
    act(() => result.current.openDetail({ id: 3 }));
    getUsers.mockClear();

    act(() => { result.current.unban({ id: 3, username: 'foo' }); });

    await waitFor(() => expect(unbanUser).toHaveBeenCalledWith(3));
    await waitFor(() => expect(toastSuccess).toHaveBeenCalledWith('Ban kalktı'));
    await waitFor(() => expect(result.current.detailUserId).toBeNull());
    await waitFor(() => expect(result.current.actionUserId).toBeNull());
    expect(getUsers).toHaveBeenCalled();
  });

  it('unban mesajsız yanıtta fallback başarı metni', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    unbanUser.mockResolvedValue({});
    const { result } = await renderSettled();

    await act(async () => { await result.current.unban({ id: 3, username: 'foo' }); });

    expect(toastSuccess).toHaveBeenCalledWith('Ban kaldırıldı.');
  });

  it('unban hata: error toast (backend mesajı), actionUserId sıfırlanır', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    unbanUser.mockRejectedValue({ response: { data: { message: 'Unban hatası' } } });
    const { result } = await renderSettled();

    await act(async () => { await result.current.unban({ id: 3, username: 'foo' }); });

    expect(toastError).toHaveBeenCalledWith('Unban hatası');
    expect(result.current.actionUserId).toBeNull();
  });

  it('unban hata + backend mesajı yoksa fallback hata metni', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    unbanUser.mockRejectedValue(new Error('x')); // response yok
    const { result } = await renderSettled();

    await act(async () => { await result.current.unban({ id: 3, username: 'foo' }); });

    expect(toastError).toHaveBeenCalledWith('Unban işlemi başarısız.');
  });
});

// ── loadUsers manuel çağrı ───────────────────────────────────────────────────
describe('useAdminUsers — loadUsers manuel', () => {
  it('döndürülen loadUsers fonksiyonu elle çağrılınca yeniden fetch eder', async () => {
    const { result } = await renderSettled();
    getUsers.mockClear();
    getUsers.mockResolvedValue({ users: [{ id: 1 }], hasMore: false });

    // loadUsers'ı act içinde başlat, Promise'i bekleme (await loadUsers içindeki setLoading
    // döngüsü mount-debounce ile act'i kilitliyordu) → sonucu waitFor ile doğrula.
    act(() => { result.current.loadUsers(); });

    await waitFor(() => expect(result.current.users).toEqual([{ id: 1 }]));
    expect(getUsers).toHaveBeenCalled();
  });
});
