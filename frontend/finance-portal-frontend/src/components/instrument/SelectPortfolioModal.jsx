import { Link } from 'react-router-dom';
import { X, Wallet, ChevronRight, Plus } from 'lucide-react';
import { useTranslation } from '../../i18n/LanguageContext';

/**
 * "Portföye Ekle" akışının ilk adımı: kullanıcı birden fazla varlık portföyüne sahipse
 * önce hedef portföyü seçtirir. Tek portföy varsa çağıran bileşen bu adımı atlar.
 *
 * @param portfolios HOLDINGS tipindeki portföyler [{id, name, totalMarketValue, currency, holdings}]
 * @param onSelect   (portfolioId) => void
 * @param onClose    () => void
 */
export default function SelectPortfolioModal({ portfolios = [], onSelect, onClose }) {
  const { t } = useTranslation();

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1a1b22]/30 backdrop-blur-sm">
      <div className="bg-white rounded-2xl shadow-2xl border border-[#e2e1eb] w-full max-w-md overflow-hidden">
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <h2 className="font-bold text-gray-900 text-lg">{t('Portföy Seç')}</h2>
          <button onClick={onClose} className="text-gray-400 hover:text-gray-600 rounded-full p-1 hover:bg-gray-50">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-4 max-h-[60vh] overflow-y-auto">
          {portfolios.length === 0 ? (
            <div className="text-center py-8">
              <p className="text-sm text-gray-500 mb-4">{t('Henüz bir varlık portföyünüz yok.')}</p>
              <Link
                to="/portfolio"
                className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-[#093eaa] text-white text-sm font-semibold hover:bg-[#0a2966] transition-colors"
              >
                <Plus className="w-4 h-4" /> {t('Portföy Oluştur')}
              </Link>
            </div>
          ) : (
            <div className="space-y-2">
              {portfolios.map(p => (
                <button
                  key={p.id}
                  onClick={() => onSelect(p.id)}
                  className="w-full flex items-center justify-between gap-3 px-4 py-3 rounded-xl border border-gray-200 hover:border-[#093eaa] hover:bg-[#093eaa]/5 transition-all text-left"
                >
                  <span className="flex items-center gap-3 min-w-0">
                    <span className="w-9 h-9 rounded-lg bg-[#093eaa]/10 text-[#093eaa] flex items-center justify-center flex-shrink-0">
                      <Wallet className="w-4 h-4" />
                    </span>
                    <span className="min-w-0">
                      <span className="block font-semibold text-gray-900 truncate">{p.name}</span>
                      {p.holdings && (
                        <span className="block text-xs text-gray-400">
                          {p.holdings.length} {t('varlık')}
                        </span>
                      )}
                    </span>
                  </span>
                  <ChevronRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
