import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ── Mocks ──────────────────────────────────────────────────────────────────────
//
// Header, gerçek ağ/canvas gerektiren 3 alt komponent (SearchBox, NotificationBell,
// ThemeToggle) render eder + AuthContext (JWT) ve portfolioApi (HTTP) kullanır.
// Bunları stub'larız; LanguageContext GERÇEK Provider ile sarılır (t(key) → "tr"de
// anahtarın kendisini döndürür, böylece Türkçe metinleri doğrudan arayabiliriz).

// react-router-dom: MemoryRouter/Link/NavLink GERÇEK kalsın, sadece useNavigate'i
// paylaşılan bir spy ile değiştir → çıkış/logout sonrası yönlendirmeyi doğrulayalım.
const navigateSpy = vi.fn();
vi.mock('react-router-dom', async (orig) => ({
  ...(await orig()),
  useNavigate: () => navigateSpy,
}));

// AuthContext — mutable durum: her testten önce setAuth() ile ayarlanır.
const logoutSpy = vi.fn();
let authState = {
  isAuthenticated: false,
  isAdmin: false,
  username: null,
  logout: logoutSpy,
};
function setAuth(partial) {
  authState = { isAuthenticated: false, isAdmin: false, username: null, logout: logoutSpy, ...partial };
}
vi.mock('../../../context/AuthContext', () => ({
  useAuth: () => authState,
}));

// portfolioApi — PortfolioNavMenu mount olunca getMyPortfolios çağırır.
vi.mock('../../../api/portfolioApi', () => ({
  getMyPortfolios: vi.fn(),
}));

// Alt komponentler — jsdom'da canvas/ağ olmadığı için basit test stub'ları.
vi.mock('../SearchBox', () => ({
  default: () => <div data-testid="searchbox" />,
}));
vi.mock('../NotificationBell', () => ({
  default: () => <div data-testid="notification-bell" />,
}));
vi.mock('../ThemeToggle', () => ({
  default: ({ className = '' }) => <div data-testid="theme-toggle" className={className} />,
}));

import { Header } from '../Header';
import { getMyPortfolios } from '../../../api/portfolioApi';
import { LanguageProvider } from '../../../context/LanguageContext';

function renderHeader() {
  return render(
    <LanguageProvider>
      <MemoryRouter>
        <Header />
      </MemoryRouter>
    </LanguageProvider>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  document.body.style.overflow = '';
  setAuth({ isAuthenticated: false });
  // PortfolioNavMenu açılmadıkça çağrılmaz; yine de güvenli varsayılan.
  getMyPortfolios.mockResolvedValue([]);
});

