import { useEffect, useState } from 'react';
import { X } from 'lucide-react';
import { changeMePassword, updateMeEmail, updateMeProfile } from '../../api/meApi';
import { keycloakAccountUrls } from '../../config/keycloakAccount';
import { mapPasswordChangeError } from './profilePasswordErrors';

function ModalShell({ title, open, onClose, children }) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-black/40">
      <div
        className="bg-white rounded-2xl shadow-xl border border-gray-200 w-full max-w-md max-h-[90vh] overflow-y-auto"
        role="dialog"
        aria-modal="true"
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h3 className="text-lg font-bold text-gray-900">{title}</h3>
          <button type="button" onClick={onClose} className="p-1 rounded-lg hover:bg-gray-100" aria-label="Kapat">
            <X className="w-5 h-5 text-gray-500" />
          </button>
        </div>
        <div className="p-6">{children}</div>
      </div>
    </div>
  );
}

function FieldInput({ label, id, type = 'text', value, onChange, autoComplete }) {
  return (
    <label className="block mb-4" htmlFor={id}>
      <span className="text-xs font-semibold text-gray-500 uppercase tracking-wide">{label}</span>
      <input
        id={id}
        type={type}
        value={value}
        onChange={onChange}
        autoComplete={autoComplete}
        className="mt-1 w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]"
      />
    </label>
  );
}

export function ProfileNameModal({ open, profile, onClose, onSuccess }) {
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open && profile) {
      setFirstName(profile.firstName || '');
      setLastName(profile.lastName || '');
      setError('');
    }
  }, [open, profile]);

  async function handleSubmit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await updateMeProfile({ firstName: firstName.trim(), lastName: lastName.trim() });
      onSuccess(res.message || 'Profil güncellendi.');
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || 'Profil güncellenemedi.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <ModalShell title="Ad / Soyad Düzenle" open={open} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <FieldInput
          label="Ad"
          id="profile-first-name"
          value={firstName}
          onChange={(e) => setFirstName(e.target.value)}
          autoComplete="given-name"
        />
        <FieldInput
          label="Soyad"
          id="profile-last-name"
          value={lastName}
          onChange={(e) => setLastName(e.target.value)}
          autoComplete="family-name"
        />
        {error && <p className="text-sm text-red-600 mb-4">{error}</p>}
        <button
          type="submit"
          disabled={busy}
          className="w-full bg-[#093eaa] text-white py-2.5 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90 disabled:opacity-60"
        >
          {busy ? 'Kaydediliyor...' : 'Kaydet'}
        </button>
      </form>
    </ModalShell>
  );
}

export function ProfileEmailModal({ open, profile, onClose, onRequiresReLogin }) {
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open && profile) {
      setEmail(profile.email || '');
      setError('');
    }
  }, [open, profile]);

  async function handleSubmit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      const res = await updateMeEmail({ email: email.trim() });
      onRequiresReLogin(res.message || 'Email adresiniz değiştirildi. Yeni email adresinizi doğrulamanız gerekiyor.');
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || 'Email güncellenemedi.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <ModalShell title="Email Değiştir" open={open} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <FieldInput
          label="Yeni email"
          id="profile-email"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
        />
        <p className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-lg p-3 mb-4">
          Email değişince doğrulama maili gönderilir ve oturumunuz güvenlik için sonlandırılır.
        </p>
        {error && <p className="text-sm text-red-600 mb-4">{error}</p>}
        <button
          type="submit"
          disabled={busy}
          className="w-full bg-[#093eaa] text-white py-2.5 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90 disabled:opacity-60"
        >
          {busy ? 'Kaydediliyor...' : 'Email Güncelle'}
        </button>
      </form>
    </ModalShell>
  );
}

export function ProfilePasswordModal({ open, onClose, onRequiresReLogin }) {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (open) {
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setError('');
    }
  }, [open]);

  async function handleSubmit(e) {
    e.preventDefault();
    if (newPassword !== confirmPassword) {
      setError('Yeni şifreler eşleşmiyor.');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const res = await changeMePassword({ currentPassword, newPassword, confirmPassword });
      onRequiresReLogin(res.message || 'Şifreniz güncellendi. Tekrar giriş yapın.');
      onClose();
    } catch (err) {
      setError(mapPasswordChangeError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <ModalShell title="Şifre Değiştir" open={open} onClose={onClose}>
      <form onSubmit={handleSubmit}>
        <FieldInput
          label="Mevcut şifre"
          id="profile-current-password"
          type="password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
          autoComplete="current-password"
        />
        <FieldInput
          label="Yeni şifre"
          id="profile-new-password"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          autoComplete="new-password"
        />
        <p className="text-xs text-gray-500 -mt-2 mb-4">
          En az 8 karakter, büyük/küçük harf ve rakam kullanın.
        </p>
        <FieldInput
          label="Yeni şifre tekrar"
          id="profile-confirm-password"
          type="password"
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
          autoComplete="new-password"
        />
        <p className="text-xs text-gray-500 mb-4">
          OTP aktif hesaplarda doğrulama başarısız olabilir. Bu durumda{' '}
          <a
            href={keycloakAccountUrls.changePassword}
            target="_blank"
            rel="noopener noreferrer"
            className="text-[#093eaa] font-semibold hover:underline"
          >
            Keycloak Hesap Ayarları
          </a>
          {' '}kullanın.
        </p>
        {error && <p className="text-sm text-red-600 mb-4">{error}</p>}
        <button
          type="submit"
          disabled={busy}
          className="w-full bg-[#093eaa] text-white py-2.5 rounded-xl text-sm font-bold hover:bg-[#093eaa]/90 disabled:opacity-60"
        >
          {busy ? 'Kaydediliyor...' : 'Şifreyi Güncelle'}
        </button>
      </form>
    </ModalShell>
  );
}
