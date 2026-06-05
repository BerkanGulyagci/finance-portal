import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

// ── Mock'lar ─────────────────────────────────────────────────────────────────
// ProfilePage; useAuth (isAuthenticated/clearLocalSession), iki API modülü
// (meApi.getMe / newsletterApi.getNewsletter), üç hesap modalı, TickerCustomizer
// ve SupportTicketsCard (kendileri ayrıca API/localStorage çekiyor) ile
// NewsletterModal çeker. jsdom'da ağ yoktur → veri kaynaklarını ve ağır/ağ-bağlı
// alt komponentleri stub'larız. Böylece test SADECE ProfilePage'in kendi
// mantığını doğrular: yükleniyor/hata/dolu dalları, hero kart (baş harfler +
// isim + rozetler), hesap bilgileri alanları, modal aç/kapa, yenile, redirect.
// LanguageProvider + MemoryRouter GERÇEK kalır → t(key) tr'de anahtarın kendisini
// döndürür ve gerçek router (useNavigate/useSearchParams/Link) çalışır.

// Auth durumu testten teste değişebilsin diye mutable holder.
const authState = { isAuthenticated: true, clearLocalSession: vi.fn() };
vi.mock('../../../context/AuthContext', () => ({
  useAuth: () => authState,
}));

const getMe = vi.fn();
vi.mock('../../../api/meApi', () => ({
  getMe: (...a) => getMe(...a),
}));

const getNewsletter = vi.fn();
vi.mock('../../../api/newsletterApi', () => ({
  getNewsletter: (...a) => getNewsletter(...a),
}));

// Üç hesap modalı: yalnızca tanınabilir işaret + props köprüsü. open=false iken
// hiçbir şey çizmemeleri, ProfilePage'in modal-açık state'ini doğrulamamızı sağlar.
vi.mock('../components/ProfileAccountModals', () => ({
  ProfileNameModal: ({ open, onClose, onSuccess }) =>
    open ? (
      <div data-testid="name-modal">
        <button onClick={onClose}>close-name</button>
        <button onClick={() => onSuccess('Ad güncellendi')}>save-name</button>
      </div>
    ) : null,
  ProfileEmailModal: ({ open, onClose }) =>
    open ? (
      <div data-testid="email-modal">
        <button onClick={onClose}>close-email</button>
      </div>
    ) : null,
  ProfilePasswordModal: ({ open, onClose }) =>
    open ? (
      <div data-testid="password-modal">
        <button onClick={onClose}>close-password</button>
      </div>
    ) : null,
}));

vi.mock('../components/TickerCustomizer', () => ({
  default: () => <div data-testid="ticker-customizer">ticker</div>,
}));

vi.mock('../components/SupportTicketsCard', () => ({
  default: () => <div data-testid="support-tickets-card">support</div>,
}));

vi.mock('../../../components/shared/NewsletterModal', () => ({
  default: ({ onClose }) => (
    <div data-testid="newsletter-modal">
      <button onClick={onClose}>close-newsletter</button>
    </div>
  ),
}));

import ProfilePage from '../ProfilePage';
import { LanguageProvider } from '../../../context/LanguageContext';

// "/login" rotasını da tanımlarız ki unauthenticated/401 redirect'inin
// gerçekten oraya gittiğini DOM üzerinden doğrulayabilelim.
function renderPage(initialEntries = ['/profile']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <LanguageProvider>
        <Routes>
          <Route path="/profile" element={<ProfilePage />} />
          <Route path="/login" element={<div>LOGIN SAYFASI</div>} />
          <Route path="/verify-email" element={<div>VERIFY SAYFASI</div>} />
        </Routes>
      </LanguageProvider>
    </MemoryRouter>
  );
}

function sampleProfile(over = {}) {
  return {
    username: 'berkan',
    email: 'berkan@example.com',
    firstName: 'Berkan',
    lastName: 'Gulyagci',
    emailVerified: true,
    enabled: true,
    roles: ['user', 'default-roles-portal', 'offline_access', 'uma_authorization'],
    ...over,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  authState.isAuthenticated = true;
  authState.clearLocalSession = vi.fn();
  // Varsayılan: oturum açık, profil + bülten başarılı döner.
  getMe.mockResolvedValue(sampleProfile());
  getNewsletter.mockResolvedValue({ subscribed: false, frequency: 'WEEKLY' });
});

