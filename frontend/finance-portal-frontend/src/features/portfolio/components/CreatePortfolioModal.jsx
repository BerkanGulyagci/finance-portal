import { useState } from 'react';
import { X, Eye, Briefcase } from 'lucide-react';
import { createPortfolio, getMyPortfolios } from '../../../api/portfolioApi';
import { useTranslation } from '../../../context/LanguageContext';

const TYPE_OPTIONS = [
  {
    value: 'WATCHLIST',
    icon: Eye,
    label: 'İzleme Listesi',
    desc: 'Sadece fiyatını takip etmek istediğin sembolleri ekle.',
    color: 'sky',
  },
  {
    value: 'HOLDINGS',
    icon: Briefcase,
    label: 'Varlık Portföyü',
    desc: 'Alış/satış işlemleri, miktar, maliyet ve kâr/zarar takibi yap.',
    color: 'emerald',
  },
];

/**
 * 2 adımlı portföy oluşturma modal'ı.
 * Adım 1: Tip seçimi (WATCHLIST / HOLDINGS)
 * Adım 2: İsim, açıklama, currency
 *
 * Props:
 *   onClose()          → modal'ı kapat
 *   onCreated(list)    → yeni portföy listesiyle çağrılır
 */
export default function CreatePortfolioModal({ onClose, onCreated }) {
  const { t } = useTranslation();
  const [selectedType, setSelectedType] = useState('HOLDINGS');
  const [form, setForm] = useState({ name: '', description: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function set(key, val) {
    setForm(f => ({ ...f, [key]: val }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    if (!form.name.trim()) { setError(t('Portföy adı zorunludur.')); return; }
    setLoading(true);
    setError('');
    try {
      await createPortfolio({
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        currency: 'TRY',
        portfolioType: selectedType,
      });
      // Response'a güvenmek yerine listeyi yeniden çek
      const updated = await getMyPortfolios();
      onCreated(updated);
      onClose();
    } catch (err) {
      setError(err.response?.data?.message || t('Portföy oluşturulamadı.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="font-bold text-gray-900 text-lg">{t('Portföy tipini seçin')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 transition-colors">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="p-6 space-y-5">
          {/* Tip seçimi */}
          <div className="grid grid-cols-2 gap-3">
            {TYPE_OPTIONS.map(opt => {
              const Icon = opt.icon;
              const active = selectedType === opt.value;
              return (
                <button
                  key={opt.value}
                  type="button"
                  onClick={() => setSelectedType(opt.value)}
                  className={`flex flex-col items-center gap-2 p-4 rounded-xl border-2 transition-all text-center ${
                    active
                      ? opt.color === 'sky'
                        ? 'border-sky-500 bg-sky-50'
                        : 'border-emerald-500 bg-emerald-50'
                      : 'border-gray-200 hover:border-gray-300 bg-white'
                  }`}
                >
                  <div className={`p-2 rounded-full ${
                    active
                      ? opt.color === 'sky' ? 'bg-sky-100' : 'bg-emerald-100'
                      : 'bg-gray-100'
                  }`}>
                    <Icon className={`w-6 h-6 ${
                      active
                        ? opt.color === 'sky' ? 'text-sky-600' : 'text-emerald-600'
                        : 'text-gray-400'
                    }`} />
                  </div>
                  <span className={`font-bold text-sm ${active ? 'text-gray-900' : 'text-gray-600'}`}>
                    {t(opt.label)}
                  </span>
                  <span className="text-xs text-gray-400 leading-tight">{t(opt.desc)}</span>
                </button>
              );
            })}
          </div>

          {/* Portföy adı */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">
              {t('Portföy Adı *')}
            </label>
            <input
              type="text"
              value={form.name}
              onChange={e => set('name', e.target.value)}
              placeholder={t('Örn: Hisse Portföyüm')}
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]"
            />
          </div>

          {/* Açıklama */}
          <div>
            <label className="block text-sm font-semibold text-gray-700 mb-1.5">
              {t('Açıklama')} <span className="text-gray-400 font-normal">{t('(isteğe bağlı)')}</span>
            </label>
            <input
              type="text"
              value={form.description}
              onChange={e => set('description', e.target.value)}
              placeholder={t('Kısa bir açıklama...')}
              className="w-full px-4 py-2.5 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-[#093eaa]/30 focus:border-[#093eaa]"
            />
          </div>

          {error && (
            <p className="text-rose-500 text-sm bg-rose-50 px-3 py-2 rounded-lg">{error}</p>
          )}

          {/* Butonlar */}
          <div className="flex gap-3 pt-1">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-gray-200 rounded-xl text-sm font-semibold hover:bg-gray-50 transition-colors"
            >
              {t('İptal')}
            </button>
            <button
              type="submit"
              disabled={loading}
              className="flex-1 bg-[#093eaa] text-white px-4 py-2.5 rounded-xl text-sm font-semibold hover:bg-[#093eaa]/90 disabled:opacity-60 transition-colors"
            >
              {loading ? t('Oluşturuluyor...') : t('Oluştur')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
