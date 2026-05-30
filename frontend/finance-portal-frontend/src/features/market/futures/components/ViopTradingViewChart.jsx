import { BarChart3 } from 'lucide-react';
import { useTranslation } from '../../../../context/LanguageContext';

export default function ViopChartPlaceholder() {
  const { t } = useTranslation();
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-12">
      <div className="flex flex-col items-center justify-center text-center">
        <div className="w-20 h-20 rounded-full bg-gradient-to-br from-blue-50 to-blue-100 flex items-center justify-center mb-4">
          <BarChart3 className="w-10 h-10 text-[#093eaa]" />
        </div>
        <h3 className="text-xl font-bold text-gray-900 mb-2">{t('Grafik Yakında Eklenecek')}</h3>
        <p className="text-sm text-gray-500 max-w-md leading-relaxed">
          {t('VİOP kontrat grafikleri için entegrasyon çalışması devam etmektedir. Şu an için fiyat bilgilerini ve açık pozisyon verilerini aşağıdaki bölümlerden inceleyebilirsiniz.')}
        </p>
      </div>
    </div>
  );
}
