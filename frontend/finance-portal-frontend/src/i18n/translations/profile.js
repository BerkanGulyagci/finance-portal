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

  // TickerCustomizer — kategori başlıkları + UI metinleri
  'Piyasa Şeridi': 'Market Ticker',
  'Piyasa şeridine hangi varlıkları koyacağını seç':
    'Choose which assets to put on the market ticker',
  '{count} sembol seçili': '{count} symbols selected',
  'Piyasa Şeridini Özelleştir': 'Customize Market Ticker',
  'Sayfanın üstündeki piyasa şeridinde hangi varlıkların görüneceğini seçin.':
    'Choose which assets appear on the market ticker at the top of the page.',
  'TCMB Döviz': 'CBRT FX',
  'Banka Kurları': 'Bank FX',
  'Endeksler (BIST)': 'Indices (BIST)',
  'Altın': 'Gold',
  'Kripto': 'Crypto',
  'Ekonomi': 'Economy',
  'Hepsi': 'All',
  'Hiçbiri': 'None',
  'Eklediğin Varlıklar': 'Added Assets',
  'Varlık Ekle': 'Add Asset',
  'Şeride istediğin hisse, kripto, döviz, altın, fon… ekleyebilirsin.':
    'You can add any stock, crypto, FX, gold or fund to the ticker.',
  'Kaldır': 'Remove',
  'şerit': 'ticker',
  // Akış hızı segmented kontrol
  'Akış Hızı': 'Scroll Speed',
  'Yavaş': 'Slow',
  'Normal': 'Normal',
  'Hızlı': 'Fast',

  // MarginAlertSettings (VİOP teminat uyarısı kartı — Profil sağ kolon)
  'Teminat Uyarısı (VİOP)': 'Margin Alert (Futures)',
  // HoldingsDetail — yeni sekme adı
  'Teminat Uyarısı': 'Margin Alert',
  'Açık VİOP pozisyonlarının marjin oranı bu eşiğin altına düşerse bildirim ve e-posta alırsınız. 0 = kapalı.':
    'You will receive notifications and email if the margin ratio of any open futures position falls below this threshold. 0 = disabled.',
  'Marjin oranı eşik (%)': 'Margin ratio threshold (%)',
  'Kapalı': 'Off',
  'Hızlı seç:': 'Quick pick:',
  'Teminat uyarısı kaydedildi.': 'Margin alert saved.',
  'Bu eşik tüm portföylerinizdeki tüm VİOP pozisyonları için geçerlidir.':
    'This threshold applies to all open futures positions across all your portfolios.',

  // AlarmCreateModal — eski sistem etiketi
  'Teminat Oranı (VİOP) — Eski Sistem': 'Margin Ratio (Futures) — Legacy',
  'Teminat uyarıları artık Profil sayfasından tek noktadan yönetilir.':
    'Margin alerts are now managed centrally from the Profile page.',
  'Profil': 'Profile',

  // AlarmsManager — eski sistem rozeti
  '(eski sistem)': '(legacy)',
  'Eski sistem alarmları düzenlenemez. Yeni alarm Profil sayfasından yapılır.':
    'Legacy alarms cannot be edited. Configure new margin alerts from the Profile page.',

  // AlarmsPage description (yeni iki cümle)
  'Fiyat koşulu sağlandığında uygulama-içi bildirim ve onaylı e-postanıza mail alırsınız.':
    'When the price condition is met you will receive an in-app notification and an email to your verified address.',
  'VİOP teminat uyarıları için Profil sayfasındaki Teminat Uyarısı bölümünü kullanın.':
    'For futures margin alerts use the Margin Alert section on the Profile page.',
};

export default { en };
