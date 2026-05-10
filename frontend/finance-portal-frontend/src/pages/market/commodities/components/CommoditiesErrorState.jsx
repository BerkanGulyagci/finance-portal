import { AlertCircle } from 'lucide-react';

export default function CommoditiesErrorState({ message }) {
  return (
    <div className="flex flex-col items-center justify-center py-20 text-gray-500 gap-3">
      <AlertCircle className="w-8 h-8 text-rose-400" />
      <p>{message ?? 'Emtia listesi alınamadı.'}</p>
    </div>
  );
}
