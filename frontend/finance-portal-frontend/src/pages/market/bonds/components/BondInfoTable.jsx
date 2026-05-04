function fmtNum(v, decimals = 2) {
  if (v == null) return null;
  const n = parseFloat(v);
  if (isNaN(n)) return null;
  return n.toLocaleString('tr-TR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
}

function Row({ label, value, highlight }) {
  return (
    <div className="flex justify-between items-center py-2.5 border-b border-gray-50 last:border-0">
      <span className="text-xs text-gray-500 shrink-0 mr-2">{label}</span>
      <span className={`text-xs font-semibold text-right ${highlight ? 'text-[#093eaa]' : 'text-gray-900'}`}>
        {value ?? '-'}
      </span>
    </div>
  );
}

export default function BondInfoTable({ bond }) {
  const dailyChange    = bond.dailyChange    != null ? parseFloat(bond.dailyChange)    : null;
  const dailyChangePct = bond.dailyChangePercent != null ? parseFloat(bond.dailyChangePercent) : null;
  const changePos = dailyChange != null && dailyChange > 0;

  const changeStr    = dailyChange    != null ? `${changePos ? '+' : ''}${fmtNum(dailyChange, 4)}` : null;
  const changePctStr = dailyChangePct != null ? `${changePos ? '+' : ''}%${fmtNum(Math.abs(dailyChangePct), 2)}` : null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-5">
      <h2 className="text-sm font-bold text-gray-900 mb-3">Kıymet Bilgileri</h2>

      <Row label="Kıymet Kodu"       value={bond.instrumentCode} highlight />
      <Row label="Tür"               value={bond.type} />
      <Row label="İhraç Tarihi"      value={bond.issueDate} />
      <Row label="Vade Tarihi"       value={bond.maturityDate} />
      <Row label="Kalan Gün"         value={bond.remainingDays != null ? `${bond.remainingDays} gün` : null} />

      <p className="text-xs text-gray-400 font-medium mt-3 mb-1 pt-2 border-t border-dashed border-gray-100">
        EVDS Değerleri
      </p>

      <Row label="Gösterge Değeri"   value={fmtNum(bond.indicatorValue, 2)} highlight />
      <Row label="Önceki Değer"      value={fmtNum(bond.previousValue, 2)} />
      <Row label="Günlük Değişim"    value={changeStr} />
      <Row label="Günlük Değişim %"  value={changePctStr} />
      <Row label="Kupon Faiz Oranı"  value={bond.couponRate != null ? `%${fmtNum(bond.couponRate, 2)}` : null} />

      <p className="text-xs text-gray-400 font-medium mt-3 mb-1 pt-2 border-t border-dashed border-gray-100">
        Meta
      </p>

      <Row label="Kaynak"            value={bond.source ?? 'TCMB EVDS'} />
      <Row label="Son Güncelleme"    value={bond.lastUpdated} />
    </div>
  );
}
