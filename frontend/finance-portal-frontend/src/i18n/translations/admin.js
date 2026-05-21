const en = {
  // AdminUsersPage
  'Kullanıcı Yönetimi': 'User Management',
  'Keycloak üzerinden kayıtlı kullanıcıları görüntüleyin ve yönetin.':
    'View and manage users registered through Keycloak.',
  'Portföye dön': 'Back to portfolio',

  // UnauthorizedPage
  'Erişim Reddedildi': 'Access Denied',
  'Bu sayfaya yalnızca yönetici (ADMIN) hesapları erişebilir.':
    'Only administrator (ADMIN) accounts can access this page.',
  'Ana Sayfaya Dön': 'Back to Home',
  'Portföyüm': 'My Portfolio',
  'Yetkisiz Erişim': 'Unauthorized Access',
  'Bu sayfaya erişim yetkiniz yok': 'You do not have permission to access this page',

  // AdminListStates
  'Yükleniyor...': 'Loading...',
  'Tekrar dene': 'Try again',
  'Kullanıcı bulunamadı.': 'No users found.',

  // AdminPageHeader
  'Yönetim': 'Administration',
  'Yenile': 'Refresh',

  // AdminUserActions
  'Detay': 'Details',
  'Ban': 'Ban',
  'Unban': 'Unban',
  'Admin': 'Admin',

  // AdminUserDetailModal
  'Kullanıcı Detayı': 'User Details',
  'Kapat': 'Close',
  'Kullanıcı bilgisi yüklenemedi.': 'Failed to load user information.',
  'Kullanıcı bilgileri yükleniyor...': 'Loading user information...',
  'Siz': 'You',
  'Kullanıcı adı': 'Username',
  'Ad': 'First Name',
  'Soyad': 'Last Name',
  'E-posta': 'Email',
  'E-posta doğrulama': 'Email Verification',
  'Doğrulandı': 'Verified',
  'Doğrulanmadı': 'Not Verified',
  'Keycloak hesap': 'Keycloak Account',
  'Etkin': 'Enabled',
  'Devre dışı': 'Disabled',
  'Ban bitiş': 'Ban Ends',
  'Roller': 'Roles',
  'Kullanıcı ID': 'User ID',
  'Banla': 'Ban',

  // AdminUsersPagination
  'Sayfa': 'Page',
  'Önceki': 'Previous',
  'Sonraki': 'Next',

  // AdminUsersSearchBar
  'Tümü': 'All',
  'Aktif': 'Active',
  'Banlı': 'Banned',
  'Kullanıcı adı veya e-posta ara...': 'Search by username or email...',
  'Kullanıcı ara': 'Search users',
  'Aramayı temizle': 'Clear search',
  'Durum': 'Status',
  'kullanıcı': 'users',
  'Sonraki sayfa mevcut': 'Next page available',

  // AdminUsersTable
  'Kullanıcı': 'User',
  'İşlem': 'Actions',

  // BanUserModal
  'Dakika': 'Minutes',
  'Saat': 'Hours',
  'Gün': 'Days',
  'Süre pozitif tam sayı olmalıdır.': 'Duration must be a positive integer.',
  'Kalıcı ban uygulanacak': 'Permanent ban will be applied',
  'Kullanıcı hesabı süresiz olarak devre dışı bırakılır ve oturumları sonlandırılır.':
    'The user account will be disabled indefinitely and sessions will be terminated.',
  'Geçici ban': 'Temporary ban',
  'Süre dolduğunda hesap otomatik olarak yeniden etkinleştirilir.':
    'The account will be automatically re-enabled when the duration expires.',
  'Kullanıcıyı Banla': 'Ban User',
  'Kalıcı veya süreli ban uygulayın': 'Apply a permanent or timed ban',
  'Ad soyad yok': 'No name provided',
  'E-posta yok': 'No email',
  'Ban türü': 'Ban type',
  'Kalıcı ban': 'Permanent ban',
  'Süre': 'Duration',
  'Örn. 8': 'e.g. 8',
  'İptal': 'Cancel',
  'Banlanıyor...': 'Banning...',
  'Banı Uygula': 'Apply Ban',

  // UserStatusBadge
  'Bitiş': 'Ends',
  'Bilinmiyor': 'Unknown',

  // useAdminUsers (toast/error messages)
  'Sunucuya ulaşılamıyor.': 'Cannot reach the server.',
  'Bu işlem için yönetici yetkisi gerekir.': 'Administrator privileges are required for this action.',
  'Oturum süreniz dolmuş olabilir. Lütfen tekrar giriş yapın.':
    'Your session may have expired. Please log in again.',
  'Kullanıcılar yüklenemedi ({status}).': 'Failed to load users ({status}).',
  'Ban işlemi başarılı.': 'Ban successful.',
  'Ban işlemi başarısız.': 'Ban failed.',
  '{username} kullanıcısının banını kaldırmak istediğinize emin misiniz?':
    'Are you sure you want to remove the ban for user {username}?',
  'Ban kaldırıldı.': 'Ban removed.',
  'Unban işlemi başarısız.': 'Unban failed.',

  // Additional common admin labels
  'Kullanıcılar': 'Users',
  'Yasakla': 'Ban',
  'Yasağı Kaldır': 'Unban',
  'Rol Ata': 'Assign Role',
  'Yasaklı': 'Banned',
};

export default { en };
