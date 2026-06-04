const PASSWORD_POLICY_MESSAGE =
  'Yeni şifre güvenlik kurallarını karşılamıyor. En az 8 karakter, büyük/küçük harf ve rakam içeren daha güçlü bir şifre deneyin.';

const PASSWORD_OTP_MESSAGE =
  'Şifre değiştirilemedi. Keycloak Hesap Ayarları üzerinden deneyebilirsiniz.';

/**
 * PATCH /api/v1/me/password hata yanıtlarını kullanıcı dostu metne çevirir.
 */
export function mapPasswordChangeError(err) {
  const status = err.response?.status;
  const data = err.response?.data ?? {};
  const message = typeof data.message === 'string' ? data.message : '';
  const errors = data.errors;

  if (message.includes('eşleşmiyor')) {
    return 'Yeni şifreler eşleşmiyor.';
  }

  if (
    message.includes('Mevcut şifre doğrulanamadı')
    || message.includes('Mevcut şifre hatalı')
  ) {
    if (message.includes('OTP') || message.includes('Hesap Ayarları')) {
      return PASSWORD_OTP_MESSAGE;
    }
    return 'Mevcut şifre hatalı.';
  }

  if (message === 'Validation failed' || hasPasswordFieldErrors(errors)) {
    return PASSWORD_POLICY_MESSAGE;
  }

  if (
    status === 400
    && (message.toLowerCase().includes('password')
      || message.toLowerCase().includes('policy')
      || message.toLowerCase().includes('invalid'))
  ) {
    return PASSWORD_POLICY_MESSAGE;
  }

  if (status === 503 || message.toLowerCase().includes('keycloak')) {
    if (message.includes('OTP') || message.toLowerCase().includes('grant')) {
      return PASSWORD_OTP_MESSAGE;
    }
    return PASSWORD_POLICY_MESSAGE;
  }

  if (message.includes('OTP') || message.toLowerCase().includes('grant')) {
    return PASSWORD_OTP_MESSAGE;
  }

  return 'Şifre güncellenemedi. Lütfen tekrar deneyin.';
}

function hasPasswordFieldErrors(errors) {
  if (!errors || typeof errors !== 'object') {
    return false;
  }
  return Boolean(errors.newPassword || errors.confirmPassword || errors.currentPassword);
}
