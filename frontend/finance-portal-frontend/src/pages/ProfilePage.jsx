import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Mail, Pencil, RefreshCw, KeyRound, BadgeCheck, AlertCircle, IdCard } from 'lucide-react';
import { getMe } from '../api/meApi';
import { useAuth } from '../context/AuthContext';
import { useTranslation } from '../i18n/LanguageContext';
import {
  ProfileEmailModal,
  ProfileNameModal,
  ProfilePasswordModal,
} from './profile/ProfileAccountModals';
import TickerCustomizer from '../components/finans/TickerCustomizer';
import NewsletterModal from '../components/finans/NewsletterModal';
import SupportTicketsCard from './profile/SupportTicketsCard';
import { getNewsletter } from '../api/newsletterApi';

function Field({ label, value }) {
  return (
    <div className="min-w-0">
      <p className="text-[11px] font-semibold text-gray-400 uppercase tracking-wide mb-0.5">{label}</p>
      <p className="text-sm font-semibold text-gray-900 break-words">{value || '—'}</p>
    </div>
  );
}

/** Tüm kartlarda aynı görünen başlık: tonal ikon + başlık (+ açıklama / sağ eylem). */
function CardHeader({ icon: Icon, title, desc, action }) {
  return (
    <div className="flex items-start justify-between gap-3 mb-4">
      <div className="flex items-center gap-3 min-w-0">
        <span className="m3-icon-chip"><Icon className="w-5 h-5" /></span>
        <div className="min-w-0">
          <h3 className="font-bold text-gray-900 leading-tight">{title}</h3>
          {desc && <p className="text-xs text-gray-500 mt-0.5">{desc}</p>}
        </div>
      </div>
      {action}
    </div>
  );
}

