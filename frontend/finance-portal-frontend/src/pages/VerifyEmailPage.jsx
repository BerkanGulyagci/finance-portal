import { Link } from 'react-router-dom';
import { Mail, Wallet } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

export default function VerifyEmailPage() {
  const { isAuthenticated, email, logout } = useAuth();

  return (
    <div className="bg-[#f5f6f8] flex items-center justify-center px-4 py-12 -mx-4 sm:-mx-6 lg:-mx-8 -my-8">
      <div className="w-full max-w-md">
        <div className="flex items-center justify-center gap-3 mb-8">
          <div className="bg-[#093eaa] p-2 rounded-xl">
            <Wallet className="w-7 h-7 text-white" />
          </div>
          <h1 className="text-2xl font-bold tracking-tight">
            <span className="text-[#093eaa]">Finans</span>
            <span className="text-gray-900">Portalı</span>
          </h1>
        </div>

        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 text-center">
          <span className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-blue-50 text-[#093eaa] mb-4">
            <Mail className="w-6 h-6" />
          </span>
          <h2 className="text-xl font-bold text-gray-900 mb-2">Email adresinizi doğrulayın</h2>
          <p className="text-sm text-gray-600 leading-relaxed mb-2">
            Portala tam erişim için email doğrulaması gereklidir.
          </p>
          {email && (
            <p className="text-sm font-semibold text-gray-800 mb-4">{email}</p>
          )}
          <p className="text-sm text-gray-500 leading-relaxed mb-6">
            Gelen kutunuzu (ve spam klasörünü) kontrol edin. Doğrulama linkine tıkladıktan sonra
            tekrar giriş yapın veya bu sayfayı yenileyin.
          </p>

          <div className="flex flex-col gap-2">
            {isAuthenticated ? (
              <button
                type="button"
                onClick={() => window.location.reload()}
                className="w-full bg-[#093eaa] text-white py-2.5 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90"
              >
                Sayfayı yenile
              </button>
            ) : (
              <Link
                to="/login"
                className="w-full inline-block bg-[#093eaa] text-white py-2.5 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90"
              >
                Giriş sayfasına dön
              </Link>
            )}
            {isAuthenticated && (
              <button
                type="button"
                onClick={logout}
                className="w-full border border-gray-200 text-gray-700 py-2.5 rounded-xl text-sm font-bold hover:bg-gray-50"
              >
                Çıkış yap
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
