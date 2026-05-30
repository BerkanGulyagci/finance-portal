import { useState } from 'react';
import { X, ArrowLeftRight } from 'lucide-react';
import { addWatchlistItem } from '../../../api/portfolioApi';
import InstrumentSearchModal from '../../../components/instrument/InstrumentSearchModal';
import { useTranslation } from '../../../i18n/LanguageContext';

/**
 * İzleme listesine sembol ekleme modal'ı.
 * InstrumentSearchModal'ı kullanır, ardından not girişi gösterir.
 *
 * Props:
 *   portfolioId: string
 *   portfolioName: string
 *   onClose(): void
 *   onAdded(newItem): void
 */
export default function AddWatchlistItemModal({ portfolioId, portfolioName, onClose, onAdded }) {
  const { t } = useTranslation();
  const [step, setStep] = useState('search'); // 'search' | 'confirm'
  const [instrument, setInstrument] = useState(null);
  const [notes, setNotes] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  function handleInstrumentSelect(inst) {
    setInstrument(inst);
    setStep('confirm');
  }

  async function handleAdd() {
    setLoading(true);
    setError('');
    try {
      const item = await addWatchlistItem(portfolioId, {
        symbol: instrument.symbol,
        assetType: instrument.assetType,
        notes: notes.trim() || undefined,
      });
      onAdded(item);
      onClose();
    } catch (err) {
      const msg = err.response?.data?.message;
      setError(typeof msg === 'string' ? msg : t('Sembol eklenemedi.'));
    } finally {
      setLoading(false);
    }
  }

  if (step === 'search') {
    return (
      <InstrumentSearchModal
        portfolioName={portfolioName}
        onSelect={handleInstrumentSelect}
        onClose={onClose}
      />
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-[#1a1b22]/30 backdrop-blur-sm">
      <div className="bg-white rounded-xl shadow-2xl border border-[#e2e1eb] w-full max-w-sm text-[#1a1b22] flex flex-col overflow-hidden">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#e2e1eb]">
          <div>
            <h2 className="font-bold text-lg">{t('İzleme Listesine Ekle')}</h2>
            <p className="text-sm text-[#434653]">
              <span className="font-bold text-[#1a1b22]">{instrument?.symbol}</span>
              {instrument?.assetType && (
                <span className="ml-1 text-[#747684]">· {instrument.assetType}</span>
              )}
            </p>
          </div>
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={() => setStep('search')}
              title={t('Enstrümanı değiştir')}
              className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold text-[#093eaa] bg-[#093eaa]/[0.08] hover:bg-[#093eaa]/[0.15] transition-colors"
            >
              <ArrowLeftRight className="w-3.5 h-3.5" />
              {t('Değiştir')}
            </button>
            <button
              type="button"
              onClick={onClose}
              aria-label={t('Kapat')}
              className="text-[#747684] hover:text-[#1a1b22] rounded-full p-1 hover:bg-[#f3f3fc] transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <div className="p-6 space-y-4">
          <div>
            <label className="block text-xs font-semibold text-[#434653] mb-1.5">
              {t('Not')} <span className="font-normal text-[#747684]">{t('(isteğe bağlı)')}</span>
            </label>
            <input
              type="text"
              value={notes}
              onChange={e => setNotes(e.target.value)}
              placeholder={t('Kısa bir not...')}
              className="w-full bg-[#f3f3fc] border border-[#e2e1eb] rounded-xl px-4 py-2.5 text-sm text-[#1a1b22] placeholder-[#747684] focus:outline-none focus:border-[#002a7d] focus:ring-1 focus:ring-[#002a7d]"
            />
          </div>

          {error && (
            <p className="text-rose-600 text-sm bg-rose-50 border border-rose-200 px-3 py-2 rounded-lg">{error}</p>
          )}

          <div className="flex gap-3">
            <button
              onClick={onClose}
              className="flex-1 px-4 py-2.5 border border-[#e2e1eb] bg-white rounded-xl text-sm font-semibold text-[#434653] hover:bg-[#f3f3fc] transition-colors"
            >
              {t('İptal')}
            </button>
            <button
              onClick={handleAdd}
              disabled={loading}
              className="flex-1 bg-[#093eaa] hover:bg-[#002a7d] text-white px-4 py-2.5 rounded-xl text-sm font-semibold disabled:opacity-60 transition-colors"
            >
              {loading ? t('Ekleniyor...') : t('Ekle')}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
