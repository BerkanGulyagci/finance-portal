export default function CommoditiesLoadingState() {
  return (
    <div className="space-y-6">
      <div className="h-8 bg-gray-200 rounded animate-pulse w-64" />
      <div className="h-12 bg-amber-50 rounded-xl animate-pulse" />
      {/* Filtre çipleri */}
      <div className="flex flex-wrap gap-2">
        {Array.from({ length: 4 }).map((_, i) => (
          <div key={i} className="h-8 bg-gray-100 rounded-full animate-pulse w-24" />
        ))}
      </div>
      {/* Kart ızgarası */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-5">
        {Array.from({ length: 9 }).map((_, i) => (
          <div key={i} className="h-[200px] bg-gray-100 rounded-2xl animate-pulse" />
        ))}
      </div>
    </div>
  );
}
