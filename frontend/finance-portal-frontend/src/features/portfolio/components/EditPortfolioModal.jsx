import { useState } from 'react';
import { X, Save } from 'lucide-react';
import { updatePortfolio } from '../../../api/portfolioApi';
import { useTranslation } from '../../../context/LanguageContext';

export default function EditPortfolioModal({ portfolio, onClose, onUpdated }) {
  const { t } = useTranslation();
  const [name, setName]               = useState(portfolio.name ?? '');
  const [description, setDescription] = useState(portfolio.description ?? '');
  const [saving, setSaving]           = useState(false);
  const [error, setError]             = useState('');

  async function handleSubmit(e) {
    e.preventDefault();
    const trimmedName = name.trim();
    if (!trimmedName) {
      setError(t('Portföy adı boş bırakılamaz.'));
      return;
    }
    setSaving(true);
    setError('');
    try {
      const updated = await updatePortfolio(portfolio.id, {
        name: trimmedName,
        description: description.trim() || null,
      });
      onUpdated(updated);
      onClose();
    } catch (err) {
      const msg = err?.response?.data?.message || err?.message || t('Güncelleme başarısız.');
      setError(msg);
    } finally {
      setSaving(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
      role="button"
      tabIndex={0}
      onClick={e => { if (e.target === e.currentTarget) onClose(); }}
      onKeyDown={e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); if (e.target === e.currentTarget) onClose(); } }}
    >
      <div className="bg-white rounded-2xl shadow-2xl w-full max-w-md">
        {/* Başlık */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="text-base font-bold text-gray-900">{t('Portföyü Düzenle')}</h2>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-600 transition-colors"
            aria-label={t('Kapat')}
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="px-6 py-5 space-y-4">
          {/* Mevcut tip bilgisi — read-only */}
          <div className="text-xs text-gray-400 bg-gray-50 rounded-lg px-3 py-2">
            <span className="font-medium text-gray-500">{t('Tür:')}</span>{' '}
            {portfolio.portfolioType === 'WATCHLIST' ? t('İzleme Listesi') : t('Varlık Portföyü')}
            {'  ·  '}
            <span className="font-medium text-gray-500">{t('Para birimi:')}</span>{' '}
            {portfolio.currency}
          </div>

          <div className="space-y-1">
            <label className="block text-sm font-medium text-gray-700">
              {t('Portföy Adı')} <span className="text-rose-500">*</span>
            </label>
            <input
              type="text"
              value={name}
              onChange={e => setName(e.target.value)}
              maxLength={100}
              required
              className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-[#093eaa] focus:ring-1 focus:ring-[#093eaa] outline-none transition"
              placeholder={t('Portföy adı')}
            />
          </div>

          <div className="space-y-1">
            <label className="block text-sm font-medium text-gray-700">{t('Açıklama')}</label>
            <textarea
              value={description}
              onChange={e => setDescription(e.target.value)}
              maxLength={500}
              rows={3}
              className="w-full rounded-xl border border-gray-200 px-3 py-2 text-sm text-gray-900 placeholder-gray-400 focus:border-[#093eaa] focus:ring-1 focus:ring-[#093eaa] outline-none transition resize-none"
              placeholder={t('Opsiyonel açıklama')}
            />
          </div>

          {error && (
            <p className="text-sm text-rose-600 bg-rose-50 border border-rose-200 rounded-xl px-3 py-2">
              {error}
            </p>
          )}

          <div className="flex justify-end gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              disabled={saving}
              className="px-4 py-2 rounded-xl text-sm font-medium text-gray-600 hover:bg-gray-100 transition-colors disabled:opacity-50"
            >
              {t('İptal')}
            </button>
            <button
              type="submit"
              disabled={saving}
              className="flex items-center gap-2 px-5 py-2 rounded-xl bg-[#093eaa] text-white text-sm font-semibold hover:bg-[#093eaa]/90 transition-colors disabled:opacity-50"
            >
              <Save className="w-4 h-4" />
              {saving ? t('Kaydediliyor…') : t('Kaydet')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
