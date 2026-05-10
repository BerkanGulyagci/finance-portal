export default function GoldErrorState({ message = 'Altın verisi alınamadı.' }) {
  return (
    <div className="flex items-center justify-center py-12">
      <p className="text-rose-500 text-sm">{message}</p>
    </div>
  );
}
