import { describe, it, expect } from 'vitest';
import { mapPasswordChangeError } from '../profilePasswordErrors';

// Üretilen kullanıcı dostu metinler kaynakta sabit; tam string yerine ayırt edici
// parçalarla eşleştiriyoruz (mesajlar uzun ama özleri kararlı).
const POLICY = 'Yeni şifre güvenlik kurallarını karşılamıyor';
const OTP = 'Keycloak Hesap Ayarları üzerinden deneyebilirsiniz';

// Hata nesnesini axios-benzeri { response: { status, data } } biçiminde kurar.
const makeErr = (status, data) => ({ response: { status, data } });

describe('mapPasswordChangeError', () => {
  describe('eşleşmiyor (öncelikli dal)', () => {
    it("mesaj 'eşleşmiyor' içeriyorsa şifreler eşleşmiyor metni döner", () => {
      const err = makeErr(400, { message: 'Yeni şifreler eşleşmiyor' });
      expect(mapPasswordChangeError(err)).toBe('Yeni şifreler eşleşmiyor.');
    });

    it("'eşleşmiyor' diğer tüm dallardan önce gelir (status 503 olsa bile)", () => {
      // 503 keycloak dalı da eşleşebilir ama 'eşleşmiyor' önce kontrol edilir.
      const err = makeErr(503, { message: 'keycloak: şifreler eşleşmiyor' });
      expect(mapPasswordChangeError(err)).toBe('Yeni şifreler eşleşmiyor.');
    });
  });

  describe('mevcut şifre hatalı dalı', () => {
    it("'Mevcut şifre doğrulanamadı' → mevcut şifre hatalı", () => {
      const err = makeErr(401, { message: 'Mevcut şifre doğrulanamadı' });
      expect(mapPasswordChangeError(err)).toBe('Mevcut şifre hatalı.');
    });

    it("'Mevcut şifre hatalı' → mevcut şifre hatalı", () => {
      const err = makeErr(401, { message: 'Mevcut şifre hatalı' });
      expect(mapPasswordChangeError(err)).toBe('Mevcut şifre hatalı.');
    });

    it("mevcut şifre + 'OTP' içeriyorsa OTP metnine yükselir", () => {
      const err = makeErr(401, { message: 'Mevcut şifre doğrulanamadı (OTP gerekli)' });
      expect(mapPasswordChangeError(err)).toContain(OTP);
    });

    it("mevcut şifre + 'Hesap Ayarları' içeriyorsa OTP metnine yükselir", () => {
      const err = makeErr(401, { message: 'Mevcut şifre hatalı, Hesap Ayarları kullanın' });
      expect(mapPasswordChangeError(err)).toContain(OTP);
    });
  });

  describe('validation / politika dalı', () => {
    it("mesaj tam olarak 'Validation failed' ise politika metni döner", () => {
      const err = makeErr(400, { message: 'Validation failed' });
      expect(mapPasswordChangeError(err)).toContain(POLICY);
    });

    it('errors.newPassword alan hatası varsa politika metni döner', () => {
      const err = makeErr(400, { message: '', errors: { newPassword: 'çok kısa' } });
      expect(mapPasswordChangeError(err)).toContain(POLICY);
    });

    it('errors.confirmPassword alan hatası varsa politika metni döner', () => {
      const err = makeErr(400, { message: '', errors: { confirmPassword: 'eşleşmedi' } });
      expect(mapPasswordChangeError(err)).toContain(POLICY);
    });

    it('errors.currentPassword alan hatası varsa politika metni döner', () => {
      const err = makeErr(400, { message: '', errors: { currentPassword: 'zorunlu' } });
      expect(mapPasswordChangeError(err)).toContain(POLICY);
    });

    it('alakasız alan hatası (örn email) politika dalını tetiklemez → genel mesaj', () => {
      // hasPasswordFieldErrors yalnızca *Password alanlarına bakar.
      const err = makeErr(422, { message: '', errors: { email: 'geçersiz' } });
      expect(mapPasswordChangeError(err)).toBe('Şifre güncellenemedi. Lütfen tekrar deneyin.');
    });
  });

  describe('status 400 + anahtar kelime dalı', () => {
    it("400 + mesajda 'password' (case-insensitive) → politika metni", () => {
      const err = makeErr(400, { message: 'Weak PASSWORD provided' });
      expect(mapPasswordChangeError(err)).toContain(POLICY);
    });

    it("400 + mesajda 'policy' → politika metni", () => {
      const err = makeErr(400, { message: 'Password policy violation' });
      expect(mapPasswordChangeError(err)).toContain(POLICY);
    });

    it("400 + mesajda 'invalid' → politika metni", () => {
      const err = makeErr(400, { message: 'Invalid value' });
      expect(mapPasswordChangeError(err)).toContain(POLICY);
    });

    it("anahtar kelime var ama status 400 değilse bu dal atlanır → genel mesaj", () => {
      // 'invalid' geçiyor fakat status 422; 400 şartı sağlanmadığı için düşer.
      const err = makeErr(422, { message: 'invalid request' });
      expect(mapPasswordChangeError(err)).toBe('Şifre güncellenemedi. Lütfen tekrar deneyin.');
    });
  });

  describe('keycloak / 503 dalı', () => {
    it("status 503 (mesaj boş) → politika metni", () => {
      const err = makeErr(503, { message: '' });
      expect(mapPasswordChangeError(err)).toContain(POLICY);
    });

    it("mesajda 'keycloak' geçerse (status farklı) → politika metni", () => {
      const err = makeErr(500, { message: 'keycloak unavailable' });
      expect(mapPasswordChangeError(err)).toContain(POLICY);
    });

    it("503 + 'OTP' → OTP metnine yükselir", () => {
      const err = makeErr(503, { message: 'OTP required by keycloak' });
      expect(mapPasswordChangeError(err)).toContain(OTP);
    });

    it("keycloak + 'grant' → OTP metnine yükselir", () => {
      const err = makeErr(500, { message: 'keycloak direct grant failed' });
      expect(mapPasswordChangeError(err)).toContain(OTP);
    });
  });

  describe('OTP / grant (son özel dal)', () => {
    it("mesajda 'OTP' geçerse OTP metni (status yokken)", () => {
      const err = { response: { data: { message: 'OTP enabled' } } };
      expect(mapPasswordChangeError(err)).toContain(OTP);
    });

    it("mesajda 'grant' geçerse OTP metni", () => {
      const err = makeErr(500, { message: 'direct grant disabled' });
      expect(mapPasswordChangeError(err)).toContain(OTP);
    });
  });

  describe('varsayılan / kenar durumlar', () => {
    it('eşleşen hiçbir dal yoksa genel hata metni döner', () => {
      const err = makeErr(500, { message: 'beklenmeyen sunucu hatası' });
      expect(mapPasswordChangeError(err)).toBe('Şifre güncellenemedi. Lütfen tekrar deneyin.');
    });

    it('response yokken (status/data undefined) çökmez → genel mesaj', () => {
      // err.response?.status undefined, data ?? {} → boş; message '' olur.
      expect(mapPasswordChangeError({})).toBe('Şifre güncellenemedi. Lütfen tekrar deneyin.');
    });

    it('data.message string değilse (örn number) boş kabul edilir → genel mesaj', () => {
      const err = makeErr(500, { message: 12345 });
      expect(mapPasswordChangeError(err)).toBe('Şifre güncellenemedi. Lütfen tekrar deneyin.');
    });

    it('data hiç yokken (response var, data yok) çökmez → genel mesaj', () => {
      const err = { response: { status: 500 } };
      expect(mapPasswordChangeError(err)).toBe('Şifre güncellenemedi. Lütfen tekrar deneyin.');
    });

    it('errors null ise alan-hata kontrolü false döner → genel mesaj', () => {
      const err = makeErr(500, { message: '', errors: null });
      expect(mapPasswordChangeError(err)).toBe('Şifre güncellenemedi. Lütfen tekrar deneyin.');
    });

    it('errors obje değil (string) ise alan-hata kontrolü false → genel mesaj', () => {
      const err = makeErr(500, { message: '', errors: 'oops' });
      expect(mapPasswordChangeError(err)).toBe('Şifre güncellenemedi. Lütfen tekrar deneyin.');
    });
  });
});
