import { fmt } from './commodityConstants';

function fmtVolume(v) {
  if (v == null) return '-';
  return parseInt(v).toLocaleString('tr-TR');
}

export default function CommodityDetailOhlcCards({ points }) {
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
    <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
      {cards.map(card => (
        <div key={card.label} className="bg-white rounded-2xl border border-gray-200 shadow-sm p-4">
          <p className="text-xs font-bold text-gray-400 uppercase tracking-wider mb-1">{card.label}</p>
          <p className="text-base font-black text-gray-900">
            {card.value != null
              ? card.isVolume
                ? fmtVolume(card.value)
                : `${fmt(card.value)} USD`
              : '-'}
          </p>
        </div>
      ))}
    </div>
  );
}
