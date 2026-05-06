import { BarChart2 } from 'lucide-react';

export default function CompareEmptyState() {
  return (
    <div className="bg-white rounded-2xl border border-gray-200 shadow-sm p-14 text-center">
      <BarChart2 className="w-12 h-12 text-gray-300 mx-auto mb-3" />
      <p className="text-gray-600 font-semibold text-base">Fon seçin ve karşılaştırın</p>
      <p className="text-gray-400 text-sm mt-1.5">
        En az 2, en fazla 4 fon seçip <span className="font-semibold text-[#093eaa]">Karşılaştır</span> butonuna tıklayın
      </p>
    </div>
  );
}
