import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Wallet } from 'lucide-react';
import { redirectToLogin } from '../api/authApi';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';

export default function LoginPage() {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (isAuthenticated) navigate('/portfolio', { replace: true });
  }, [isAuthenticated]);

  return (
    <div className="bg-[#f5f6f8] flex items-center justify-center px-4 py-12 -mx-4 sm:-mx-6 lg:-mx-8 -my-8">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="bg-[#093eaa] p-2 rounded-xl">
            <Wallet className="w-7 h-7 text-white" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight">
            <span className="text-[#093eaa]">Finans</span>
            <span className="text-gray-900">Portalı</span>
          </h1>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8">
          <h2 className="text-xl font-bold text-gray-900 mb-2">Hesabınıza Giriş Yapın</h2>
          <p className="text-sm text-gray-500 mb-6">
            Portföyünüzü yönetmek için güvenli giriş yapın.
          </p>

          {/* 2FA info */}
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-4 mb-6">
            <div className="flex items-start gap-3">
              <span className="text-2xl">🔐</span>
              <div>
                <p className="text-sm font-semibold text-blue-800">2 Faktörlü Kimlik Doğrulama</p>
                <p className="text-xs text-blue-600 mt-1">
                  Hesabınız Google Authenticator ile korunmaktadır. İlk girişte TOTP kurulumu yapmanız gerekecektir.
                </p>
              </div>
            </div>
          </div>

          <button
            onClick={redirectToLogin}
            className="w-full bg-[#093eaa] text-white py-3 rounded-xl font-bold text-sm hover:bg-[#093eaa]/90 transition-all flex items-center justify-center gap-2"
          >
            <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 3h4a2 2 0 012 2v14a2 2 0 01-2 2h-4M10 17l5-5-5-5M15 12H3" />
            </svg>
            Güvenli Giriş Yap
          </button>

          <p className="text-xs text-gray-400 text-center mt-4">
            Keycloak güvenli kimlik doğrulama sistemi kullanılmaktadır.
          </p>

          <p className="text-sm text-center text-gray-500 mt-4">
            Hesabınız yok mu?{' '}
            <Link to="/register" className="text-[#093eaa] font-semibold hover:underline">Kayıt Olun</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