describe('Header (Vitest + @testing-library/react)', () => {
  describe('smoke / temel render', () => {
    it('mock\'larla hata fırlatmadan render eder ve Portiva markasını gösterir', () => {
      renderHeader();
      // "Port" + "iva" iki ayrı span → birleşik metin parça parça; her ikisi de DOM'da.
      expect(screen.getAllByText('Port').length).toBeGreaterThan(0);
      expect(screen.getAllByText('iva').length).toBeGreaterThan(0);
    });

    it('logo görselini ve alt komponentleri (SearchBox/ThemeToggle) render eder', () => {
      renderHeader();
      const logos = screen.getAllByRole('presentation');
      expect(logos.length).toBeGreaterThan(0);
      expect(logos[0]).toHaveAttribute('src', '/brand-logo.png');
      expect(screen.getByTestId('searchbox')).toBeInTheDocument();
      expect(screen.getByTestId('theme-toggle')).toBeInTheDocument();
    });

    it('her zaman görünen piyasa dropdown başlıklarını render eder', () => {
      renderHeader();
      // Bunlar desktop nav'da daima DOM'dadır (drawer kapalıyken tek kopya).
      expect(screen.getByText('Ekonomi')).toBeInTheDocument();
      expect(screen.getByText('Piyasalar')).toBeInTheDocument();
      expect(screen.getByText('Döviz')).toBeInTheDocument();
      expect(screen.getByText('Emtia')).toBeInTheDocument();
      expect(screen.getByText('Fonlar')).toBeInTheDocument();
    });
  });

  describe('kimlik doğrulama durumuna göre koşullu render', () => {
    it('giriş YAPILMAMIŞKEN Giriş Yap / Kayıt Ol gösterir, Dashboard/Portföy/Admin gizler', () => {
      setAuth({ isAuthenticated: false });
      renderHeader();

      expect(screen.getByText('Giriş Yap')).toBeInTheDocument();
      expect(screen.getByText('Kayıt Ol')).toBeInTheDocument();
      // auth gerektiren öğeler render edilmemeli
      expect(screen.queryByText('Dashboard')).not.toBeInTheDocument();
      expect(screen.queryByText('Portföy')).not.toBeInTheDocument();
      expect(screen.queryByText('Admin Panel')).not.toBeInTheDocument();
      expect(screen.queryByTestId('notification-bell')).not.toBeInTheDocument();
    });

    it('giriş YAPILMIŞKEN kullanıcı adını, Dashboard + Portföy menüsünü ve bildirim zilini gösterir', () => {
      setAuth({ isAuthenticated: true, username: 'berkan' });
      renderHeader();

      // Profil butonunda kullanıcı adı
      expect(screen.getByText('berkan')).toBeInTheDocument();
      // Avatar baş harfi (B)
      expect(screen.getByText('B')).toBeInTheDocument();
      expect(screen.getByText('Dashboard')).toBeInTheDocument();
      expect(screen.getByText('Portföy')).toBeInTheDocument();
      expect(screen.getByTestId('notification-bell')).toBeInTheDocument();
      // Giriş/Kayıt gizli
      expect(screen.queryByText('Giriş Yap')).not.toBeInTheDocument();
      expect(screen.queryByText('Kayıt Ol')).not.toBeInTheDocument();
    });

    it('kullanıcı adı boşsa profil butonu varsayılan "U" + "Hesabım" gösterir', () => {
      setAuth({ isAuthenticated: true, username: null });
      renderHeader();
      expect(screen.getByText('U')).toBeInTheDocument();
      expect(screen.getByText('Hesabım')).toBeInTheDocument();
    });

    it('admin kullanıcıda "Admin Panel" linki görünür, admin olmayanda görünmez', () => {
      setAuth({ isAuthenticated: true, username: 'admin', isAdmin: true });
      const { unmount } = renderHeader();
      expect(screen.getByText('Admin Panel')).toBeInTheDocument();
      unmount();

      setAuth({ isAuthenticated: true, username: 'user', isAdmin: false });
      renderHeader();
      expect(screen.queryByText('Admin Panel')).not.toBeInTheDocument();
    });
  });

  describe('dropdown menüleri (etkileşim)', () => {
    it('"Piyasalar" başlığına tıklayınca menü öğeleri (Hisse Senetleri vb.) açılır', () => {
      renderHeader();
      // Önce kapalı: öğeler yok
      expect(screen.queryByText('Hisse Senetleri')).not.toBeInTheDocument();

      fireEvent.click(screen.getByText('Piyasalar'));

      expect(screen.getByText('Hisse Senetleri')).toBeInTheDocument();
      expect(screen.getByText('Kripto Para')).toBeInTheDocument();
      expect(screen.getByText('Varlık Karşılaştırma')).toBeInTheDocument();
    });

    it('gruplu "Ekonomi" menüsü grup başlıklarını ve öğelerini render eder', () => {
      renderHeader();
      fireEvent.click(screen.getByText('Ekonomi'));

      expect(screen.getByText('Genel')).toBeInTheDocument();
      expect(screen.getByText('Hesaplama Araçları')).toBeInTheDocument();
      expect(screen.getByText('Türkiye Ekonomisi')).toBeInTheDocument();
      expect(screen.getByText('Kredi Hesaplama')).toBeInTheDocument();
    });

    it('aynı dropdown başlığına tekrar tıklamak menüyü kapatır (toggle)', () => {
      renderHeader();
      const btn = screen.getByText('Döviz');

      fireEvent.click(btn);
      expect(screen.getByText('TCMB Kurları')).toBeInTheDocument();

      fireEvent.click(btn);
      expect(screen.queryByText('TCMB Kurları')).not.toBeInTheDocument();
    });
  });

  describe('Portföy dropdown (async veri)', () => {
    it('açılınca getMyPortfolios çağrılır ve en değerli 3 portföy listelenir', async () => {
      setAuth({ isAuthenticated: true, username: 'berkan' });
      getMyPortfolios.mockResolvedValue([
        { id: 1, name: 'Büyük Portföy', portfolioType: 'STANDARD', totalMarketValue: '500000' },
        { id: 2, name: 'Orta Portföy', portfolioType: 'STANDARD', totalMarketValue: '100000' },
        { id: 3, name: 'İzleme', portfolioType: 'WATCHLIST', totalMarketValue: '999999' },
      ]);
      renderHeader();

      fireEvent.click(screen.getByText('Portföy'));

      // async fetch sonrası en değerli (WATCHLIST hariç) portföyler görünür
      expect(await screen.findByText('Büyük Portföy')).toBeInTheDocument();
      expect(screen.getByText('Orta Portföy')).toBeInTheDocument();
      // WATCHLIST filtrelenir
      expect(screen.queryByText('İzleme')).not.toBeInTheDocument();
      expect(getMyPortfolios).toHaveBeenCalledTimes(1);
      // ₺ formatlı değer (tr-TR binlik ayraçlı) — kesin metin yerine sembolü doğrula
      expect(screen.getAllByText(/₺/).length).toBeGreaterThan(0);
    });

    it('hiç (holding) portföy yokken "Henüz portföyünüz yok." mesajı gösterir', async () => {
      setAuth({ isAuthenticated: true, username: 'berkan' });
      getMyPortfolios.mockResolvedValue([]);
      renderHeader();

      fireEvent.click(screen.getByText('Portföy'));

      expect(await screen.findByText('Henüz portföyünüz yok.')).toBeInTheDocument();
      // Menü içindeki sabit linkler de var
      expect(screen.getByText('Tüm Portföyler')).toBeInTheDocument();
      expect(screen.getByText('Bildirimler')).toBeInTheDocument();
    });
  });

  describe('profil menüsü (etkileşim)', () => {
    it('profil butonuna tıklayınca menü açılır; "Çıkış Yap" tıklanınca logout + "/" yönlendirme', () => {
      setAuth({ isAuthenticated: true, username: 'berkan' });
      renderHeader();

      // Profil menüsü kapalı: "Profilim" yok
      expect(screen.queryByText('Profilim')).not.toBeInTheDocument();

      // Profil butonu = kullanıcı adını içeren buton
      fireEvent.click(screen.getByText('berkan'));
      expect(screen.getByText('Profilim')).toBeInTheDocument();
      expect(screen.getByText('Şifre Değiştir')).toBeInTheDocument();

      fireEvent.click(screen.getByText('Çıkış Yap'));
      expect(logoutSpy).toHaveBeenCalledTimes(1);
      expect(navigateSpy).toHaveBeenCalledWith('/');
    });
  });

  describe('mobil çekmece (drawer)', () => {
    it('"Menüyü aç" butonu drawer\'ı açar (role=dialog) ve body scroll kilitlenir', () => {
      renderHeader();
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      expect(document.body.style.overflow).toBe('');

      fireEvent.click(screen.getByLabelText('Menüyü aç'));

      const dialog = screen.getByRole('dialog');
      expect(dialog).toBeInTheDocument();
      expect(document.body.style.overflow).toBe('hidden');
    });

    it('drawer açıkken Esc tuşu çekmeceyi kapatır ve body scroll geri yüklenir', async () => {
      renderHeader();
      fireEvent.click(screen.getByLabelText('Menüyü aç'));
      expect(screen.getByRole('dialog')).toBeInTheDocument();

      fireEvent.keyDown(window, { key: 'Escape' });

      await waitFor(() => {
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
      });
      expect(document.body.style.overflow).toBe('');
    });

    it('drawer içindeki accordion (Piyasalar) başlığa tıklayınca öğelerini açar', () => {
      renderHeader();
      fireEvent.click(screen.getByLabelText('Menüyü aç'));
      const dialog = screen.getByRole('dialog');

      // Drawer içinde "Piyasalar" accordion başlığı (desktop'taki ayrı kopyadan
      // ayırmak için dialog kapsamında ara).
      const piyasalarBtn = within(dialog).getByText('Piyasalar');
      fireEvent.click(piyasalarBtn);

      expect(within(dialog).getByText('Hisse Senetleri')).toBeInTheDocument();
    });
  });
});
