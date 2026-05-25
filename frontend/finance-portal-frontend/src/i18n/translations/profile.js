const en = {
  // ProfilePage
  'Profilim': 'My Profile',
  'Hesap bilgilerinizi görüntüleyin ve yönetin': 'View and manage your account information',
  'Profil bilgisi yüklenemedi.': 'Could not load profile information.',
  'Yükleniyor...': 'Loading...',
  'Tekrar dene': 'Try again',
  'Kullanıcı adı': 'Username',
  'Email': 'Email',
  'Ad': 'First name',
  'Soyad': 'Last name',
  'Email doğrulama': 'Email verification',
  'Doğrulandı': 'Verified',
  'Doğrulanmadı': 'Not verified',
  'Hesap durumu': 'Account status',
  'Aktif': 'Active',
  'Pasif': 'Inactive',
  'Roller': 'Roles',
  'Hesap Bilgileri': 'Account Information',
  'Bilgilerimi Düzenle': 'Edit My Information',
  'Şifremi Değiştir': 'Change My Password',
  'Email Değiştir': 'Change Email',
  'Bilgileri yenile': 'Refresh information',
  'Yenile': 'Refresh',
  'Email doğrulama sayfası': 'Email verification page',

  // ProfileAccountModals - shared
  'Kapat': 'Close',
  'Kaydet': 'Save',
  'Kaydediliyor...': 'Saving...',

  // ProfileNameModal
  'Ad / Soyad Düzenle': 'Edit First Name / Last Name',
  'Profil güncellendi.': 'Profile updated.',
  'Profil güncellenemedi.': 'Could not update profile.',

  // ProfileEmailModal
  'Yeni email': 'New email',
  'Email adresiniz değiştirildi. Yeni email adresinizi doğrulamanız gerekiyor.':
    'Your email address has been changed. You need to verify your new email address.',
  'Email güncellenemedi.': 'Could not update email.',
  'Email değişince doğrulama maili gönderilir ve oturumunuz güvenlik için sonlandırılır.':
    'When your email changes, a verification email is sent and your session is ended for security.',
  'Email Güncelle': 'Update Email',

  // ProfilePasswordModal
  'Şifre Değiştir': 'Change Password',
  'Mevcut şifre': 'Current password',
  'Yeni şifre': 'New password',
  'Yeni şifre tekrar': 'Confirm new password',
  'En az 8 karakter, büyük/küçük harf ve rakam kullanın.':
    'Use at least 8 characters with upper/lowercase letters and numbers.',
  'OTP aktif hesaplarda doğrulama başarısız olabilir. Bu durumda':
    'Verification may fail on OTP-enabled accounts. In that case use',
  'Keycloak Hesap Ayarları': 'Keycloak Account Settings',
  'kullanın.': 'instead.',
  'Yeni şifreler eşleşmiyor.': 'New passwords do not match.',
  'Şifreniz güncellendi. Tekrar giriş yapın.': 'Your password has been updated. Please sign in again.',
  'Şifreyi Güncelle': 'Update Password',

  // profilePasswordErrors return values (wrapped at call site)
  'Yeni şifre güvenlik kurallarını karşılamıyor. En az 8 karakter, büyük/küçük harf ve rakam içeren daha güçlü bir şifre deneyin.':
    'New password does not meet the security rules. Try a stronger password with at least 8 characters, upper/lowercase letters and numbers.',
  'Şifre değiştirilemedi. Keycloak Hesap Ayarları üzerinden deneyebilirsiniz.':
    'Password could not be changed. You can try via Keycloak Account Settings.',
  'Mevcut şifre hatalı.': 'Current password is incorrect.',
  'Şifre güncellenemedi. Lütfen tekrar deneyin.': 'Could not update password. Please try again.',
};

export default { en };