describe('ProfilePage (jsdom + testing-library + React 19)', () => {
  it('yükleme sırasında iskelet (skeleton) gösterir, sayfa başlığı her zaman görünür', async () => {
    // getMe çözülmeden loading dalını yakala.
    let resolve;
    getMe.mockReturnValue(new Promise((r) => { resolve = r; }));
    const { container } = renderPage();

    expect(screen.getByText('Profilim')).toBeInTheDocument();
    // Loading dalında SkeletonProfile render olur (animate-pulse placeholder'lar); henüz isim yok.
    expect(container.querySelector('.animate-pulse')).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Berkan Gulyagci' })).not.toBeInTheDocument();

    resolve(sampleProfile());
    // Veri gelince hero başlık (gerçek isim) görünür → skeleton geçti.
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Berkan Gulyagci' })).toBeInTheDocument());
  });

  it('profil başarıyla yüklenince hero kartı (isim, @kullanıcı, baş harfler) ve hesap bilgilerini gösterir', async () => {
    renderPage();

    // Hero başlık: tam ad.
    expect(await screen.findByRole('heading', { name: 'Berkan Gulyagci' })).toBeInTheDocument();

    // Baş harfler: "Berkan Gulyagci" → "BG".
    expect(screen.getByText('BG')).toBeInTheDocument();

    // @kullanıcı + email satırı (kısmi metinle, aynı <p> içinde).
    expect(screen.getByText(/@berkan/)).toBeInTheDocument();

    // Hesap bilgileri alanları: değerler (Field) görünür.
    // username hem hero (@berkan) hem Field'da; email iki yerde; ad/soyad birer kez.
    expect(screen.getAllByText('berkan@example.com').length).toBeGreaterThan(0);
    expect(screen.getByText('Gulyagci')).toBeInTheDocument();

    // Alt kartlar (mock'lu) render olur.
    expect(screen.getByTestId('ticker-customizer')).toBeInTheDocument();
    expect(screen.getByTestId('support-tickets-card')).toBeInTheDocument();

    // getMe ve getNewsletter açılışta birer kez çağrılır.
    expect(getMe).toHaveBeenCalledTimes(1);
    expect(getNewsletter).toHaveBeenCalledTimes(1);
  });

  it('doğrulanmış + aktif hesap için "Doğrulandı"/"Aktif" rozetlerini gösterir; düzenleme modalları başta kapalı', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'Berkan Gulyagci' });

    // Rozetler (hero chip + Field değeri → en az bir kez).
    expect(screen.getAllByText('Doğrulandı').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Aktif').length).toBeGreaterThan(0);

    // Sistem rolleri (default-roles-/offline_access/uma_authorization) filtrelenir;
    // yalnız "user" rozeti kalır.
    expect(screen.getAllByText('user')[0]).toBeInTheDocument();
    expect(screen.queryByText('offline_access')).not.toBeInTheDocument();

    // Modallar başta kapalı.
    expect(screen.queryByTestId('name-modal')).not.toBeInTheDocument();
    expect(screen.queryByTestId('email-modal')).not.toBeInTheDocument();
    expect(screen.queryByTestId('password-modal')).not.toBeInTheDocument();
  });

  it('email doğrulanmamışsa "Doğrulanmadı" rozeti + "Email doğrulama sayfası" bağlantısı çıkar', async () => {
    getMe.mockResolvedValue(sampleProfile({ emailVerified: false }));
    renderPage();
    await screen.findByRole('heading', { name: 'Berkan Gulyagci' });

    expect(screen.getAllByText('Doğrulanmadı').length).toBeGreaterThan(0);
    // Doğrulanmamışken görünen tonal link.
    expect(screen.getByRole('link', { name: 'Email doğrulama sayfası' })).toBeInTheDocument();
  });

  it('"Bilgilerimi Düzenle" → isim modalı açılır; onSuccess sonrası profil yeniden yüklenir', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'Berkan Gulyagci' });
    expect(getMe).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'Bilgilerimi Düzenle' }));
    expect(screen.getByTestId('name-modal')).toBeInTheDocument();

    // save-name → onSuccess: başarı mesajı gösterilir + loadProfile tekrar çağrılır.
    fireEvent.click(screen.getByText('save-name'));
    expect(await screen.findByText('Ad güncellendi')).toBeInTheDocument();
    await waitFor(() => expect(getMe).toHaveBeenCalledTimes(2));
  });

  it('"Şifre Değiştir" → şifre modalı açılır ve kapatılabilir', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'Berkan Gulyagci' });

    fireEvent.click(screen.getByRole('button', { name: 'Şifre Değiştir' }));
    expect(screen.getByTestId('password-modal')).toBeInTheDocument();

    fireEvent.click(screen.getByText('close-password'));
    await waitFor(() => expect(screen.queryByTestId('password-modal')).not.toBeInTheDocument());
  });

  it('"Email Değiştir" → email modalı açılır', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'Berkan Gulyagci' });

    fireEvent.click(screen.getByRole('button', { name: 'Email Değiştir' }));
    expect(screen.getByTestId('email-modal')).toBeInTheDocument();
  });

  it('bülten kartı: abone değilken "Bülten aboneliği değil"; "Düzenle" → NewsletterModal açılır', async () => {
    renderPage();
    await screen.findByRole('heading', { name: 'Berkan Gulyagci' });

    expect(screen.getByText('Bülten aboneliği değil')).toBeInTheDocument();

    // Bülten kartındaki "Düzenle" (TickerCustomizer mock'landığı için tek "Düzenle").
    fireEvent.click(screen.getByRole('button', { name: 'Düzenle' }));
    expect(screen.getByTestId('newsletter-modal')).toBeInTheDocument();

    fireEvent.click(screen.getByText('close-newsletter'));
    await waitFor(() => expect(screen.queryByTestId('newsletter-modal')).not.toBeInTheDocument());
  });

  it('aboneyse bülten kartı sıklığı (DAILY → "Günlük") gösterir', async () => {
    getNewsletter.mockResolvedValue({ subscribed: true, frequency: 'DAILY' });
    renderPage();
    await screen.findByRole('heading', { name: 'Berkan Gulyagci' });

    expect(await screen.findByText('Günlük')).toBeInTheDocument();
    expect(screen.queryByText('Bülten aboneliği değil')).not.toBeInTheDocument();
  });

  it('hata (401 değil) → hata mesajı + "Tekrar dene" butonu; tıklayınca getMe yeniden çağrılır', async () => {
    getMe.mockRejectedValueOnce({ response: { status: 500, data: { message: 'Sunucu hatası' } } });
    renderPage();

    // Backend mesajı gösterilir.
    expect(await screen.findByText('Sunucu hatası')).toBeInTheDocument();
    const retry = screen.getByRole('button', { name: 'Tekrar dene' });
    expect(retry).toBeInTheDocument();
    expect(getMe).toHaveBeenCalledTimes(1);

    // İkinci denemede başarı → profil render olur, hata kalkar.
    fireEvent.click(retry);
    expect(await screen.findByRole('heading', { name: 'Berkan Gulyagci' })).toBeInTheDocument();
    expect(screen.queryByText('Sunucu hatası')).not.toBeInTheDocument();
    expect(getMe).toHaveBeenCalledTimes(2);
  });

  it('401 yanıtı → /login sayfasına yönlendirir (profil render edilmez)', async () => {
    getMe.mockRejectedValueOnce({ response: { status: 401 } });
    renderPage();

    expect(await screen.findByText('LOGIN SAYFASI')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Berkan Gulyagci' })).not.toBeInTheDocument();
  });

  it('oturum açık değilse profil çağrısı yapılmadan /login sayfasına yönlendirir', async () => {
    authState.isAuthenticated = false;
    renderPage();

    expect(await screen.findByText('LOGIN SAYFASI')).toBeInTheDocument();
    expect(getMe).not.toHaveBeenCalled();
  });

  it('URL ?modal=password ile gelince şifre modalı otomatik açılır', async () => {
    renderPage(['/profile?modal=password']);
    // password modalı, profil yüklenmesini beklemeden açılır (kodda erken dönüş).
    expect(await screen.findByTestId('password-modal')).toBeInTheDocument();
  });

  it('URL ?modal=name ile gelince (profil yüklendikten sonra) isim modalı otomatik açılır', async () => {
    renderPage(['/profile?modal=name']);
    // Önce profil yüklenir, sonra isim modalı açılır.
    expect(await screen.findByTestId('name-modal')).toBeInTheDocument();
  });
});
