import { Users } from 'lucide-react';

export function AdminLoadingState() {
  return <p className="p-12 text-center text-gray-500 text-sm">Yükleniyor...</p>;
}

export function AdminErrorState({ message, onRetry }) {
  return (
    <section className="p-12 text-center">
      <p className="text-rose-600 text-sm font-semibold mb-3">{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="text-[#093eaa] text-sm font-bold hover:underline"
      >
        Tekrar dene
      </button>
    </section>
  );
}

export function AdminEmptyState({ message = 'Kullanıcı bulunamadı.' }) {
  return (
    <section className="p-12 text-center">
      <Users className="w-10 h-10 text-gray-300 mx-auto mb-3" />
      <p className="text-gray-500 text-sm">{message}</p>
    </section>
  );
}
