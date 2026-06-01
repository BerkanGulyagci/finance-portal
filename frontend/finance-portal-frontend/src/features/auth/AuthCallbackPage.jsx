import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CheckCircle2, Wallet } from 'lucide-react';
import {
  clearOAuthPkceSession,
  exchangeCodeForToken,
  isOAuthLoginPending,
  OAuthActionCompleteError,
  OAUTH_ACTION_COMPLETE_MESSAGE,
} from '../../api/authApi';
import { useAuth } from '../../context/AuthContext';
import { useTranslation } from '../../context/LanguageContext';

function ActionCompleteCard({ onGoToLogin }) {
  const { t } = useTranslation();
  return (
    <div className="min-h-screen bg-[#f5f6f8] flex items-center justify-center px-4">
      <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 max-w-md w-full text-center">
        <span className="inline-flex h-12 w-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600 mb-4">
          <CheckCircle2 className="w-7 h-7" />
        </span>
        <h1 className="text-lg font-bold text-gray-900 mb-2">{t('İşlem tamamlandı')}</h1>
        <p className="text-sm text-gray-600 leading-relaxed mb-6">{t(OAUTH_ACTION_COMPLETE_MESSAGE)}</p>
        <button
          type="button"
          onClick={onGoToLogin}
          className="w-full bg-[#093eaa] text-white px-6 py-2.5 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90"
        >
          {t('Giriş sayfasına dön')}
        </button>
      </div>
    </div>
  );
}

function ErrorCard({ message, onGoToLogin }) {
  const { t } = useTranslation();
  return (
    <div className="min-h-screen bg-[#f5f6f8] flex items-center justify-center px-4">
      <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 max-w-md w-full text-center">
        <div className="text-rose-500 text-sm mb-4">{message}</div>
        <button
          type="button"
          onClick={onGoToLogin}
          className="bg-[#093eaa] text-white px-6 py-2 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90"
        >
          {t('Giriş sayfasına dön')}
        </button>
      </div>
    </div>
  );
}

export default function AuthCallbackPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { login, clearLocalSession } = useAuth();
  const [error, setError] = useState('');
  const [actionComplete, setActionComplete] = useState(false);

  function goToLogin() {
    navigate('/login', { replace: true });
  }

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const state = params.get('state');
    const errorParam = params.get('error');

    if (errorParam) {
      setError(`${t('Giriş iptal edildi')}: ${errorParam}`);
      return;
    }

    if (!code) {
      if (!isOAuthLoginPending()) {
        clearOAuthPkceSession();
        clearLocalSession();
        setActionComplete(true);
        return;
      }
      setError(t('Geçersiz callback. Lütfen tekrar giriş yapın.'));
      return;
    }

    exchangeCodeForToken(code, state)
      .then(({ access_token, id_token, refresh_token }) => {
        login(access_token, id_token, refresh_token);
        navigate('/dashboard', { replace: true });
      })
      .catch(err => {
        if (err instanceof OAuthActionCompleteError) {
          clearOAuthPkceSession();
          clearLocalSession();
          setActionComplete(true);
          return;
        }
        setError(err.message);
      });
    // eslint-disable-next-line react-hooks/exhaustive-deps -- OAuth callback yalnızca mount'ta işlenir
  }, []);

  if (actionComplete) {
    return <ActionCompleteCard onGoToLogin={goToLogin} />;
  }

  if (error) {
    return <ErrorCard message={error} onGoToLogin={goToLogin} />;
  }

  return (
    <div className="min-h-screen bg-[#f5f6f8] flex items-center justify-center">
      <div className="text-center">
        <div className="bg-[#093eaa] p-3 rounded-xl inline-block mb-4">
          <Wallet className="w-8 h-8 text-white" />
        </div>
        <p className="text-gray-500 text-sm">{t('Giriş yapılıyor...')}</p>
        <div className="flex items-center justify-center gap-1.5 mt-3">
          <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
          <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
          <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
        </div>
      </div>
    </div>
  );
}
