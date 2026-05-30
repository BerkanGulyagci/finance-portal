import { useTranslation } from '../../../../context/LanguageContext';
import WhatIfComparison from './WhatIfComparison';

export default function PortfolioStatsSummary({ portfolioId, holdings, valuesHidden }) {
  const { t } = useTranslation();
  const count = holdings?.length ?? 0;

  if (!count) {
    return (
      <div className="p-12 text-center text-sm text-gray-400">{t('Portföyde pozisyon bulunamadı.')}</div>
    );
  }

  return (
    <div className="p-4 sm:p-5 min-w-0">
      {portfolioId && (
        <WhatIfComparison portfolioId={portfolioId} holdings={holdings} valuesHidden={valuesHidden} />
      )}
    </div>
  );
}
