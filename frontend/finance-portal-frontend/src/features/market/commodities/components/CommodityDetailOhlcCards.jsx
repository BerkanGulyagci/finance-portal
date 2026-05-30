import { fmt } from './commodityConstants';
import { useTranslation } from '../../../../i18n/LanguageContext';

function fmtVolume(v) {
  if (v == null) return '-';
  return parseInt(v).toLocaleString('tr-TR');
}

export default function CommodityDetailOhlcCards({ points }) {
  const { t } = useTranslation();
  if (!points?.length) return null;

  const last = points[points.length - 1];
  if (!last) return null;

  const cards = [
    { label: 'Açılış',  value: last.displayOpen  ?? last.rawOpen  },
    { label: 'Yüksek',  value: last.displayHigh  ?? last.rawHigh  },
    { label: 'Düşük',   value: last.displayLow   ?? last.rawLow   },
    { label: 'Kapanış', value: last.displayClose ?? last.rawClose },
  ];

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm px-5 py-3">
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-x-6 gap-y-2">
        {cards.map(card => (
          <div key={card.label} className="flex items-baseline justify-between gap-2 sm:block">
            <p className="text-[11px] font-bold text-gray-400 uppercase tracking-wider">{t(card.label)}</p>
            <p className="text-sm font-black text-gray-900">
              {card.value != null
                ? card.isVolume
                  ? fmtVolume(card.value)
                  : `${fmt(card.value)} USD`
                : '-'}
            </p>
          </div>
        ))}
      </div>
    </div>
  );
}
