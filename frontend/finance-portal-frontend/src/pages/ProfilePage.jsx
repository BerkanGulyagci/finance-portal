import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Mail, Pencil, RefreshCw, Shield, User } from 'lucide-react';
import { getMe } from '../api/meApi';
import { useAuth } from '../context/AuthContext';
import {
  ProfileEmailModal,
  ProfileNameModal,
  ProfilePasswordModal,
} from './profile/ProfileAccountModals';

function Field({ label, value }) {
  return (
    <div className="py-3 border-b border-gray-100 last:border-0">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-1">{label}</p>
      <p className="text-sm font-semibold text-gray-900 break-words">{value || '—'}</p>
    </div>
  );
}

export default function ProfilePage() {
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
      setError(err.response?.data?.message || 'Profil bilgisi yüklenemedi.');
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
  }, [isAuthenticated, loadProfile, navigate]);

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

  function handleRequiresReLogin(message, redirectTo = '/verify-email') {
    setSuccessMessage(message);
    clearLocalSession();
    navigate(redirectTo, { replace: true, state: { profileNotice: message } });
  }

  const displayRoles = (profile?.roles || []).filter(
    (role) => !role.startsWith('default-roles-') && role !== 'offline_access' && role !== 'uma_authorization'
  );

  return (
    <div className="max-w-2xl mx-auto py-8 px-4">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-12 h-12 rounded-2xl bg-blue-50 flex items-center justify-center">
          <User className="w-6 h-6 text-[#093eaa]" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Profilim</h1>
          <p className="text-sm text-gray-500">Hesap bilgilerinizi görüntüleyin ve yönetin</p>
        </div>
      </div>

      {successMessage && (
        <div className="mb-4 rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-900">
          {successMessage}
        </div>
      )}

      {loading && (
        <div className="bg-white rounded-2xl border border-gray-200 p-8 text-center text-sm text-gray-500">
          Yükleniyor...
        </div>
      )}

      {!loading && error && (
        <div className="bg-white rounded-2xl border border-red-200 p-8 text-center">
          <p className="text-sm text-red-600 mb-4">{error}</p>
          <button
            type="button"
            onClick={loadProfile}
            className="inline-flex items-center gap-2 text-sm font-bold text-[#093eaa] hover:underline"
          >
            <RefreshCw className="w-4 h-4" />
            Tekrar dene
          </button>
        </div>
      )}

      {!loading && !error && profile && (
        <>
          <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6 mb-6">
            <Field label="Kullanıcı adı" value={profile.username} />
            <Field label="Email" value={profile.email} />
            <Field label="Ad" value={profile.firstName} />
            <Field label="Soyad" value={profile.lastName} />
            <Field
              label="Email doğrulama"
              value={profile.emailVerified ? 'Doğrulandı' : 'Doğrulanmadı'}
            />
            <Field
              label="Hesap durumu"
              value={profile.enabled ? 'Aktif' : 'Pasif'}
            />
            <Field
              label="Roller"
              value={displayRoles.length > 0 ? displayRoles.join(', ') : '—'}
            />
          </div>

          <div className="grid gap-3 sm:grid-cols-2 mb-6">
            <button
              type="button"
              onClick={() => setNameModalOpen(true)}
              className="flex items-center justify-center gap-2 bg-[#093eaa] text-white py-3 px-4 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90"
            >
              <Pencil className="w-4 h-4" />
              Bilgilerimi Düzenle
            </button>
            <button
              type="button"
              onClick={() => setPasswordModalOpen(true)}
              className="flex items-center justify-center gap-2 border border-gray-200 text-gray-800 py-3 px-4 rounded-xl text-sm font-bold hover:bg-gray-50"
            >
              <Shield className="w-4 h-4" />
              Şifremi Değiştir
            </button>
            <button
              type="button"
              onClick={() => setEmailModalOpen(true)}
              className="flex items-center justify-center gap-2 border border-gray-200 text-gray-800 py-3 px-4 rounded-xl text-sm font-bold hover:bg-gray-50"
            >
              <Mail className="w-4 h-4" />
              Email Değiştir
            </button>
          </div>

          <div className="flex flex-wrap gap-4 text-sm">
            <button
              type="button"
              onClick={loadProfile}
              className="inline-flex items-center gap-1.5 font-semibold text-[#093eaa] hover:underline"
            >
              <RefreshCw className="w-4 h-4" />
              Bilgileri yenile
            </button>
            {!profile.emailVerified && (
              <Link to="/verify-email" className="font-semibold text-amber-700 hover:underline">
                Email doğrulama sayfası
              </Link>
            )}
          </div>
        </>
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
    </div>
  );
}
