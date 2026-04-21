import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Wallet } from 'lucide-react';
import { exchangeCodeForToken } from '../api/authApi';
import { useAuth } from '../context/AuthContext';

export default function AuthCallbackPage() {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const state = params.get('state');
    const errorParam = params.get('error');

    if (errorParam) {
      setError(`Giriş iptal edildi: ${errorParam}`);
      return;
    }

    if (!code) {
      setError('Geçersiz callback. Lütfen tekrar giriş yapın.');
      return;
    }

    exchangeCodeForToken(code, state)
      .then(({ access_token, id_token }) => {
        login(access_token, id_token);
        navigate('/portfolio', { replace: true });
      })
      .catch(err => {
        setError(err.message);
      });
  }, []);

  if (error) {
    return (
      <div className="min-h-screen bg-[#f5f6f8] flex items-center justify-center px-4">
        <div className="bg-white rounded-2xl shadow-sm border border-gray-200 p-8 max-w-md w-full text-center">
          <div className="text-rose-500 text-sm mb-4">{error}</div>
          <button onClick={() => navigate('/login')}
            className="bg-[#093eaa] text-white px-6 py-2 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90">
            Giriş Sayfasına Dön
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#f5f6f8] flex items-center justify-center">
      <div className="text-center">
        <div className="bg-[#093eaa] p-3 rounded-xl inline-block mb-4">
          <Wallet className="w-8 h-8 text-white" />
        </div>
        <p className="text-gray-500 text-sm">Giriş yapılıyor...</p>
        <div className="flex items-center justify-center gap-1.5 mt-3">
          <div className="w-2 h-2 bg-[#093eaa] rounded-full animate-bounce" />
          <div className="w-2 h-2 bg-[#093eaa]/60 rounded-full animate-bounce [animation-delay:100ms]" />
          <div className="w-2 h-2 bg-[#093eaa]/30 rounded-full animate-bounce [animation-delay:200ms]" />
        </div>
      </div>
    </div>
  );
}
