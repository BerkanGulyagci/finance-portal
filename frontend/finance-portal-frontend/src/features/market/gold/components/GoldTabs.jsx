import { GOLD_TABS } from './goldConstants';
import { useTranslation } from '../../../../context/LanguageContext';

export default function GoldTabs({ activeTab, onTabChange }) {
  const { t } = useTranslation();
  return (
    <div className="flex overflow-x-auto border-b border-gray-200">
      {GOLD_TABS.map(tab => (
        <button
          key={tab.key}
          onClick={() => onTabChange(tab.key)}
          className={`px-5 py-3 text-sm font-semibold whitespace-nowrap transition-all border-b-2 ${
            activeTab === tab.key
              ? 'border-[#093eaa] text-[#093eaa] bg-blue-50'
              : 'border-transparent text-gray-600 hover:text-[#093eaa] hover:bg-gray-50'
          }`}
        >
          {t(tab.label)}
        </button>
      ))}
    </div>
  );
}
