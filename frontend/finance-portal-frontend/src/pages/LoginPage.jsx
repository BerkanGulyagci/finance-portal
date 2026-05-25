import { useEffect, useState } from 'react';
import { Link, useSearchParams, useNavigate } from 'react-router-dom';
import { redirectToLogin } from '../api/authApi';
import { useAuth } from '../context/AuthContext';
import { useTranslation } from '../i18n/LanguageContext';

export default function LoginPage() {
  const { t } = useTranslation();
  const { isAuthenticated, emailVerified } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [loginError, setLoginError] = useState('');
  const bannedNotice = searchParams.get('banned') === '1';

  useEffect(() => {
    if (!isAuthenticated) return;
    if (!emailVerified) {
      navigate('/verify-email', { replace: true });
      return;
    }
    navigate('/portfolio', { replace: true });
  }, [isAuthenticated, emailVerified, navigate]);

  async function handleSecureLogin(event) {
    event.preventDefault();
    setLoginError('');
    try {
      await redirectToLogin(event);
    } catch (err) {
      console.error('PKCE login start failed', err);
      setLoginError(err.message || t('Giriş başlatılamadı. Lütfen tekrar deneyin.'));
    }
  }

  return (
    <div className="bg-[#f5f6f8] flex items-center justify-center px-4 py-12 -mx-4 sm:-mx-6 lg:-mx-8 -my-8">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <img src="/brand-logo.png" alt="" className="h-12 w-auto object-contain shrink-0" width={57} height={48} decoding="async" />
          <h1 className="text-2xl font-bold tracking-tight">
            <span className="text-[#093eaa]">Port</span>
            <span className="text-gray-900">iva</span>
          </h1>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8">
          <h2 className="text-xl font-bold text-gray-900 mb-2">{t('Hesabınıza Giriş Yapın')}</h2>
          <p className="text-sm text-gray-500 mb-6">
            {t('Portföyünüzü yönetmek için güvenli giriş yapın.')}
          </p>

          {bannedNotice && (
            <div className="bg-rose-50 border border-rose-200 rounded-xl p-4 mb-6 text-sm text-rose-800">
              {t('Hesabınız yönetici tarafından devre dışı bırakıldı. Oturumunuz sonlandırıldı.')}
            </div>
          )}

          {/* 2FA info */}
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-6">
            <div className="flex items-start gap-3">
              <span className="text-2xl">🔐</span>
              <div>
                <p className="text-sm font-semibold text-blue-800">{t('2 Faktörlü Kimlik Doğrulama')}</p>
                <p className="text-xs text-blue-600 mt-1">
                  {t('Hesabınız Google Authenticator ile korunmaktadır. İlk girişte TOTP kurulumu yapmanız gerekecektir.')}
                </p>
              </div>
            </div>
          </div>

          {loginError && (
            <div className="bg-rose-50 border border-rose-200 rounded-xl p-4 mb-4 text-sm text-rose-800">
              {loginError}
            </div>
          )}

          <button
            type="button"
            onClick={handleSecureLogin}
            className="w-full bg-[#093eaa] text-white py-3 rounded-xl font-bold text-sm hover:bg-[#093eaa]/90 transition-all flex items-center justify-center gap-2"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 3h4a2 2 0 012 2v14a2 2 0 01-2 2h-4M10 17l5-5-5-5M15 12H3" />
            </svg>
            {t('Güvenli Giriş Yap')}
          </button>

          <p className="text-xs text-gray-400 text-center mt-4">
            {t('Keycloak güvenli kimlik doğrulama sistemi kullanılmaktadır.')}
          </p>

          <p className="text-sm text-center text-gray-500 mt-4">
            {t('Hesabınız yok mu?')}{' '}
            <Link to="/register" className="text-[#093eaa] font-semibold hover:underline">{t('Kayıt Olun')}</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
