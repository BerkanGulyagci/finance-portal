import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ExternalLink, Mail, RefreshCw, Shield, User } from 'lucide-react';
import { getMe } from '../api/meApi';
import { keycloakAccountUrls } from '../config/keycloakAccount';
import { useAuth } from '../context/AuthContext';

function Field({ label, value }) {
  return (
    <div className="py-3 border-b border-gray-100 last:border-0">
      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-1">{label}</p>
      <p className="text-sm font-semibold text-gray-900 break-words">{value || '—'}</p>
    </div>
  );
}

export default function ProfilePage() {
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

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
          <p className="text-sm text-gray-500">Hesap bilgilerinizi görüntüleyin</p>
        </div>
      </div>

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

          <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 mb-6 text-sm text-amber-900">
            Ad, soyad, email ve şifre değişiklikleri Keycloak Hesap Ayarları üzerinden yapılır.
            Değişikliklerden sonra bilgilerin güncellenmesi için sayfayı yenileyin veya tekrar giriş yapmanız gerekebilir.
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <a
              href={keycloakAccountUrls.home}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-center gap-2 bg-[#093eaa] text-white py-3 px-4 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90"
            >
              <ExternalLink className="w-4 h-4" />
              Bilgilerimi Düzenle
            </a>
            <a
              href={keycloakAccountUrls.changePassword}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-center gap-2 border border-gray-200 text-gray-800 py-3 px-4 rounded-xl text-sm font-bold hover:bg-gray-50"
            >
              <Shield className="w-4 h-4" />
              Şifremi Değiştir
            </a>
            <a
              href={keycloakAccountUrls.home}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-center gap-2 border border-gray-200 text-gray-800 py-3 px-4 rounded-xl text-sm font-bold hover:bg-gray-50"
            >
              <Mail className="w-4 h-4" />
              Email Değiştir
            </a>
            <button
              type="button"
              onClick={logout}
              className="flex items-center justify-center gap-2 border border-red-200 text-red-700 py-3 px-4 rounded-xl text-sm font-bold hover:bg-red-50"
            >
              Çıkış Yap
            </button>
          </div>

          <div className="mt-6 flex flex-wrap gap-4 text-sm">
            <button
              type="button"
              onClick={loadProfile}
              className="inline-flex items-center gap-1.5 font-semibold text-[#093eaa] hover:underline"
            >
              <RefreshCw className="w-4 h-4" />
              Bilgileri yenile
            </button>
            <a
              href={keycloakAccountUrls.home}
              target="_blank"
              rel="noopener noreferrer"
              className="font-semibold text-gray-600 hover:text-[#093eaa]"
            >
              Hesap Ayarları (Keycloak)
            </a>
            {!profile.emailVerified && (
              <Link to="/verify-email" className="font-semibold text-amber-700 hover:underline">
                Email doğrulama sayfası
              </Link>
            )}
          </div>
        </>
      )}
    </div>
  );
}