export default function ProfilePage() {
  const { t } = useTranslation();
  const { isAuthenticated, clearLocalSession } = useAuth();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');
  const [nameModalOpen, setNameModalOpen] = useState(false);
  const [emailModalOpen, setEmailModalOpen] = useState(false);
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);
  const [newsletterOpen, setNewsletterOpen] = useState(false);
  const [newsletter, setNewsletter] = useState({ subscribed: false, frequency: 'WEEKLY' });

  const loadNewsletter = useCallback(() => {
    getNewsletter().then(setNewsletter).catch(() => {});
  }, []);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await getMe();
      setProfile(data);
    } catch (err) {
      const status = err.response?.status;
      if (status === 401) {
        navigate('/login', { replace: true });
        return;
      }
      setError(err.response?.data?.message || t('Profil bilgisi yüklenemedi.'));
    } finally {
      setLoading(false);
    }
  }, [navigate]);

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login', { replace: true });
      return;
    }
    loadProfile();
    loadNewsletter();
  }, [isAuthenticated, loadProfile, loadNewsletter, navigate]);

  useEffect(() => {
    const modal = searchParams.get('modal');
    if (!modal) return;

    if (modal === 'password') {
      setPasswordModalOpen(true);
      setSearchParams({}, { replace: true });
      return;
    }

    if (!loading && profile) {
      if (modal === 'name') setNameModalOpen(true);
      if (modal === 'email') setEmailModalOpen(true);
      setSearchParams({}, { replace: true });
    }
  }, [searchParams, profile, loading, setSearchParams]);

  // "Taleplerim" menüsünden gelince destek talepleri kartına kaydır.
  useEffect(() => {
    if (searchParams.get('tab') !== 'support' || loading || !profile) return;
    const el = document.getElementById('support-tickets');
    if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    setSearchParams({}, { replace: true });
  }, [searchParams, profile, loading, setSearchParams]);

  function handleRequiresReLogin(message, redirectTo = '/verify-email') {
    setSuccessMessage(message);
    clearLocalSession();
    navigate(redirectTo, { replace: true, state: { profileNotice: message } });
  }

  const displayRoles = (profile?.roles || []).filter(
    (role) => !role.startsWith('default-roles-') && role !== 'offline_access' && role !== 'uma_authorization'
  );

  const fullName = [profile?.firstName, profile?.lastName].filter(Boolean).join(' ').trim();
  const displayName = fullName || profile?.username || '—';
  const initials =
    (fullName || profile?.username || '?')
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((s) => s[0]?.toUpperCase())
      .join('') || '?';

  return (
    <div className="max-w-6xl mx-auto py-8 px-4">
      <p className="text-[11px] font-bold text-gray-400 uppercase tracking-widest mb-3">{t('Profilim')}</p>

      {successMessage && (
        <div className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">
          {successMessage}
        </div>
      )}

      {loading && (
        <div className="m3-card p-8 text-center text-sm text-gray-500">{t('Yükleniyor...')}</div>
      )}

      {!loading && error && (
        <div className="m3-card p-8 text-center" style={{ borderColor: '#fecaca' }}>
          <p className="text-sm text-red-600 mb-4">{error}</p>
          <button type="button" onClick={loadProfile} className="m3-btn m3-btn-text mx-auto">
            <RefreshCw className="w-4 h-4" />
            {t('Tekrar dene')}
          </button>
        </div>
      )}

      {!loading && !error && profile && (
        <div className="space-y-5">
          {/* ── Üst kimlik kartı (hero): avatar + isim + rozetler + eylemler ── */}
          <section className="m3-card p-6">
            <div className="flex flex-col gap-5 lg:flex-row lg:items-center">
              <div className="flex items-center gap-4 min-w-0">
                <div className="w-16 h-16 rounded-full bg-[#e7eefb] text-[#093eaa] flex items-center justify-center text-xl font-bold shrink-0">
                  {initials}
                </div>
                <div className="min-w-0">
                  <h1 className="text-xl font-bold text-gray-900 truncate">{displayName}</h1>
                  <p className="text-sm text-gray-500 truncate">
                    @{profile.username}
                    {profile.email ? <span className="text-gray-300"> · </span> : null}
                    {profile.email}
                  </p>
                  <div className="flex flex-wrap items-center gap-1.5 mt-2">
                    {profile.emailVerified ? (
                      <span className="m3-chip bg-emerald-50 text-emerald-700 border-emerald-100">
                        <BadgeCheck className="w-3.5 h-3.5" /> {t('Doğrulandı')}
                      </span>
                    ) : (
                      <span className="m3-chip bg-amber-50 text-amber-700 border-amber-100">
                        <AlertCircle className="w-3.5 h-3.5" /> {t('Doğrulanmadı')}
                      </span>
                    )}
                    <span
                      className={`m3-chip ${
                        profile.enabled
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-100'
                          : 'bg-rose-50 text-rose-700 border-rose-100'
                      }`}
                    >
                      {profile.enabled ? t('Aktif') : t('Pasif')}
                    </span>
                    {displayRoles.map((role) => (
                      <span key={role} className="m3-chip bg-[#eef0f6] text-gray-600 border-gray-200">
                        {role}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="flex flex-wrap gap-2 lg:ml-auto lg:shrink-0">
                <button type="button" onClick={() => setNameModalOpen(true)} className="m3-btn m3-btn-filled">
                  <Pencil className="w-4 h-4" />
                  {t('Bilgilerimi Düzenle')}
                </button>
                <button type="button" onClick={() => setPasswordModalOpen(true)} className="m3-btn m3-btn-tonal">
                  <KeyRound className="w-4 h-4" />
                  {t('Şifre Değiştir')}
                </button>
                <button type="button" onClick={() => setEmailModalOpen(true)} className="m3-btn m3-btn-tonal">
                  <Mail className="w-4 h-4" />
                  {t('Email Değiştir')}
                </button>
              </div>
            </div>
          </section>

          {/* ── İçerik ızgarası ── */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-5 items-start">
            {/* Sol — hesap bilgileri + destek talepleri */}
            <div className="lg:col-span-2 space-y-5 min-w-0">
              <section className="m3-card p-6">
                <CardHeader
                  icon={IdCard}
                  title={t('Hesap Bilgileri')}
                  desc={t('Hesap bilgilerinizi görüntüleyin ve yönetin')}
                  action={
                    <button type="button" onClick={loadProfile} className="m3-btn m3-btn-text shrink-0">
                      <RefreshCw className="w-4 h-4" />
                      <span className="hidden sm:inline">{t('Yenile')}</span>
                    </button>
                  }
                />
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-4">
                  <Field label={t('Kullanıcı adı')} value={profile.username} />
                  <Field label={t('Email')} value={profile.email} />
                  <Field label={t('Ad')} value={profile.firstName} />
                  <Field label={t('Soyad')} value={profile.lastName} />
                  <Field
                    label={t('Email doğrulama')}
                    value={profile.emailVerified ? t('Doğrulandı') : t('Doğrulanmadı')}
                  />
                  <Field label={t('Hesap durumu')} value={profile.enabled ? t('Aktif') : t('Pasif')} />
                  <Field
                    label={t('Roller')}
                    value={displayRoles.length > 0 ? displayRoles.join(', ') : '—'}
                  />
                </div>
                {!profile.emailVerified && (
                  <Link
                    to="/verify-email"
                    className="m3-btn m3-btn-tonal mt-5 w-full sm:w-auto bg-amber-50 text-amber-800 hover:bg-amber-100"
                  >
                    <AlertCircle className="w-4 h-4" />
                    {t('Email doğrulama sayfası')}
                  </Link>
                )}
              </section>

              <div id="support-tickets" className="scroll-mt-24">
                <SupportTicketsCard />
              </div>
            </div>

            {/* Sağ — piyasa şeridi + bülten */}
            <div className="lg:col-span-1 min-w-0 space-y-5">
              <TickerCustomizer />

              <section className="m3-card p-6">
                <CardHeader
                  icon={Mail}
                  title={t('Bülten Aboneliği')}
                  desc={t('Panonuzun özetini e-posta ile alın')}
                />
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-semibold text-gray-700">
                    {newsletter.subscribed
                      ? t(
                          newsletter.frequency === 'DAILY'
                            ? 'Günlük'
                            : newsletter.frequency === 'MONTHLY'
                            ? 'Aylık'
                            : 'Haftalık'
                        )
                      : t('Bülten aboneliği değil')}
                  </span>
                  <button onClick={() => setNewsletterOpen(true)} className="m3-btn m3-btn-text shrink-0">
                    {t('Düzenle')}
                  </button>
                </div>
              </section>
            </div>
          </div>
        </div>
      )}

      <ProfileNameModal
        open={nameModalOpen}
        profile={profile}
        onClose={() => setNameModalOpen(false)}
        onSuccess={(message) => {
          setSuccessMessage(message);
          loadProfile();
        }}
      />
      <ProfileEmailModal
        open={emailModalOpen}
        profile={profile}
        onClose={() => setEmailModalOpen(false)}
        onRequiresReLogin={(message) => handleRequiresReLogin(message, '/verify-email')}
      />
      <ProfilePasswordModal
        open={passwordModalOpen}
        onClose={() => setPasswordModalOpen(false)}
        onRequiresReLogin={(message) => handleRequiresReLogin(message, '/login')}
      />

      {newsletterOpen && (
        <NewsletterModal
          email={profile?.email}
          onClose={() => setNewsletterOpen(false)}
          onSaved={loadNewsletter}
        />
      )}
    </div>
  );
}
