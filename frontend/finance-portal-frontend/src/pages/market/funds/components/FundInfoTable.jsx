import { useState } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';
import { useTranslation } from '../../../../i18n/LanguageContext';

function InfoRow({ title, text }) {
  return (
    <div className="flex justify-between items-start py-3 border-b border-gray-100 last:border-0">
      <span className="text-sm text-gray-500 w-48 shrink-0">{title}</span>
      <span className="text-sm font-semibold text-gray-900 text-right">{text ?? '-'}</span>
    </div>
  );
}

export default function FundInfoTable({ fund }) {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(false);

  const labels = fund?.infoLabels ?? [];
  const minCount = fund?.minShowCount ?? 4;
  const visible = expanded ? labels : labels.slice(0, minCount);

  if (!labels.length) return null;

  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-6">
      <h2 className="font-bold text-gray-900 mb-4">{fund?.code} {t('Fon Bilgisi')}</h2>
      <div>
        {visible.map((label, i) => (
          <InfoRow key={i} title={label.title} text={label.text} />
        ))}
      </div>
      {labels.length > minCount && (
        <button
          onClick={() => setExpanded(e => !e)}
          className="mt-3 flex items-center gap-1.5 text-sm text-[#093eaa] font-semibold hover:underline"
        >
          {expanded ? (
            <><ChevronUp className="w-4 h-4" /> {t('Daha az göster')}</>
          ) : (
            <><ChevronDown className="w-4 h-4" /> {t('Daha fazla görüntüle')} ({labels.length - minCount} {t('daha')})</>
          )}
        </button>
      )}
    </div>
  );
}
