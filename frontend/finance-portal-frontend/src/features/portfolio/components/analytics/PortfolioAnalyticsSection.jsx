import GridBoard from '../../../../components/common/GridBoard';
import { ANALYTICS_CHARTS } from './analyticsRegistry';
import AddChartToDashboardMenu from './AddChartToDashboardMenu';

/**
 * Portföy "Grafikler" sekmesi — serbest yerleşimli + boyutlandırılabilir pano (GridBoard).
 * "Özelleştir" ile düzenleme moduna geçilir; araç çubuğundaki "Dashboard'a Ekle" menüsüyle
 * istenen grafik (kaynak portföy adıyla) Dashboard'a gönderilir.
 *
 * @param portfolioId / portfolioName  Dashboard'a ekleme için
 */
export default function PortfolioAnalyticsSection({ holdings, valuesHidden, currency, portfolioId, portfolioName }) {
  const items = ANALYTICS_CHARTS.map(c => ({
    key: c.key,
    w: c.w,
    h: c.h,
    node: <c.Comp holdings={holdings} valuesHidden={valuesHidden} currency={currency} portfolioId={portfolioId} />,
  }));

  return (
    <div className="p-4 sm:p-5 min-w-0 overflow-x-hidden">
      <GridBoard
        storageKey="pf-analytics-grid-v2"
        items={items}
        removable
        toolbar={portfolioId
          ? <AddChartToDashboardMenu portfolioId={portfolioId} portfolioName={portfolioName} />
          : null}
      />
    </div>
  );
}
