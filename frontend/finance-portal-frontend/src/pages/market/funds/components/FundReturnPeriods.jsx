const PERIODS = [
  { key: 'return1M', label: '1 Ay' },
  { key: 'return3M', label: '3 Ay' },
  { key: 'return6M', label: '6 Ay' },
  { key: 'return1Y', label: '1 Yıl' },
  { key: 'return3Y', label: '3 Yıl' },
  { key: 'return5Y', label: '5 Yıl' },
];

function ReturnCell({ label, value, last }) {
  const pos = (value ?? 0) >= 0;
  return (
    <div className={`p-4 text-center ${!last ? 'border-r border-gray-100' : ''}`}>
      <p className="text-xs text-gray-400 mb-1.5">{label}</p>
      <p className={`text-base font-black ${value == null ? 'text-gray-300' : pos ? 'text-emerald-600' : 'text-rose-600'}`}>
        {value == null ? '-' : `${pos ? '+' : ''}${value.toFixed(2)}%`}
      </p>
    </div>
  );
}

export default function FundReturnPeriods({ fund }) {
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100">
        <h2 className="font-bold text-gray-900">Dönem Getirileri</h2>
        <p className="text-xs text-gray-400 mt-0.5">Kaynak: HangiKredi</p>
      </div>
      <div className="grid grid-cols-3 sm:grid-cols-6 divide-y sm:divide-y-0 divide-x divide-gray-100">
        {PERIODS.map((p, i) => (
          <ReturnCell
            key={p.key}
            label={p.label}
            value={fund?.[p.key] ?? null}
            last={i === PERIODS.length - 1}
          />
        ))}
      </div>
    </div>
  );
}
