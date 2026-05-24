import { useEffect, useState } from 'react';
import { Star } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';
import { useWatchlist } from '../../context/WatchlistContext';
import { useTranslation } from '../../i18n/LanguageContext';
import InstrumentTargetModal from './InstrumentTargetModal';

/**
 * Liste satırlarının sağına eklenen küçük yıldız. Enstrüman izleme listesinde mi gösterir;
 * tıklayınca **birleşik seçici** açılır (izleme listesi VEYA varlık portföyü seçilebilir).
 * Yalnız giriş yapan kullanıcıya görünür.
 */
export default function WatchlistStar({ assetType, symbol, name, price, size = 16, className = '' }) {
  const { isAuthenticated } = useAuth();
  const { ensureLoaded, isWatched } = useWatchlist();
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  useEffect(() => { if (isAuthenticated) ensureLoaded(); }, [isAuthenticated, ensureLoaded]);

  if (!isAuthenticated || !symbol) return null;

  const watched = isWatched(assetType, symbol);

  function onClick(e) {
    e.preventDefault();
    e.stopPropagation();
    setOpen(true);
  }

  return (
    <>
      <button
        type="button"
        onClick={onClick}
        title={watched ? t('İzleniyor — listeye/portföye ekle') : t('İzleme listesine / portföye ekle')}
        className={`p-1 rounded-md transition-colors ${
          watched ? 'text-amber-400 hover:text-amber-500' : 'text-gray-300 hover:text-amber-400'
        } ${className}`}
      >
        <Star style={{ width: size, height: size }} className={watched ? 'fill-current' : ''} />
      </button>

      {open && (
        <InstrumentTargetModal
          instrument={{ assetType, symbol, name: name || symbol, price }}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}
