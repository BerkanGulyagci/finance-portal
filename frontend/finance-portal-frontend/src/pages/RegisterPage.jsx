import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Eye, EyeOff } from 'lucide-react';
import { registerRequest } from '../api/authApi';
import { useTranslation } from '../i18n/LanguageContext';

export default function RegisterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', email: '', password: '', confirmPassword: '', firstName: '', lastName: '' });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  function handleChange(e) {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    if (form.password !== form.confirmPassword) {
      setError(t('Şifreler eşleşmiyor.'));
      return;
    }
    if (form.password.length < 6) {
      setError(t('Şifre en az 6 karakter olmalıdır.'));
      return;
    }
    setLoading(true);
    try {
      // Kullanıcı adı min 3 karakter kontrolü (frontend)
      if (form.username.trim().length < 3) {
        setError(t('Kullanıcı adı en az 3 karakter olmalıdır.'));
        setLoading(false);
        return;
      }
      await registerRequest({
        username: form.username,
        email: form.email,
        password: form.password,
        firstName: form.firstName,
        lastName: form.lastName,
      });
      setSuccess(t('Kayıt başarılı! Devam etmek için email adresinizi doğrulayın. Giriş sayfasına yönlendiriliyorsunuz...'));
      setTimeout(() => navigate('/login'), 2000);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bg-[#f5f6f8] flex items-center justify-center px-4 py-8 -mx-4 sm:-mx-6 lg:-mx-8 -my-8">
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
          <h2 className="text-xl font-bold text-gray-900 mb-2">{t('Hesap Oluşturun')}</h2>
          <p className="text-sm text-gray-500 mb-6">{t('Portföyünüzü yönetmek için ücretsiz kayıt olun.')}</p>

          {success && (
            <div className="bg-emerald-50 border border-emerald-200 text-emerald-700 text-sm px-4 py-3 rounded-xl mb-4">
              {success}
            </div>
          )}

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('Ad')}</label>
                <input name="firstName" type="text" value={form.firstName} onChange={handleChange}
                  required placeholder={t('Adınız')} disabled={loading}
                  className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] focus:border-transparent" />
              </div>
              <div>
                <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('Soyad')}</label>
                <input name="lastName" type="text" value={form.lastName} onChange={handleChange}
                  required placeholder={t('Soyadınız')} disabled={loading}
                  className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] focus:border-transparent" />
              </div>
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('Kullanıcı Adı')}</label>
              <input name="username" type="text" value={form.username} onChange={handleChange}
                required placeholder="ornek_kullanici" disabled={loading}
                className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] focus:border-transparent" />
              <p className="text-xs text-gray-400 mt-1">{t('Sadece İngilizce harf, rakam, nokta, alt çizgi ve tire kullanın.')}</p>
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('E-posta')}</label>
              <input name="email" type="email" value={form.email} onChange={handleChange}
                required placeholder="ornek@email.com" disabled={loading}
                className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] focus:border-transparent" />
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('Şifre')}</label>
              <div className="relative">
                <input name="password" type={showPassword ? 'text' : 'password'} value={form.password} onChange={handleChange}
                  required placeholder={t('En az 6 karakter')} disabled={loading}
                  className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] focus:border-transparent pr-12" />
                <button type="button" onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600">
                  {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                </button>
              </div>
            </div>

            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">{t('Şifre Tekrar')}</label>
              <input name="confirmPassword" type={showPassword ? 'text' : 'password'} value={form.confirmPassword} onChange={handleChange}
                required placeholder={t('Şifrenizi tekrar girin')} disabled={loading}
                className="w-full px-4 py-3 bg-gray-50 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa] focus:border-transparent" />
            </div>

            {error && (
              <div className="bg-red-50 border border-red-200 text-red-600 text-sm px-4 py-3 rounded-xl">
                {error}
              </div>
            )}

            <button type="submit" disabled={loading}
              className="w-full bg-[#093eaa] text-white py-3 rounded-xl font-bold text-sm hover:bg-[#093eaa]/90 transition-all disabled:opacity-60 disabled:cursor-not-allowed">
              {loading ? t('Kayıt yapılıyor...') : t('Kayıt Ol')}
            </button>
          </form>

          <p className="text-sm text-center text-gray-500 mt-6">
            {t('Zaten hesabınız var mı?')}{' '}
            <Link to="/login" className="text-[#093eaa] font-semibold hover:underline">{t('Giriş Yapın')}</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
